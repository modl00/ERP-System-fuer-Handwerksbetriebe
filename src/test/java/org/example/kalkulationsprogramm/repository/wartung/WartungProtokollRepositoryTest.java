package org.example.kalkulationsprogramm.repository.wartung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollListeneintragDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/** Prüft die Listenabfrage der Protokolle gegen die Test-Datenbank: Filter, Zähler, Sortierung, Suche. */
@DataJpaTest
class WartungProtokollRepositoryTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private WartungProtokollRepository repository;

    private Kunde kunde;
    private WartungObjekt objekt;
    private WartungTuer tuer;
    private WartungProtokoll alt;
    private WartungProtokoll neu;
    private WartungProtokoll archiviert;

    @BeforeEach
    void setUp() {
        kunde = new Kunde();
        kunde.setKundennummer("K-1");
        kunde.setName("Max Mustermann GmbH");
        em.persist(kunde);

        objekt = new WartungObjekt();
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        em.persist(objekt);

        tuer = new WartungTuer();
        tuer.setObjekt(objekt);
        tuer.setBezeichnung("Tür 1.01");
        em.persist(tuer);

        alt = protokoll("WA-2026-1001", "Jahreswartung", LocalDate.of(2026, 1, 10), WartungStatus.ABGESCHLOSSEN, tuer);
        neu = protokoll("WA-2026-1002", "Nachprüfung", LocalDate.of(2026, 6, 20), WartungStatus.OFFEN, tuer);
        archiviert = protokoll("WA-2025-1050", "Altbestand", LocalDate.of(2025, 3, 1), WartungStatus.ARCHIVIERT, null);

        punkt(alt, "Schild", WartungErgebnis.IO);
        punkt(alt, "Bänder", WartungErgebnis.NIO);
        punkt(alt, "Schließer", WartungErgebnis.NA);
        punkt(neu, "Schild", null);
        punkt(neu, "Bänder", null);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Ohne Archiv: neueste zuerst, Zähler stimmen")
    void listeOhneArchiv() {
        List<WartungProtokollListeneintragDto> liste = suche(null, null, WartungStatus.ARCHIVIERT, null, null);

        assertThat(liste).extracting(WartungProtokollListeneintragDto::nummer).containsExactly("WA-2026-1002", "WA-2026-1001");
        WartungProtokollListeneintragDto eintrag = liste.get(1);
        assertThat(eintrag.pruefpunkteGesamt()).isEqualTo(3);
        assertThat(eintrag.pruefpunkteBewertet()).isEqualTo(3);
        assertThat(eintrag.maengel()).isEqualTo(1);
        assertThat(liste.get(0).pruefpunkteGesamt()).isEqualTo(2);
        assertThat(liste.get(0).pruefpunkteBewertet()).isZero();
        assertThat(eintrag.kundeName()).isEqualTo("Max Mustermann GmbH");
        assertThat(eintrag.objektBezeichnung()).isEqualTo("Werk Nord");
        assertThat(eintrag.tuerBezeichnung()).isEqualTo("Tür 1.01");
    }

    @Test
    @DisplayName("Mit Archiv kommt auch das Protokoll ohne Objekt und Tür vor")
    void listeMitArchiv() {
        List<WartungProtokollListeneintragDto> liste = suche(null, null, null, null, null);

        assertThat(liste).extracting(WartungProtokollListeneintragDto::nummer)
                .containsExactly("WA-2026-1002", "WA-2026-1001", "WA-2025-1050");
        WartungProtokollListeneintragDto ohneObjekt = liste.get(2);
        assertThat(ohneObjekt.objektId()).isNull();
        assertThat(ohneObjekt.tuerId()).isNull();
    }

    @Test
    @DisplayName("Filter nach Tür, Status und Datum")
    void filter() {
        assertThat(suche(null, tuer.getId(), null, null, null)).hasSize(2);
        assertThat(repository.suche(null, null, null, WartungStatus.OFFEN, null, null, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(WartungProtokollListeneintragDto::nummer).containsExactly("WA-2026-1002");
        assertThat(repository.suche(null, null, null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null,
                PageRequest.of(0, 10)).getContent())
                .extracting(WartungProtokollListeneintragDto::nummer).containsExactly("WA-2026-1001");
        assertThat(suche(999_999L, null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("Suche über Nummer, Titel, Kunde, Objekt und Tür, ohne Groß-/Kleinschreibung")
    void sucheUeberTexte() {
        assertThat(suche(null, null, null, null, "%1002%")).hasSize(1);
        assertThat(suche(null, null, null, null, "%nachprüfung%")).hasSize(1);
        assertThat(suche(null, null, null, null, "%max mustermann%")).hasSize(3);
        assertThat(suche(null, null, null, null, "%werk nord%")).hasSize(2);
        assertThat(suche(null, null, null, null, "%tür 1.01%")).hasSize(2);
        assertThat(suche(null, null, null, null, "%gibtesnicht%")).isEmpty();
    }

    @Test
    @DisplayName("Maskierte Platzhalter finden nur den Text selbst, SQL-Zeichen sind harmlos")
    void sucheMitMaskierung() {
        assertThat(suche(null, null, null, null, "%100!%%")).isEmpty();
        assertThat(suche(null, null, null, null, "%!_%")).isEmpty();
        assertThat(suche(null, null, null, null, "%'; drop table wartung_protokoll; --%")).isEmpty();
        assertThat(suche(null, null, null, null, null)).hasSize(3);
    }

    @Test
    @DisplayName("Der letzte abgeschlossene Vorgänger einer Tür wird gefunden, auch wenn er archiviert ist")
    void vorgaengerAuchImArchiv() {
        WartungProtokoll geladen = em.find(WartungProtokoll.class, alt.getId());
        geladen.setAbgeschlossenAm(java.time.LocalDateTime.of(2026, 1, 10, 12, 0));
        geladen.setStatus(WartungStatus.ARCHIVIERT);
        em.flush();
        em.clear();

        List<WartungProtokoll> vorgaenger = repository
                .findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(tuer.getId(), PageRequest.of(0, 1));

        assertThat(vorgaenger).extracting(WartungProtokoll::getNummer).containsExactly("WA-2026-1001");
    }

    private List<WartungProtokollListeneintragDto> suche(Long kundeId, Long tuerId, WartungStatus ausschluss, WartungStatus status, String suche) {
        return repository.suche(kundeId, null, tuerId, status, ausschluss, null, null, suche, PageRequest.of(0, 50)).getContent();
    }

    private WartungProtokoll protokoll(String nummer, String titel, LocalDate datum, WartungStatus status, WartungTuer tuer) {
        WartungProtokoll p = new WartungProtokoll();
        p.setNummer(nummer);
        p.setKunde(kunde);
        p.setObjekt(tuer == null ? null : objekt);
        p.setTuer(tuer);
        p.setTitel(titel);
        p.setDatum(datum);
        p.setIntervallMonate(12);
        p.setStatus(status);
        em.persist(p);
        return p;
    }

    private void punkt(WartungProtokoll protokoll, String label, WartungErgebnis ergebnis) {
        WartungProtokollPruefpunkt punkt = new WartungProtokollPruefpunkt();
        punkt.setProtokollId(protokoll.getId());
        punkt.setLabel(label);
        punkt.setErgebnis(ergebnis);
        em.persist(punkt);
    }
}
