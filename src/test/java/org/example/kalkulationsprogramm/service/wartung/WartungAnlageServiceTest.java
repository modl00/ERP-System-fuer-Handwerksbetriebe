package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungAnlageDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Die Wartung als Ganzes: Historie, Zuordnung zu einem Projekt und der Bereich "Wartungen" im Projekt (gegen die Test-Datenbank). */
@DataJpaTest
@Import(WartungAnlageService.class)
class WartungAnlageServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private WartungAnlageService service;

    private Kunde kunde;
    private Kunde andererKunde;
    private WartungObjekt objekt;
    private WartungTuer heizung;
    private Projekt projektA;
    private Projekt projektB;
    private Mitarbeiter mueller;
    private Mitarbeiter schmidt;
    private int nummer = 1000;
    private int projektNummer = 0;

    @BeforeEach
    void setUp() {
        kunde = kunde("K-1", "Max Mustermann GmbH");
        andererKunde = kunde("K-2", "Erika Beispiel AG");
        objekt = new WartungObjekt();
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        objekt.setStrasse("Industriestraße 12");
        objekt.setPlz("97080");
        objekt.setOrt("Würzburg");
        em.persist(objekt);
        heizung = tuer("Heizungsanlage", objekt);
        projektA = projekt("Neubau Halle A", kunde);
        projektB = projekt("Umbau Halle B", kunde);
        mueller = mitarbeiter("Max", "Müller");
        schmidt = mitarbeiter("Erika", "Schmidt");
        flush();
    }

    private Kunde kunde(String nummer, String name) {
        Kunde k = new Kunde();
        k.setKundennummer(nummer);
        k.setName(name);
        return em.persist(k);
    }

    private WartungTuer tuer(String name, WartungObjekt objekt) {
        WartungTuer t = new WartungTuer();
        t.setObjekt(objekt);
        t.setBezeichnung(name);
        return em.persist(t);
    }

    private Projekt projekt(String bauvorhaben, Kunde kunde) {
        Projekt p = new Projekt();
        p.setBauvorhaben(bauvorhaben);
        p.setAnlegedatum(LocalDate.of(2026, 1, 15));
        p.setBruttoPreis(java.math.BigDecimal.ZERO);
        p.setAuftragsnummer("A-" + (++projektNummer));
        p.setKundenId(kunde);
        return em.persist(p);
    }

    private Mitarbeiter mitarbeiter(String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setAktiv(true);
        return em.persist(m);
    }

    private WartungProtokoll pruefung(WartungTuer tuer, LocalDate datum, WartungStatus status, Mitarbeiter techniker, Projekt projekt) {
        WartungProtokoll p = new WartungProtokoll();
        p.setNummer("WA-2026-" + (++nummer));
        p.setKunde(tuer.getObjekt().getKunde());
        p.setObjekt(tuer.getObjekt());
        p.setTuer(tuer);
        p.setTechniker(techniker);
        p.setProjekt(projekt);
        p.setDatum(datum);
        p.setIntervallMonate(6);
        p.setStatus(status);
        if (status == WartungStatus.ABGESCHLOSSEN || status == WartungStatus.ARCHIVIERT) {
            p.setAbgeschlossenAm(datum.atTime(12, 0));
        }
        return em.persist(p);
    }

    private void punkt(WartungProtokoll p, String label, WartungErgebnis ergebnis) {
        WartungProtokollPruefpunkt pp = new WartungProtokollPruefpunkt();
        pp.setProtokollId(p.getId());
        pp.setLabel(label);
        pp.setErgebnis(ergebnis);
        em.persist(pp);
    }

    private ProjektDokument bericht(Projekt projekt, String dateiname) {
        ProjektDokument d = new ProjektDokument();
        d.setProjekt(projekt);
        d.setOriginalDateiname(dateiname);
        d.setGespeicherterDateiname("gespeichert-" + dateiname);
        d.setDokumentGruppe(DokumentGruppe.DIVERSE_DOKUMENTE);
        d.setUploadDatum(LocalDate.now());
        return em.persist(d);
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    // ── Historie ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Die Historie nummeriert die Prüfungen von der ältesten an, zeigt Mitarbeiter, Zahlen, Vorlage und Bericht")
    void historie() {
        WartungProtokoll erste = pruefung(heizung, LocalDate.of(2026, 10, 1), WartungStatus.ABGESCHLOSSEN, mueller, projektA);
        WartungProtokoll zweite = pruefung(heizung, LocalDate.of(2027, 4, 1), WartungStatus.ABGESCHLOSSEN, mueller, projektA);
        WartungProtokoll dritte = pruefung(heizung, LocalDate.of(2027, 10, 1), WartungStatus.IN_BEARBEITUNG, schmidt, projektA);
        zweite.setVorlageProtokollId(erste.getId());
        dritte.setVorlageProtokollId(zweite.getId());
        punkt(erste, "Druck", WartungErgebnis.IO);
        punkt(erste, "Ventil", WartungErgebnis.NIO);
        punkt(dritte, "Druck", WartungErgebnis.IO);
        punkt(dritte, "Ventil", null);
        ProjektDokument pdf = bericht(projektA, "Wartungsbericht_WA-2026-1001.pdf");
        erste.setBerichtDokumentId(pdf.getId());
        flush();

        WartungAnlageDto anlage = service.laden(heizung.getId());

        assertThat(anlage.historie()).extracting(WartungAnlageDto.Pruefung::pruefung).containsExactly(1, 2, 3);
        assertThat(anlage.historie()).extracting(WartungAnlageDto.Pruefung::datum)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 10, 1));
        assertThat(anlage.historie()).extracting(WartungAnlageDto.Pruefung::technikerName).containsExactly("Max Müller", "Max Müller", "Erika Schmidt");
        WartungAnlageDto.Pruefung eins = anlage.historie().get(0);
        assertThat(eins).extracting(WartungAnlageDto.Pruefung::pruefpunkteGesamt, WartungAnlageDto.Pruefung::pruefpunkteBewertet, WartungAnlageDto.Pruefung::maengel)
                .containsExactly(2L, 2L, 1L);
        assertThat(eins.berichtUrl()).isEqualTo("/api/dokumente/gespeichert-Wartungsbericht_WA-2026-1001.pdf");
        assertThat(anlage.historie().get(1).vorlageProtokollId()).isEqualTo(erste.getId());
        WartungAnlageDto.Pruefung drei = anlage.historie().get(2);
        assertThat(drei).extracting(WartungAnlageDto.Pruefung::pruefpunkteGesamt, WartungAnlageDto.Pruefung::pruefpunkteBewertet, WartungAnlageDto.Pruefung::maengel)
                .containsExactly(2L, 1L, 0L);
        assertThat(drei.berichtUrl()).isNull();
        // Prüfungen ohne Prüfpunkte zählen null
        assertThat(anlage.historie().get(1).pruefpunkteGesamt()).isZero();
    }

    @Test
    @DisplayName("Die Detailseite nennt den offenen Feldeinsatz und die neueste abgeschlossene Prüfung als Vorlage")
    void offenerFeldeinsatzUndVorlage() {
        WartungProtokoll alt = pruefung(heizung, LocalDate.of(2026, 4, 1), WartungStatus.ABGESCHLOSSEN, mueller, null);
        WartungProtokoll neuer = pruefung(heizung, LocalDate.of(2026, 10, 1), WartungStatus.ARCHIVIERT, mueller, null);
        WartungProtokoll offen = pruefung(heizung, LocalDate.of(2027, 4, 1), WartungStatus.OFFEN, mueller, null);
        flush();

        WartungAnlageDto anlage = service.laden(heizung.getId());

        assertThat(anlage.offenerFeldeinsatzId()).isEqualTo(offen.getId());
        assertThat(anlage.offenerFeldeinsatzNummer()).isEqualTo(offen.getNummer());
        assertThat(anlage.letztePruefungId()).isEqualTo(neuer.getId()).isNotEqualTo(alt.getId());
        assertThat(anlage.kunde().name()).isEqualTo("Max Mustermann GmbH");
        assertThat(anlage.objekt().ort()).isEqualTo("Würzburg");
    }

    @Test
    @DisplayName("Die Module der Wartung stehen mit Name, Bauart und Schlüssel der Bauart da")
    void module() {
        org.example.kalkulationsprogramm.domain.wartung.WartungModul modul = new org.example.kalkulationsprogramm.domain.wartung.WartungModul();
        modul.setModulKey("m_heizung");
        modul.setName("Heizungsanlage");
        em.persist(modul);
        org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp bauart = new org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp();
        bauart.setModulId(modul.getId());
        bauart.setSchluessel("gas");
        bauart.setLabel("Gas-Brennwert");
        em.persist(bauart);
        WartungTuer verwaltet = em.find(WartungTuer.class, heizung.getId());
        verwaltet.getModule().add(new org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl(modul.getId(), "gas"));
        flush();

        WartungAnlageDto anlage = service.laden(heizung.getId());

        assertThat(anlage.module()).containsExactly(new WartungAnlageDto.Modul(modul.getId(), "Heizungsanlage", "Gas-Brennwert", "gas"));
    }

    @Test
    @DisplayName("Ohne Prüfungen gibt es keine Vorlage und keinen offenen Feldeinsatz")
    void ohnePruefungen() {
        WartungAnlageDto anlage = service.laden(heizung.getId());

        assertThat(anlage.historie()).isEmpty();
        assertThat(anlage.offenerFeldeinsatzId()).isNull();
        assertThat(anlage.letztePruefungId()).isNull();
        assertThat(anlage.projekt()).isNull();
    }

    @Test
    @DisplayName("Eine unbekannte Wartung ergibt 404")
    void unbekannt() {
        assertThatThrownBy(() -> service.laden(999_999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Projekt zuordnen ────────────────────────────────────────────────────

    @Test
    @DisplayName("Zuordnen, ändern und trennen: Es ist immer dieselbe Wartung, und es entsteht keine zweite")
    void projektZuordnen() {
        long vorher = em.getEntityManager().createQuery("select count(t) from WartungTuer t", Long.class).getSingleResult();

        assertThat(service.projektZuordnen(heizung.getId(), projektA.getId()).projekt().id()).isEqualTo(projektA.getId());
        flush();
        assertThat(service.laden(heizung.getId()).projekt().name()).contains("Neubau Halle A");

        assertThat(service.projektZuordnen(heizung.getId(), projektB.getId()).projekt().id()).isEqualTo(projektB.getId());
        flush();
        assertThat(service.projektWartungen(projektA.getId())).isEmpty();
        assertThat(service.projektWartungen(projektB.getId())).extracting(WartungProjektWartungDto::id).containsExactly(heizung.getId());
        // Für "Wartung starten" im Projekt braucht die Oberfläche das Objekt der Anlage
        assertThat(service.projektWartungen(projektB.getId())).extracting(WartungProjektWartungDto::objektId).containsExactly(heizung.getObjekt().getId());

        assertThat(service.projektZuordnen(heizung.getId(), null).projekt()).isNull();
        flush();
        assertThat(service.projektWartungen(projektB.getId())).isEmpty();

        long nachher = em.getEntityManager().createQuery("select count(t) from WartungTuer t", Long.class).getSingleResult();
        assertThat(nachher).isEqualTo(vorher);
    }

    @Test
    @DisplayName("Ein Projektwechsel verschiebt keine bestehenden Prüfungen und ihre Berichte in ein anderes Projekt")
    void projektwechselLaesstPruefungenInRuhe() {
        WartungProtokoll pruefung = pruefung(heizung, LocalDate.of(2026, 10, 1), WartungStatus.ABGESCHLOSSEN, mueller, projektA);
        flush();

        service.projektZuordnen(heizung.getId(), projektB.getId());
        flush();

        WartungProtokoll danach = em.find(WartungProtokoll.class, pruefung.getId());
        assertThat(danach.getProjekt().getId()).isEqualTo(projektA.getId());
    }

    @Test
    @DisplayName("Ein Projekt eines anderen Kunden oder ein unbekanntes Projekt wird abgelehnt und ändert nichts")
    void projektFehler() {
        Projekt fremd = projekt("Projekt des anderen Kunden", andererKunde);
        flush();
        service.projektZuordnen(heizung.getId(), projektA.getId());
        flush();

        assertThatThrownBy(() -> service.projektZuordnen(heizung.getId(), fremd.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("anderen Kunden");
                });
        assertThatThrownBy(() -> service.projektZuordnen(heizung.getId(), 999_999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        flush();
        assertThat(service.laden(heizung.getId()).projekt().id()).isEqualTo(projektA.getId());
    }

    // ── Bereich "Wartungen" im Projekt ──────────────────────────────────────

    @Test
    @DisplayName("Im Projekt stehen nur die aktiven Wartungen dieses Projekts, nicht die eines anderen oder ohne Projekt")
    void nurWartungenDiesesProjekts() {
        heizung = em.find(WartungTuer.class, heizung.getId());
        WartungTuer inA = tuer("Lüftung", objekt);
        WartungTuer inB = tuer("Brandschutztür", objekt);
        WartungTuer ohne = tuer("Tor 3", objekt);
        WartungTuer inaktiv = tuer("Altes Tor", objekt);
        heizung.setProjekt(projektA);
        inA.setProjekt(projektA);
        inB.setProjekt(projektB);
        inaktiv.setProjekt(projektA);
        inaktiv.setAktiv(false);
        flush();

        List<WartungProjektWartungDto> a = service.projektWartungen(projektA.getId());
        List<WartungProjektWartungDto> b = service.projektWartungen(projektB.getId());

        assertThat(a).extracting(WartungProjektWartungDto::bezeichnung).containsExactly("Heizungsanlage", "Lüftung");
        assertThat(b).extracting(WartungProjektWartungDto::bezeichnung).containsExactly("Brandschutztür");
        assertThat(a).extracting(WartungProjektWartungDto::id).doesNotContain(inB.getId(), ohne.getId(), inaktiv.getId());
        assertThat(a.get(0)).extracting(WartungProjektWartungDto::objektBezeichnung, WartungProjektWartungDto::kundeName)
                .containsExactly("Werk Nord", "Max Mustermann GmbH");
    }

    @Test
    @DisplayName("Im Projekt stehen je Wartung Anzahl der Prüfungen, letzte Prüfung, offener Feldeinsatz und die abgelegten Berichte")
    void projektBereichMitBerichten() {
        heizung = em.find(WartungTuer.class, heizung.getId());
        heizung.setProjekt(projektA);
        heizung.setNaechsteFaelligkeit(LocalDate.of(2027, 4, 1));
        WartungProtokoll erste = pruefung(heizung, LocalDate.of(2026, 10, 1), WartungStatus.ABGESCHLOSSEN, mueller, projektA);
        WartungProtokoll zweite = pruefung(heizung, LocalDate.of(2027, 4, 1), WartungStatus.ABGESCHLOSSEN, mueller, projektA);
        WartungProtokoll offen = pruefung(heizung, LocalDate.of(2027, 10, 1), WartungStatus.OFFEN, mueller, projektA);
        erste.setBerichtDokumentId(bericht(projektA, "Bericht1.pdf").getId());
        zweite.setBerichtDokumentId(bericht(projektA, "Bericht2.pdf").getId());
        offen.setBerichtDokumentId(999_999L); // Das Dokument wurde im Projekt gelöscht
        flush();

        WartungProjektWartungDto wartung = service.projektWartungen(projektA.getId()).get(0);

        assertThat(wartung.anzahlPruefungen()).isEqualTo(3);
        assertThat(wartung.letztePruefungDatum()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(wartung.naechsteFaelligkeit()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(wartung.offenerFeldeinsatzId()).isEqualTo(offen.getId());
        assertThat(wartung.berichte()).extracting(WartungProjektWartungDto.Bericht::dateiname).containsExactly("Bericht2.pdf", "Bericht1.pdf");
        assertThat(wartung.berichte().get(0).url()).isEqualTo("/api/dokumente/gespeichert-Bericht2.pdf");
        assertThat(wartung.berichte().get(0).protokollId()).isEqualTo(zweite.getId());
    }

    @Test
    @DisplayName("Zur Auswahl gibt es nur die Projekte des eigenen Kunden, die neuesten zuerst")
    void projekteDesKunden() {
        Projekt neuer = projekt("Neuestes Projekt", kunde);
        neuer.setAnlegedatum(LocalDate.of(2026, 9, 1));
        projekt("Projekt des anderen Kunden", andererKunde);
        flush();

        List<WartungAnlageDto.Bezug> projekte = service.projekteDesKunden(kunde.getId());

        assertThat(projekte).extracting(WartungAnlageDto.Bezug::name).hasSize(3);
        assertThat(projekte.get(0).id()).isEqualTo(neuer.getId());
        assertThat(projekte).extracting(WartungAnlageDto.Bezug::name).noneMatch(n -> n.contains("anderen Kunden"));
        assertThat(service.projekteDesKunden(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("Ein unbekanntes Projekt ergibt 404, ein Projekt ohne Wartungen eine leere Liste")
    void projektOhneWartungen() {
        assertThat(service.projektWartungen(projektA.getId())).isEmpty();
        assertThatThrownBy(() -> service.projektWartungen(999_999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
