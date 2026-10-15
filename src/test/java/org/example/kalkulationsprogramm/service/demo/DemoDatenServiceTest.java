package org.example.kalkulationsprogramm.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.planung.AngebotsZeiten;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung;
import org.example.kalkulationsprogramm.service.planung.PlanKapazitaetQuelle;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen;
import org.example.kalkulationsprogramm.service.planung.PlanungsHistorie;
import org.example.kalkulationsprogramm.service.planung.PlanungsIst;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/** Die früheren Demo-Daten entfernen: Nur was die Demo-Daten angelegt haben, alles andere bleibt. Nur Dummy-Daten. */
@DataJpaTest
@Import({ DemoDatenService.class, PlanungsService.class, PlanKapazitaetQuelle.class, PlanungsIst.class, PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class, PlanBelegung.class })
class DemoDatenServiceTest {

    @MockBean
    private AngebotsZeiten angebotsZeiten;
    @MockBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;
    @Autowired
    private DemoDatenService demo;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private KundeRepository kunden;
    @Autowired
    private ProjektRepository projekte;
    @Autowired
    private LeistungRepository leistungen;
    @Autowired
    private LieferantenRepository lieferanten;
    @Autowired
    private MitarbeiterRepository mitarbeiter;
    @Autowired
    private KalenderEintragRepository kalender;

    private Kunde kunde(String nummer, String name) {
        Kunde k = new Kunde();
        k.setKundennummer(nummer);
        k.setName(name);
        return kunden.save(k);
    }

    private Projekt projekt(String nummer, Kunde k) {
        Projekt p = new Projekt();
        p.setBauvorhaben("Auftrag " + nummer);
        p.setAuftragsnummer(nummer);
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        p.setKundenId(k);
        return projekte.save(p);
    }

    private Leistung leistung(String name) {
        Leistung l = new Leistung();
        l.setBezeichnung(name);
        l.setEinheit(Verrechnungseinheit.STUECK);
        l.setPreis(BigDecimal.ZERO);
        return leistungen.save(l);
    }

    private Mitarbeiter person(String vor, String mail) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vor);
        m.setNachname("Test");
        m.setEmail(mail);
        return mitarbeiter.save(m);
    }

    private void termin(String beschreibung) {
        KalenderEintrag k = new KalenderEintrag();
        k.setTitel("Termin");
        k.setDatum(LocalDate.now());
        k.setStartZeit(LocalTime.of(8, 0));
        k.setEndeZeit(LocalTime.of(9, 0));
        k.setBeschreibung(beschreibung);
        kalender.save(k);
    }

    @Test
    void ohneDemoDatenGibtEsNichtsZuEntfernen() {
        assertThat(demo.vorhanden()).isFalse();
        var e = demo.entfernen();
        assertThat(e).isEqualTo(new DemoDatenService.Ergebnis(0, 0, 0, 0, 0, 0));
    }

    @Test
    void entferntNurDieDemoDatenUndLaesstEchtesStehen() {
        Kunde demoKunde = kunde("DEMO-K01", "Mustermann Bau GmbH");
        Kunde echt = kunde("K-100", "Echter Kunde");
        Projekt demoAuftrag = projekt("DEMO-2026-001", demoKunde);
        Projekt echterAuftrag = projekt("A-2026-017", echt);
        leistung("Geländer, Stahl, gestrichen (Demo)");
        leistung("Geländer, Stahl, gestrichen");
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Feuerverzinkerei Demo GmbH");
        lieferanten.save(l);
        Lieferanten echterLieferant = new Lieferanten();
        echterLieferant.setLieferantenname("Feuerverzinkerei Schmidt");
        lieferanten.save(echterLieferant);
        person("Max", "max@demo.invalid");
        person("Erika", "erika@firma.de");
        termin("Demo-Termin");
        termin("Besprechung mit Kunde");
        Long typ = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, null, null).id();
        planung.vorgangAnlegen(demoAuftrag.getId(), new VorgangEingabe(typ, null, null, null, null, null));
        planung.vorgangAnlegen(echterAuftrag.getId(), new VorgangEingabe(typ, null, null, null, null, null));
        assertThat(demo.vorhanden()).isTrue();

        var e = demo.entfernen();

        assertThat(e).isEqualTo(new DemoDatenService.Ergebnis(1, 1, 1, 1, 1, 1));
        assertThat(demo.vorhanden()).isFalse();
        assertThat(kunden.findAll()).extracting(Kunde::getKundennummer).containsExactly("K-100");
        assertThat(projekte.findAll()).extracting(Projekt::getAuftragsnummer).containsExactly("A-2026-017");
        assertThat(leistungen.findAll()).extracting(Leistung::getBezeichnung).containsExactly("Geländer, Stahl, gestrichen");
        assertThat(lieferanten.findAll()).extracting(Lieferanten::getLieferantenname).containsExactly("Feuerverzinkerei Schmidt");
        assertThat(mitarbeiter.findAll()).extracting(Mitarbeiter::getEmail).containsExactly("erika@firma.de");
        assertThat(kalender.findAll()).extracting(KalenderEintrag::getBeschreibung).containsExactly("Besprechung mit Kunde");
        assertThat(planung.auftraege()).extracting(PlanungsService.AuftragDto::projektId).containsExactly(echterAuftrag.getId());
    }

    @Test
    void einZweiterLaufEntferntNichtsMehr() {
        kunde("DEMO-K02", "Familie Beispiel");
        assertThat(demo.entfernen().kunden()).isEqualTo(1);
        assertThat(demo.entfernen().kunden()).isZero();
    }
}
