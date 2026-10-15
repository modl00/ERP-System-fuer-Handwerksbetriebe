package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WartungProtokollPdfServiceTest {

    private static final long ID = 42L;

    @Mock
    private WartungProtokollRepository protokollRepository;
    @Mock
    private WartungProtokollAbschnittRepository abschnittRepository;
    @Mock
    private WartungProtokollPruefpunktRepository pruefpunktRepository;
    @Mock
    private WartungProtokollDatenfeldRepository datenfeldRepository;
    @Mock
    private FirmeninformationService firmeninformationService;

    @InjectMocks
    private WartungProtokollPdfService service;

    private WartungProtokoll protokoll;
    private final List<WartungProtokollPruefpunkt> punkte = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Max Mustermann GmbH");
        WartungObjekt objekt = new WartungObjekt();
        objekt.setId(5L);
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        WartungTuer tuer = new WartungTuer();
        tuer.setId(9L);
        tuer.setObjekt(objekt);
        tuer.setBezeichnung("Tür 1.01");
        Mitarbeiter techniker = new Mitarbeiter();
        techniker.setVorname("Erika");
        techniker.setNachname("Beispiel");

        protokoll = new WartungProtokoll();
        protokoll.setId(ID);
        protokoll.setNummer("WA-2026-1001");
        protokoll.setKunde(kunde);
        protokoll.setObjekt(objekt);
        protokoll.setTuer(tuer);
        protokoll.setTechniker(techniker);
        protokoll.setTitel("Jahreswartung");
        protokoll.setProtokollTyp("Wartungsprotokoll");
        protokoll.setDatum(LocalDate.of(2026, 5, 10));
        protokoll.setIntervallMonate(12);
        protokoll.setNaechsteWartung(LocalDate.of(2027, 5, 10));
        protokoll.setStatus(WartungStatus.IN_BEARBEITUNG);
        protokoll.setModule(new LinkedHashSet<>(List.of(new WartungProtokollModul(1L, "m_bst", "Brandschutztür", "zweiflg", "2-flügelig"))));
        protokoll.setBemerkungen("Zugang nur über den Hof.");

        WartungProtokollAbschnitt abschnitt = new WartungProtokollAbschnitt();
        abschnitt.setId(10L);
        abschnitt.setProtokollId(ID);
        abschnitt.setBereich(WartungBereich.PRUEFPUNKTE);
        abschnitt.setLabel("Prüfpunkte Brandschutztür");

        punkte.clear();
        punkte.add(punkt(1, "Zulassungsschild lesbar", WartungErgebnis.IO, null));
        punkte.add(punkt(2, "Dichtung auf Beschädigungen prüfen", WartungErgebnis.NIO, "Dichtung gerissen"));
        punkte.add(punkt(3, "Auslösung über Handauslösetaster → Feststellung wird aufgehoben", WartungErgebnis.NA, null));
        punkte.add(punkt(4, "Schließfunktion prüfen", null, null));

        WartungProtokollDatenfeld feld = new WartungProtokollDatenfeld();
        feld.setId(1L);
        feld.setProtokollId(ID);
        feld.setLabel("Standort / Lage");
        feld.setFeldtyp(WartungFeldtyp.TEXT);
        feld.setWert("Halle 3, Eingang Süd");

        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setFirmenname("Schlosserei Beispiel");
        firma.setStrasse("Hauptstraße 1");
        firma.setPlz("97070");
        firma.setOrt("Würzburg");
        firma.setTelefon("0931 123456");

        lenient().when(protokollRepository.findById(ID)).thenReturn(Optional.of(protokoll));
        lenient().when(abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of(abschnitt));
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(a -> List.copyOf(punkte));
        lenient().when(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of(feld));
        lenient().when(firmeninformationService.getFirmeninformation()).thenReturn(firma);
        lenient().when(firmeninformationService.loadLogoImage()).thenReturn(null);
    }

    private static WartungProtokollPruefpunkt punkt(int nr, String label, WartungErgebnis ergebnis, String bemerkung) {
        WartungProtokollPruefpunkt p = new WartungProtokollPruefpunkt();
        p.setId((long) nr);
        p.setProtokollId(ID);
        p.setAbschnittId(10L);
        p.setModulKey("m_bst");
        p.setLabel(label);
        p.setErgebnis(ergebnis);
        p.setBemerkung(bemerkung);
        p.setSortierung(nr);
        return p;
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }

    private static byte[] pngBytes() throws Exception {
        // Ohne Transparenz, sonst legt das PDF zu dem Bild noch ein zweites Objekt für die Maske an.
        BufferedImage bild = new BufferedImage(40, 15, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 40; x++) {
                bild.setRGB(x, y, y == 7 ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        ImageIO.write(bild, "png", aus);
        return aus.toByteArray();
    }

    @Test
    @DisplayName("Das PDF enthält Nummer, Ort, Techniker, Datenaufnahme, Prüfpunkte mit Ergebnis und die Bemerkung")
    void inhalt() throws Exception {
        WartungProtokollPdfService.PdfDatei datei = service.erstelle(ID);

        assertThat(new String(datei.inhalt(), 0, 5)).isEqualTo("%PDF-");
        String t = text(datei.inhalt());
        assertThat(t).contains("WA-2026-1001", "Wartungsprotokoll", "Jahreswartung", "Max Mustermann GmbH", "Werk Nord", "Tür 1.01",
                "Brandschutztür (2-flügelig)", "Erika Beispiel", "10.05.2026", "10.05.2027",
                "Schlosserei Beispiel", "Hauptstraße 1", "97070 Würzburg",
                "Standort / Lage", "Halle 3, Eingang Süd",
                "Prüfpunkte Brandschutztür", "Zulassungsschild lesbar", "Dichtung auf Beschädigungen prüfen",
                "Zugang nur über den Hof.");
        assertThat(t).contains("i.O.", "n.i.O.", "n.a.", "offen");
    }

    @Test
    @DisplayName("„keine Angabe“ steht nicht im gedruckten Protokoll (die Zeile entfällt), leere Felder zeigen weiter einen Strich")
    void keineAngabeWirdNichtGedruckt() throws Exception {
        WartungProtokollDatenfeld anschlag = datenfeld(1L, "Anschlag", "keine Angabe", 0);
        WartungProtokollDatenfeld hersteller = datenfeld(2L, "Hersteller", "Schüco", 1);
        WartungProtokollDatenfeld baujahr = datenfeld(3L, "Baujahr", null, 2);
        org.mockito.Mockito.when(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of(anschlag, hersteller, baujahr));

        String t = text(service.erstelle(ID).inhalt());

        assertThat(t).contains("Hersteller", "Schüco", "Baujahr");
        assertThat(t).doesNotContain("Anschlag").doesNotContain("keine Angabe");
    }

    private WartungProtokollDatenfeld datenfeld(Long id, String label, String wert, int sortierung) {
        WartungProtokollDatenfeld f = new WartungProtokollDatenfeld();
        f.setId(id);
        f.setProtokollId(ID);
        f.setLabel(label);
        f.setFeldtyp(org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp.AUSWAHL);
        f.setWert(wert);
        f.setSortierung(sortierung);
        return f;
    }

    @Test
    @DisplayName("Mängel erscheinen in einem eigenen Kasten mit ihrer Beschreibung, samt Zusammenfassung")
    void maengel() throws Exception {
        String t = text(service.erstelle(ID).inhalt());
        assertThat(t).contains("Festgestellter Mangel", "Dichtung gerissen", "3 von 4 Prüfpunkten bewertet, davon 1 in Ordnung.");
    }

    @Test
    @DisplayName("Ohne Mängel steht, dass keine festgestellt wurden")
    void keineMaengel() throws Exception {
        punkte.removeIf(p -> p.getErgebnis() == WartungErgebnis.NIO);
        assertThat(text(service.erstelle(ID).inhalt())).contains("Keine Mängel festgestellt.");
    }

    @Test
    @DisplayName("Zeichen, die die PDF-Schrift nicht kennt, werden ersetzt statt zu verschwinden")
    void sonderzeichen() throws Exception {
        String t = text(service.erstelle(ID).inhalt());
        assertThat(t).contains("Handauslösetaster -> Feststellung wird aufgehoben");
        assertThat(t).doesNotContain("→");
    }

    @Test
    @DisplayName("Ein nicht abgeschlossenes Protokoll ist als Entwurf gekennzeichnet, ein abgeschlossenes nicht")
    void entwurfHinweis() throws Exception {
        assertThat(text(service.erstelle(ID).inhalt())).contains("ENTWURF");

        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.of(2026, 5, 10, 12, 0));
        assertThat(text(service.erstelle(ID).inhalt())).doesNotContain("ENTWURF");
    }

    @Test
    @DisplayName("Ein wieder geöffnetes Protokoll gilt wieder als Entwurf")
    void wiederGeoeffnetIstEntwurf() throws Exception {
        protokoll.setStatus(WartungStatus.IN_BEARBEITUNG);
        protokoll.setAbgeschlossenAm(null);
        assertThat(text(service.erstelle(ID).inhalt())).contains("ENTWURF");
    }

    @Test
    @DisplayName("Eine Unterschrift erscheint als Bild mit Namen, eine fehlende als leere Linie zum Unterschreiben von Hand")
    void unterschriften() throws Exception {
        String png = WartungUnterschriftPruefer.PRAEFIX + Base64.getEncoder().encodeToString(pngBytes());
        protokoll.setUnterschriftTechniker(png);
        protokoll.setUnterschriftTechnikerName("Erika Beispiel");
        protokoll.setAbgeschlossenAm(LocalDateTime.of(2026, 5, 10, 12, 0));

        byte[] pdf = service.erstelle(ID).inhalt();
        String t = text(pdf);
        assertThat(t).contains("Techniker: Erika Beispiel", "Kunde");
        assertThat(t).doesNotContain("Kunde:");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<String> bilder = new ArrayList<>();
            for (PDPage seite : doc.getPages()) {
                for (COSName name : seite.getResources().getXObjectNames()) {
                    PDXObject objekt = seite.getResources().getXObject(name);
                    if (objekt instanceof PDImageXObject bild) {
                        bilder.add("Seite " + (doc.getPages().indexOf(seite) + 1) + " " + name.getName() + " " + bild.getWidth() + "x" + bild.getHeight());
                    }
                }
            }
            assertThat(bilder).as("Seiten: " + doc.getNumberOfPages()).hasSize(1);
        }
    }

    @Test
    @DisplayName("Eine kaputte Unterschrift bringt das PDF nicht zum Absturz, die Linie bleibt leer")
    void kaputteUnterschrift() throws Exception {
        protokoll.setUnterschriftKunde(WartungUnterschriftPruefer.PRAEFIX + Base64.getEncoder().encodeToString("kein bild".getBytes()));
        protokoll.setUnterschriftKundeName("Max");

        assertThat(text(service.erstelle(ID).inhalt())).contains("Kunde: Max");
    }

    @Test
    @DisplayName("Ein langes Protokoll läuft auf mehrere Seiten, jede mit Fußzeile und Seitenzahl")
    void mehrereSeiten() throws Exception {
        for (int i = 5; i <= 120; i++) {
            punkte.add(punkt(i, "Weiterer Prüfpunkt Nummer " + i, WartungErgebnis.IO, null));
        }
        byte[] pdf = service.erstelle(ID).inhalt();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int seite = 1; seite <= doc.getNumberOfPages(); seite++) {
                stripper.setStartPage(seite);
                stripper.setEndPage(seite);
                String t = stripper.getText(doc).replaceAll("\\s+", " ");
                assertThat(t).contains("Wartungsprotokoll WA-2026-1001");
                assertThat(t).contains("Seite " + seite + " von " + doc.getNumberOfPages());
            }
        }
    }

    @Test
    @DisplayName("Die Nummerierung der Prüfpunkte läuft über die Abschnitte hinweg durch")
    void nummerierung() throws Exception {
        assertThat(text(service.erstelle(ID).inhalt())).contains("1 Zulassungsschild lesbar").contains("4 Schließfunktion prüfen");
    }

    @Test
    @DisplayName("Fehlen die Firmendaten, wird das PDF trotzdem erstellt")
    void ohneFirmendaten() throws Exception {
        when(firmeninformationService.getFirmeninformation()).thenThrow(new IllegalStateException("keine Firma"));

        assertThat(text(service.erstelle(ID).inhalt())).contains("WA-2026-1001");
    }

    @Test
    @DisplayName("Ein unbekanntes Protokoll ergibt 404")
    void unbekannt() {
        assertThatThrownBy(() -> service.erstelle(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("pdfText ersetzt Pfeile und Vergleichszeichen, unbekannte Zeichen durch ein Fragezeichen, und kommt mit null klar")
    void pdfText() {
        assertThat(WartungProtokollPdfService.pdfText("a → b ≤ c ≥ d")).isEqualTo("a -> b <= c >= d");
        assertThat(WartungProtokollPdfService.pdfText("Prüfung ärztlich ß")).isEqualTo("Prüfung ärztlich ß");
        assertThat(WartungProtokollPdfService.pdfText("Tür 🚪 offen")).isEqualTo("Tür ? offen");
        assertThat(WartungProtokollPdfService.pdfText(null)).isEmpty();
    }

    @Test
    @DisplayName("Der Dateiname enthält nur sichere Zeichen")
    void dateiname() {
        protokoll.setNummer("WA-2026-1001");
        assertThat(WartungProtokollPdfService.dateiname(protokoll)).isEqualTo("Wartungsprotokoll_WA-2026-1001.pdf");
        protokoll.setNummer("WA/../2026\"1001\r\nX-Evil: 1");
        assertThat(WartungProtokollPdfService.dateiname(protokoll)).matches("Wartungsprotokoll_[A-Za-z0-9._-]+\\.pdf");
    }
}
