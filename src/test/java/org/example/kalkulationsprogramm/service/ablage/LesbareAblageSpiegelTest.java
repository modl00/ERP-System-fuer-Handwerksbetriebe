package org.example.kalkulationsprogramm.service.ablage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Eintrag;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Ergebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Das Spiegeln selbst, ohne Datenbank: auf einem temporären Ordner. */
class LesbareAblageSpiegelTest {

    @TempDir Path ordner;
    Path uploads;
    LesbareAblageService service;

    @BeforeEach
    void setUp() throws IOException {
        uploads = Files.createDirectories(ordner.resolve("uploads"));
        service = new LesbareAblageService(uploads.toString(), uploads.toString(), uploads.toString(), uploads.toString());
    }

    private Path datei(String name, String inhalt) throws IOException {
        Path p = uploads.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, inhalt);
        return p;
    }

    private Path ziel(String relativ) {
        return service.zielordner().resolve(relativ);
    }

    @Test
    void legtDieLesbareStrukturAlsHardlinkAnOhneSpeicherZuVerdoppeln() throws IOException {
        Path original = datei("3f2a-uuid.pdf", "Rechnung 1");

        Ergebnis e = service.spiegle(List.of(new Eintrag(original, "Projekte/2026/014 - Halle/Rechnung.pdf")), false, 0);

        assertThat(e.neu()).isEqualTo(1);
        assertThat(e.kopiert()).isZero();
        Path link = ziel("Projekte/2026/014 - Halle/Rechnung.pdf");
        assertThat(Files.readString(link)).isEqualTo("Rechnung 1");
        assertThat(Files.isSameFile(link, original)).as("dasselbe Exemplar, kein zweites").isTrue();
        assertThat(e.beispiele()).containsExactly("Projekte/2026/014 - Halle/Rechnung.pdf");
    }

    @Test
    void zweiterLaufIstEinNullSummenSpielUndAendertNichts() throws IOException {
        Path original = datei("a.pdf", "A");
        List<Eintrag> plan = List.of(new Eintrag(original, "Projekte/x/A.pdf"));
        service.spiegle(plan, false, 0);

        Ergebnis zweiter = service.spiegle(plan, false, 0);

        assertThat(zweiter.neu()).isZero();
        assertThat(zweiter.ersetzt()).isZero();
        assertThat(zweiter.unveraendert()).isEqualTo(1);
        assertThat(zweiter.entfernt()).isZero();
    }

    @Test
    void probelaufZeigtWasPassierenWuerdeUndAendertNichts() throws IOException {
        Path original = datei("a.pdf", "A");

        Ergebnis e = service.spiegle(List.of(new Eintrag(original, "Projekte/x/A.pdf")), true, 2);

        assertThat(e.probelauf()).isTrue();
        assertThat(e.neu()).isEqualTo(1);
        assertThat(e.fehlend()).isEqualTo(2);
        assertThat(Files.exists(service.zielordner())).as("nichts angelegt").isFalse();
    }

    @Test
    void entferntVeralteteEintraegeUndLeereOrdnerAberNieDasOriginal() throws IOException {
        Path a = datei("a.pdf", "A");
        Path b = datei("b.pdf", "B");
        service.spiegle(List.of(new Eintrag(a, "Projekte/alt/A.pdf"), new Eintrag(b, "Projekte/bleibt/B.pdf")), false, 0);

        Ergebnis e = service.spiegle(List.of(new Eintrag(b, "Projekte/bleibt/B.pdf")), false, 0);

        assertThat(e.entfernt()).isEqualTo(1);
        assertThat(Files.exists(ziel("Projekte/alt/A.pdf"))).isFalse();
        assertThat(Files.exists(ziel("Projekte/alt"))).as("leerer Ordner weg").isFalse();
        assertThat(Files.readString(a)).as("das Original bleibt unberührt").isEqualTo("A");
        assertThat(Files.exists(ziel("Projekte/bleibt/B.pdf"))).isTrue();
    }

    @Test
    void probelaufEntferntNichts() throws IOException {
        Path a = datei("a.pdf", "A");
        service.spiegle(List.of(new Eintrag(a, "Projekte/alt/A.pdf")), false, 0);

        Ergebnis e = service.spiegle(List.of(), true, 0);

        assertThat(e.entfernt()).isEqualTo(1);
        assertThat(Files.exists(ziel("Projekte/alt/A.pdf"))).isTrue();
    }

    @Test
    void ersetztEinenEintragDerAufEineAndereDateiZeigt() throws IOException {
        Path alt = datei("alt.pdf", "ALT");
        Path neu = datei("neu.pdf", "NEU");
        Files.setLastModifiedTime(neu, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(alt).toMillis() + 60_000));
        service.spiegle(List.of(new Eintrag(alt, "Projekte/x/Plan.pdf")), false, 0);

        Ergebnis e = service.spiegle(List.of(new Eintrag(neu, "Projekte/x/Plan.pdf")), false, 0);

        assertThat(e.ersetzt()).isEqualTo(1);
        assertThat(Files.readString(ziel("Projekte/x/Plan.pdf"))).isEqualTo("NEU");
        assertThat(Files.readString(alt)).isEqualTo("ALT");
    }

    @Test
    void eineKopieMitKnappAbweichenderZeitUndAnderemInhaltWirdErsetzt() throws IOException {
        service.setVerknuepfer((z, q) -> {
            throw new java.nio.file.FileSystemException("Cross-device link");
        });
        Path original = datei("a.pdf", "AAAA");
        service.spiegle(List.of(new Eintrag(original, "Projekte/x/A.pdf")), false, 0);
        // Das Original ändert sich bei gleicher Größe, die Zeit weicht nur um eine Sekunde ab (grobe Zeitstempel)
        Files.writeString(original, "BBBB");
        Files.setLastModifiedTime(ziel("Projekte/x/A.pdf"), java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(original).toMillis() - 1000));

        Ergebnis e = service.spiegle(List.of(new Eintrag(original, "Projekte/x/A.pdf")), false, 0);

        assertThat(e.ersetzt()).isEqualTo(1);
        assertThat(Files.readString(ziel("Projekte/x/A.pdf"))).isEqualTo("BBBB");
    }

    @Test
    void kopiertWennHardlinksNichtGehenUndWeistDasAus() throws IOException {
        service.setVerknuepfer((z, q) -> {
            throw new java.nio.file.FileSystemException("Cross-device link");
        });
        Path original = datei("a.pdf", "Inhalt");

        Ergebnis e = service.spiegle(List.of(new Eintrag(original, "Projekte/x/A.pdf")), false, 0);

        assertThat(e.kopiert()).isEqualTo(1);
        assertThat(Files.readString(ziel("Projekte/x/A.pdf"))).isEqualTo("Inhalt");
        assertThat(Files.isSameFile(ziel("Projekte/x/A.pdf"), original)).isFalse();
        // Eine Kopie mit gleicher Größe und Zeit gilt beim nächsten Lauf als unverändert
        Ergebnis zweiter = service.spiegle(List.of(new Eintrag(original, "Projekte/x/A.pdf")), false, 0);
        assertThat(zweiter.unveraendert()).isEqualTo(1);
        assertThat(zweiter.kopiert()).isZero();
    }

    @Test
    void ungueltigeZielpfadeWerdenAbgelehnt() throws IOException {
        Path original = datei("a.pdf", "A");

        Ergebnis e = service.spiegle(List.of(
                new Eintrag(original, "../../ausserhalb.pdf"),
                new Eintrag(original, "Projekte/../../ausserhalb2.pdf"),
                new Eintrag(original, "")), false, 0);

        assertThat(e.neu()).isZero();
        assertThat(e.fehler()).hasSize(3);
        assertThat(Files.exists(ordner.resolve("ausserhalb.pdf"))).isFalse();
        assertThat(Files.exists(uploads.getParent().resolve("ausserhalb2.pdf"))).isFalse();
    }

    @Test
    void einSymlinkInDerLesbarenAblageWirdEntferntAberNichtVerfolgt() throws IOException {
        Path wichtig = datei("../wichtig.txt", "NICHT LOESCHEN");
        Path links = Files.createDirectories(service.zielordner().resolve("Projekte"));
        try {
            Files.createSymbolicLink(links.resolve("falle"), wichtig.getParent());   // Ordner-Symlink nach außen
        } catch (UnsupportedOperationException | IOException e) {
            return;   // Dateisystem ohne Symlinks: nichts zu prüfen
        }

        service.spiegle(List.of(), false, 0);

        assertThat(Files.readString(wichtig)).as("Datei außerhalb bleibt").isEqualTo("NICHT LOESCHEN");
        assertThat(Files.exists(wichtig.getParent())).isTrue();
    }

    @Test
    void innerhalbVerhindertAusbruchAusDerBasis() {
        assertThat(LesbareAblageService.innerhalb(uploads, "../geheim.txt")).isNull();
        assertThat(LesbareAblageService.innerhalb(uploads, "a/../../geheim.txt")).isNull();
        assertThat(LesbareAblageService.innerhalb(uploads, null)).isNull();
        assertThat(LesbareAblageService.innerhalb(uploads, " ")).isNull();
        assertThat(LesbareAblageService.innerhalb(uploads, "belege/x.pdf")).isEqualTo(uploads.resolve("belege/x.pdf"));
    }
}
