package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme.Eingabe;
import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme.FremdleistungEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/** Das Fenster bei der Auftragsbestätigung: vorausgefüllt aus dem Auftrag, „Übernehmen“ legt die Kette an, „Später“ schätzt (#331, Abschnitt 5). Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class, PlanungsAufnahme.class })
class PlanungsAufnahmeTest {

    @MockBean
    private AngebotsZeiten angebotsZeiten;
    @MockBean
    private TagesSollService tagesSollService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsAufnahme aufnahme;
    @Autowired
    private TestEntityManager em;

    private Long projektId;
    private Long werkstatt;
    private Long max;
    private Long fremdleistungId;
    private Long lieferantId;

    @BeforeEach
    void setUp() {
        Abteilung a = new Abteilung();
        a.setName("Werkstatt");
        werkstatt = em.persistAndFlush(a).getId();
        planung.typSpeichern(null, "Planung", "#6366f1", 10, 2, true, null, null, false, true);
        planung.typSpeichern(null, "Materialbeschaffung", "#d97706", 20, 4, true, null, null, true, false);
        planung.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, werkstatt, null, false, false);
        planung.typSpeichern(null, "Veredelung", "#0891b2", 40, 5, true, null, null, true, false);
        planung.typSpeichern(null, "Montage", "#059669", 50, 2, true, null, null, false, false);
        Projekt p = new Projekt();
        p.setBauvorhaben("Geländer Musterstraße");
        p.setAuftragsnummer("A-1");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        projektId = em.persistAndFlush(p).getId();
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        max = em.persistAndFlush(m).getId();
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Verzinkerei Muster");
        l.setStandardLieferzeitTage(6);
        lieferantId = em.persistAndFlush(l).getId();
        Fremdleistung f = new Fremdleistung();
        f.setLieferant(em.find(Lieferanten.class, lieferantId));
        f.setBezeichnung("Feuerverzinken");
        f.setEinheit(Fremdleistung.Einheit.KG);
        f.setGueltigAb(LocalDate.now());
        fremdleistungId = em.persistAndFlush(f).getId();
        when(angebotsZeiten.fuerProjekt(projektId)).thenReturn(new AngebotsZeiten.Zeiten("AUFTRAGSBESTAETIGUNG", Map.of(werkstatt, new BigDecimal("40.00")), List.of(),
                List.of(new AngebotsZeiten.Fremd(fremdleistungId, "Feuerverzinken", "Verzinkerei Muster", "kg", new BigDecimal("250.5")))));
    }

    private List<String> namen(PlanungsService.AuftragDto a) {
        return a.vorgaenge().stream().map(PlanungsService.VorgangDto::bezeichnung).toList();
    }

    @Test
    void dasFensterIstAusDemAuftragVorausgefuellt() {
        var a = aufnahme.aufnahme(projektId);

        assertThat(a.bezeichnung()).isEqualTo("Geländer Musterstraße");
        assertThat(a.quelle()).isEqualTo("AUFTRAGSBESTAETIGUNG");
        assertThat(a.bereitsGeplant()).isFalse();
        assertThat(a.hatArbeitszeit()).isTrue();
        assertThat(a.fremdleistungen()).hasSize(1);
        var f = a.fremdleistungen().get(0);
        assertThat(f.bezeichnung()).isEqualTo("Feuerverzinken");
        assertThat(f.lieferantName()).isEqualTo("Verzinkerei Muster");
        assertThat(f.lieferzeitTage()).isEqualTo(6);
        assertThat(f.menge()).isEqualByComparingTo("250.5");
        assertThat(a.arten()).extracting(PlanungsAufnahme.ArtDto::name).containsExactly("Planung", "Materialbeschaffung", "Fertigung", "Veredelung", "Montage");
    }

    @Test
    void uebernehmenLegtDieKetteMitFremdleistungMontageUndPersonenAn() {
        LocalDate ziel = LocalDate.now().plusDays(60);
        var e = new Eingabe(false, ziel, "DRINGEND", List.of(new FremdleistungEingabe(fremdleistungId, "Feuerverzinken", lieferantId, new BigDecimal("250.5"), "kg")), true, 3, List.of(max));

        var a = aufnahme.planen(projektId, e);

        assertThat(namen(a)).containsExactly("Planung", "Materialbeschaffung", "Fertigung", "Feuerverzinken (Verzinkerei Muster)", "Montage");
        assertThat(a.zielDatum()).isEqualTo(ziel);
        assertThat(a.vorgaenge()).allSatisfy(v -> assertThat(v.prioritaet()).isEqualTo("DRINGEND"));
        var fertigung = a.vorgaenge().get(2);
        assertThat(fertigung.aufwandStunden()).isEqualByComparingTo("40");
        assertThat(fertigung.aufwandHerkunft()).as("aus der Kalkulation").isEqualTo("KALKULATION");
        assertThat(fertigung.mitarbeiterIds()).containsExactly(max);
        var planungVorgang = a.vorgaenge().get(0);
        assertThat(planungVorgang.mitarbeiterIds()).as("Büroarbeit bekommt die Wunschpersonen nicht").isEmpty();
        var veredelung = a.vorgaenge().get(3);
        assertThat(veredelung.lieferantId()).isEqualTo(lieferantId);
        assertThat(veredelung.fremdleistungId()).isEqualTo(fremdleistungId);
        assertThat(veredelung.menge()).isEqualByComparingTo("250.5");
        assertThat(veredelung.mengenEinheit()).isEqualTo("KG");
        assertThat(veredelung.dauerTage()).as("übliche Lieferzeit des Veredlers").isEqualTo(6);
        var montage = a.vorgaenge().get(4);
        assertThat(montage.aufwandStunden()).as("3 Tage mal 8 Stunden mal 1 Person").isEqualByComparingTo("24");
        assertThat(montage.mitarbeiterIds()).containsExactly(max);
        for (int i = 1; i < a.vorgaenge().size(); i++) {
            assertThat(a.vorgaenge().get(i).vorgaenger()).extracting(PlanungsService.AbhaengigkeitDto::vorgaengerId).containsExactly(a.vorgaenge().get(i - 1).id());
        }
        assertThat(aufnahme.aufnahme(projektId).bereitsGeplant()).isTrue();
    }

    @Test
    void ohneFremdleistungUndOhneMontageFehlenVeredelungUndMontage() {
        var a = aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(), false, null, List.of()));

        assertThat(namen(a)).containsExactly("Planung", "Materialbeschaffung", "Fertigung");
        assertThat(a.vorgaenge()).allSatisfy(v -> assertThat(v.prioritaet()).isEqualTo("NORMAL"));
    }

    @Test
    void spaeterLegtDieStandardketteMitGeschaetztenWertenAnUndMerktDenAuftragAlsOffen() {
        var a = aufnahme.planen(projektId, new Eingabe(true, null, null, null, false, null, null));

        assertThat(namen(a)).hasSize(5);
        assertThat(a.vorgaenge()).allSatisfy(v -> assertThat(v.dauerHerkunft()).isEqualTo("GESCHAETZT"));
        assertThat(planung.offeneAuftraege()).extracting(PlanungsService.OffenerAuftrag::projektId).containsExactly(projektId);
        assertThat(aufnahme.aufnahme(projektId).offen()).isTrue();
    }

    @Test
    void einOffenerAuftragWirdBeimPlanenNeuAufgebautUndIstDanachNichtMehrOffen() {
        aufnahme.planen(projektId, new Eingabe(true, null, null, null, false, null, null));

        var a = aufnahme.planen(projektId, new Eingabe(false, null, "NICHT_DRINGEND", List.of(), false, null, List.of()));

        assertThat(namen(a)).containsExactly("Planung", "Materialbeschaffung", "Fertigung");
        assertThat(a.vorgaenge()).allSatisfy(v -> assertThat(v.prioritaet()).isEqualTo("NICHT_DRINGEND"));
        assertThat(planung.offeneAuftraege()).isEmpty();
    }

    @Test
    void einBereitsGeplanterAuftragLaesstSichNichtNochmalPlanen() {
        aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(), false, null, List.of()));

        assertThatThrownBy(() -> aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(), false, null, List.of()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schon Vorgänge");
    }

    @Test
    void eingabenWerdenGeprueft() {
        assertThatThrownBy(() -> aufnahme.planen(projektId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aufnahme.planen(projektId, new Eingabe(false, null, "SOFORT", List.of(), false, null, List.of()))).hasMessageContaining("Priorität");
        assertThatThrownBy(() -> aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(), true, 0, List.of()))).hasMessageContaining("Montage");
        assertThatThrownBy(() -> aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(), false, null, List.of(999999L)))).hasMessageContaining("Mitarbeiter");
        assertThatThrownBy(() -> aufnahme.planen(projektId, new Eingabe(false, null, null, List.of(new FremdleistungEingabe(null, "x", 999999L, null, null)), false, null, List.of()))).hasMessageContaining("Lieferanten");
        assertThatThrownBy(() -> aufnahme.planen(999999L, new Eingabe(false, null, null, List.of(), false, null, List.of()))).hasMessageContaining("Auftrag");
    }

    @Test
    void dasFensterLaesstSichProBenutzerAbschalten() {
        assertThat(aufnahme.fensterAktiv()).isTrue();
        aufnahme.fensterSetzen(false);
        assertThat(aufnahme.fensterAktiv()).isFalse();
        aufnahme.fensterSetzen(true);
        assertThat(aufnahme.fensterAktiv()).isTrue();
    }
}
