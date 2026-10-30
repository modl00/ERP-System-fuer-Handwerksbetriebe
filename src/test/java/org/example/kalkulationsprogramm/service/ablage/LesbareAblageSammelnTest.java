package org.example.kalkulationsprogramm.service.ablage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Ergebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

/** Das Einsammeln aus der Datenbank: richtige Ordner, richtige Quellen, Auslassungen. Nur Dummy-Daten (DSGVO). */
@DataJpaTest
class LesbareAblageSammelnTest {

    @Autowired EntityManager em;
    @TempDir Path ordner;
    Path uploads;
    Path angebote;
    LesbareAblageService service;

    @BeforeEach
    void setUp() throws IOException {
        uploads = Files.createDirectories(ordner.resolve("uploads"));
        angebote = Files.createDirectories(uploads.resolve("offers"));
        service = new LesbareAblageService(uploads.toString(), angebote.toString(), uploads.resolve("attachments").toString(), uploads.resolve("images").toString());
        ReflectionTestUtils.setField(service, "entityManager", em);
    }

    private Path datei(Path basis, String name, String inhalt) throws IOException {
        Path p = basis.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, inhalt);
        return p;
    }

    private Projekt projekt() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Anbau Halle / Süd");
        p.setAuftragsnummer("2026/014");
        p.setAnlegedatum(LocalDate.of(2026, 3, 2));
        p.setBruttoPreis(BigDecimal.ZERO);
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-P1");
        kunde.setName("Max Mustermann GmbH");
        em.persist(kunde);
        p.setKundenId(kunde);
        em.persist(p);
        return p;
    }

    private Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Stahl Müller GmbH");
        em.persist(l);
        return l;
    }

    @Test
    void projektdokumenteLandenUnterJahrProjektUndGruppe() throws IOException {
        Projekt p = projekt();
        ProjektDokument d = new ProjektDokument();
        d.setProjekt(p);
        d.setOriginalDateiname("Rechnung RE-2026-031.pdf");
        d.setGespeicherterDateiname("9c1e-uuid.pdf");
        d.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);
        em.persist(d);
        em.flush();
        datei(uploads, "9c1e-uuid.pdf", "RECHNUNG");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isEqualTo(1);
        Path erwartet = service.zielordner().resolve("Projekte/2026/Max Mustermann GmbH/2026-014 - Anbau Halle - Süd/Geschaeftsdokumente/Rechnung RE-2026-031.pdf");
        assertThat(Files.readString(erwartet)).isEqualTo("RECHNUNG");
        assertThat(Files.isSameFile(erwartet, uploads.resolve("9c1e-uuid.pdf"))).isTrue();
    }

    @Test
    void anfragedokumenteLiegenImAngebotsordnerUndLandenUnterKundeUndBauvorhaben() throws IOException {
        Kunde k = new Kunde();
        k.setKundennummer("K-1");
        k.setName("Muster GmbH");
        em.persist(k);
        Anfrage a = new Anfrage();
        a.setBauvorhaben("Geländer Hof");
        a.setAnlegedatum(LocalDate.of(2026, 5, 4));
        a.setKunde(k);
        em.persist(a);
        AnfrageDokument d = new AnfrageDokument();
        d.setAnfrage(a);
        d.setOriginalDateiname("Kalkulation.xlsx");
        d.setGespeicherterDateiname("77aa.xlsx");
        d.setDokumentGruppe(DokumentGruppe.KALKULATIONSDOKUMENTE);
        em.persist(d);
        em.flush();
        datei(angebote, "77aa.xlsx", "ZAHLEN");

        service.aufbauen(false);

        assertThat(Files.readString(service.zielordner().resolve("Anfragen/2026/Muster GmbH - Geländer Hof (A" + a.getId() + ")/Kalkulation/Kalkulation.xlsx")))
                .isEqualTo("ZAHLEN");
    }

    @Test
    void lieferantendokumenteLandenUnterLieferantJahrUndTypBelegeBleibenDraussen() throws IOException {
        Lieferanten l = lieferant();
        LieferantDokument rechnung = lieferantDokument(l, LieferantDokumentTyp.RECHNUNG, "Rechnung 1234.pdf", "r1.pdf", false);
        lieferantDokument(l, LieferantDokumentTyp.BELEG, "Quittung.jpg", "b1.jpg", false);
        lieferantDokument(l, LieferantDokumentTyp.LIEFERSCHEIN, "Ausgeblendet.pdf", "x1.pdf", true);
        em.flush();
        datei(uploads, "lieferanten/" + l.getId() + "/r1.pdf", "RE1234");
        datei(uploads, "belege/b1.jpg", "QUITTUNG");
        datei(uploads, "lieferanten/" + l.getId() + "/x1.pdf", "WEG");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).as("Beleg und ausgeblendetes Dokument fehlen").isEqualTo(1);
        assertThat(Files.readString(service.zielordner().resolve("Lieferanten/Stahl Müller GmbH/2026/Rechnungen/Rechnung 1234.pdf"))).isEqualTo("RE1234");
        assertThat(rechnung.getId()).isNotNull();
    }

    private LieferantDokument lieferantDokument(Lieferanten l, LieferantDokumentTyp typ, String original, String gespeichert, boolean ausgeblendet) {
        LieferantDokument d = new LieferantDokument();
        d.setLieferant(l);
        d.setTyp(typ);
        d.setOriginalDateiname(original);
        d.setGespeicherterDateiname(gespeichert);
        d.setUploadDatum(LocalDateTime.of(2026, 7, 9, 10, 0));
        d.setAusgeblendet(ausgeblendet);
        em.persist(d);
        return d;
    }

    private AusgangsGeschaeftsDokument ausgang(AusgangsGeschaeftsDokumentTyp typ, String nummer, String pdf, Projekt projekt, Anfrage anfrage, boolean storniert) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setTyp(typ);
        d.setDokumentNummer(nummer);
        d.setDatum(LocalDate.of(2026, 6, 1));
        d.setPdfDateiname(pdf);
        d.setProjekt(projekt);
        d.setAnfrage(anfrage);
        d.setStorniert(storniert);
        em.persist(d);
        return d;
    }

    @Test
    void gebuchteAusgangsdokumenteLandenImProjektUnterAusgangsdokumenten() throws IOException {
        Projekt p = projekt();
        ausgang(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026-031", "re31.pdf", p, null, false);
        ausgang(AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026-007", "an7.pdf", p, null, true);
        em.flush();
        datei(uploads, "re31.pdf", "RECHNUNG");
        datei(uploads, "an7.pdf", "ANGEBOT");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isEqualTo(2);
        Path basis = service.zielordner().resolve("Projekte/2026/Max Mustermann GmbH/2026-014 - Anbau Halle - Süd/Ausgangsdokumente");
        assertThat(Files.readString(basis.resolve("Rechnung RE-2026-031.pdf"))).isEqualTo("RECHNUNG");
        assertThat(Files.readString(basis.resolve("STORNIERT Angebot AN-2026-007.pdf"))).isEqualTo("ANGEBOT");
    }

    @Test
    void dieselbeDateiAlsProjektdokumentUndAusgangsdokumentErscheintNurEinmal() throws IOException {
        Projekt p = projekt();
        ProjektDokument op = new ProjektDokument();
        op.setProjekt(p);
        op.setOriginalDateiname("RE-2026-031.pdf");
        op.setGespeicherterDateiname("re31.pdf");
        op.setDokumentGruppe(DokumentGruppe.GESCHAEFTSDOKUMENTE);
        em.persist(op);
        ausgang(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026-031", "re31.pdf", p, null, false);
        em.flush();
        datei(uploads, "re31.pdf", "RECHNUNG");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isEqualTo(1);
        assertThat(Files.exists(service.zielordner().resolve("Projekte/2026/Max Mustermann GmbH/2026-014 - Anbau Halle - Süd/Geschaeftsdokumente/RE-2026-031.pdf"))).isTrue();
    }

    @Test
    void ausgangsdokumentOhneProjektUndAnfrageLandetUnterAusgangsdokumenten() throws IOException {
        ausgang(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT, "GS-1", "gs1.pdf", null, null, false);
        em.flush();
        datei(uploads, "gs1.pdf", "GUTSCHRIFT");

        service.aufbauen(false);

        assertThat(Files.readString(service.zielordner().resolve("Ausgangsdokumente/2026/Ausgangsdokumente/Gutschrift GS-1.pdf"))).isEqualTo("GUTSCHRIFT");
    }

    @Test
    void fehlendeDateienWerdenGezaehltUndUebersprungen() {
        Projekt p = projekt();
        ProjektDokument d = new ProjektDokument();
        d.setProjekt(p);
        d.setOriginalDateiname("weg.pdf");
        d.setGespeicherterDateiname("gibt-es-nicht.pdf");
        em.persist(d);
        em.flush();

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isZero();
        assertThat(e.fehlend()).isEqualTo(1);
    }

    @Test
    void einAusbruchsversuchImGespeichertenNamenWirdNichtGelesen() throws IOException {
        Path geheim = datei(ordner, "geheim.txt", "GEHEIM");
        Projekt p = projekt();
        ProjektDokument d = new ProjektDokument();
        d.setProjekt(p);
        d.setOriginalDateiname("../../geheim.txt");
        d.setGespeicherterDateiname("../" + geheim.getFileName());
        em.persist(d);
        em.flush();

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isZero();
        assertThat(e.fehlend()).isEqualTo(1);
        assertThat(Files.exists(service.zielordner())).isFalse();
    }

    @Test
    void gleicheOriginalnamenImSelbenOrdnerWerdenDurchnummeriert() throws IOException {
        Projekt p = projekt();
        for (int i = 1; i <= 3; i++) {
            ProjektDokument d = new ProjektDokument();
            d.setProjekt(p);
            d.setOriginalDateiname("Plan.pdf");
            d.setGespeicherterDateiname("p" + i + ".pdf");
            d.setDokumentGruppe(DokumentGruppe.PLANUNGSDOKUMENTE);
            em.persist(d);
            datei(uploads, "p" + i + ".pdf", "PLAN" + i);
        }
        em.flush();

        service.aufbauen(false);

        Path ziel = service.zielordner().resolve("Projekte/2026/Max Mustermann GmbH/2026-014 - Anbau Halle - Süd/Planungsdokumente");
        try (var dateien = Files.list(ziel)) {
            assertThat(dateien.map(x -> x.getFileName().toString())).containsExactlyInAnyOrder("Plan.pdf", "Plan (2).pdf", "Plan (3).pdf");
        }
        assertThat(Files.readString(ziel.resolve("Plan.pdf"))).isEqualTo("PLAN1");
        assertThat(Files.readString(ziel.resolve("Plan (3).pdf"))).isEqualTo("PLAN3");
    }

    @Test
    void zweiAufbauteGleichzeitigWirdAbgewiesen() {
        java.util.concurrent.atomic.AtomicBoolean laeuft = (java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(service, "laeuft");
        laeuft.set(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.aufbauen(false))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("gerade aufgebaut");
    }

    private Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        em.persist(m);
        return m;
    }

    @Test
    void fotosAusDerHandyAppLandenImProjektUnterBilder() throws IOException {
        Projekt p = projekt();
        ProjektNotiz n = new ProjektNotiz();
        n.setProjekt(p);
        n.setMitarbeiter(mitarbeiter());
        n.setNotiz("Baustelle Süd");
        n.setErstelltAm(LocalDateTime.of(2026, 3, 5, 8, 0));
        em.persist(n);
        ProjektNotizBild b = new ProjektNotizBild();
        b.setNotiz(n);
        b.setGespeicherterDateiname("f0f0-uuid.jpg");
        b.setOriginalDateiname("IMG_0042.jpg");
        em.persist(b);
        ProjektNotizBild ohneName = new ProjektNotizBild();
        ohneName.setNotiz(n);
        ohneName.setGespeicherterDateiname("a1a1-uuid.png");
        em.persist(ohneName);
        em.flush();
        datei(uploads, "f0f0-uuid.jpg", "FOTO1");
        datei(uploads, "a1a1-uuid.png", "FOTO2");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isEqualTo(2);
        Path bilder = service.zielordner().resolve("Projekte/2026/Max Mustermann GmbH/2026-014 - Anbau Halle - Süd/Bilder");
        assertThat(Files.readString(bilder.resolve("IMG_0042.jpg"))).isEqualTo("FOTO1");
        assertThat(Files.readString(bilder.resolve("Foto " + ohneName.getId() + ".png"))).as("ohne Originalnamen: Foto <Nummer> mit der Endung der Datei").isEqualTo("FOTO2");
    }

    @Test
    void fotosAnAnfragenLiegenImBilderOrdnerDerAnfrageUndWerdenNachBilderspeicherGesucht() throws IOException {
        Kunde k = new Kunde();
        k.setKundennummer("K-2");
        k.setName("Muster GmbH");
        em.persist(k);
        Anfrage a = new Anfrage();
        a.setBauvorhaben("Geländer Hof");
        a.setAnlegedatum(LocalDate.of(2026, 5, 4));
        a.setKunde(k);
        em.persist(a);
        AnfrageNotiz n = new AnfrageNotiz();
        n.setAnfrage(a);
        n.setMitarbeiter(mitarbeiter());
        n.setNotiz("Aufmass");
        n.setErstelltAm(LocalDateTime.of(2026, 5, 5, 9, 0));
        em.persist(n);
        AnfrageNotizBild b = new AnfrageNotizBild();
        b.setNotiz(n);
        b.setGespeicherterDateiname("c3c3-uuid.jpg");
        b.setOriginalDateiname("Aufmass.jpg");
        b.setErstelltAm(LocalDateTime.of(2026, 5, 5, 9, 1));
        em.persist(b);
        em.flush();
        datei(uploads.resolve("images"), "c3c3-uuid.jpg", "AUFMASS");

        Ergebnis e = service.aufbauen(false);

        assertThat(e.dateien()).isEqualTo(1);
        Path erwartet = service.zielordner().resolve("Anfragen/2026/Muster GmbH - Geländer Hof (A" + a.getId() + ")/Bilder/Aufmass.jpg");
        assertThat(Files.readString(erwartet)).isEqualTo("AUFMASS");
    }
}
