package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

/** Stempelung als Fortschritt: Gestempelte Stunden zählen vom Aufwand ab, nur der Rest wird verplant (#331, Abschnitt 3.10). Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class, PlanungsIst.class })
class PlanungsIstTest {

    @MockitoBean
    private AngebotsZeiten angebotsZeiten;
    @MockitoBean
    private TagesSollService tagesSollService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsZuweisung zuweisung;
    @Autowired
    private PlanungsIst ist;
    @Autowired
    private TestEntityManager em;

    private Long projektId;
    private Long werkstatt;
    private Long schweissen;
    private Long montageGang;
    private Long fertigungArt;
    private Long montageArt;
    private Long max;

    @BeforeEach
    void setUp() {
        Abteilung a = new Abteilung();
        a.setName("Werkstatt");
        werkstatt = em.persistAndFlush(a).getId();
        schweissen = arbeitsgang("Schweißen", a);
        montageGang = arbeitsgang("Montage Baustelle", a);
        fertigungArt = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, werkstatt, null).id();
        montageArt = planung.typSpeichern(null, "Montage", "#059669", 50, 2, true, werkstatt, null).id();
        Projekt p = new Projekt();
        p.setBauvorhaben("Geländer");
        p.setAuftragsnummer("A-1");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        projektId = em.persistAndFlush(p).getId();
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        max = em.persistAndFlush(m).getId();
    }

    private Long arbeitsgang(String name, Abteilung a) {
        Arbeitsgang g = new Arbeitsgang();
        g.setBeschreibung(name);
        g.setAbteilung(a);
        return em.persistAndFlush(g).getId();
    }

    private void stempel(Long arbeitsgangId, String stunden, BuchungsTyp typ, LocalDateTime start, Long mitarbeiterId) {
        Zeitbuchung z = new Zeitbuchung();
        z.setProjekt(em.find(Projekt.class, projektId));
        z.setArbeitsgang(em.find(Arbeitsgang.class, arbeitsgangId));
        z.setMitarbeiter(em.find(Mitarbeiter.class, mitarbeiterId));
        z.setAnzahlInStunden(stunden == null ? null : new BigDecimal(stunden));
        z.setStartZeit(start);
        z.setEndeZeit(stunden == null ? null : start.plusHours(1));
        z.setTyp(typ);
        em.persistAndFlush(z);
    }

    private PlanungsService.VorgangDto vorgang(int index) {
        return planung.auftrag(projektId).vorgaenge().get(index);
    }

    @Test
    void gestempelteStundenZaehlenVomAufwandAbUndVerkuerzenDenVorgang() {
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Geländer fertigen", null, null, null, null, new BigDecimal("40")));
        Long vorgangId = vorgang(0).id();
        zuweisung.zuweisen(vorgangId, new PlanungsZuweisung.Zuweisung(List.of(max), null, null));
        assertThat(vorgang(0).dauerTage()).isEqualTo(5);

        stempel(schweissen, "16", BuchungsTyp.ARBEIT, LocalDateTime.now().minusDays(3), max);
        planung.neuBerechnenAlle();

        var v = vorgang(0);
        assertThat(v.istStunden()).isEqualByComparingTo("16");
        assertThat(v.dauerTage()).as("noch 24 Stunden = 3 Tage").isEqualTo(3);
        assertThat(v.aufwandStunden()).as("der Aufwand selbst bleibt").isEqualByComparingTo("40");
    }

    @Test
    void pausenLaufendeBuchungenUndAndereAuftraegeZaehlenNicht() {
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Geländer fertigen", null, null, null, null, new BigDecimal("40")));
        stempel(schweissen, "8", BuchungsTyp.PAUSE, LocalDateTime.now().minusDays(2), max);
        stempel(schweissen, null, BuchungsTyp.ARBEIT, LocalDateTime.now().minusHours(1), max);

        assertThat(vorgang(0).istStunden()).isNull();
    }

    @Test
    void teilenSichZweiArtenEineAbteilungGiltDieZuordnungDesArbeitsgangs() {
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Fertigen", null, null, null, null, new BigDecimal("40")));
        planung.vorgangAnlegen(projektId, new VorgangEingabe(montageArt, "Montieren", null, null, null, null, new BigDecimal("16")));
        stempel(schweissen, "10", BuchungsTyp.ARBEIT, LocalDateTime.now().minusDays(2), max);
        stempel(montageGang, "6", BuchungsTyp.ARBEIT, LocalDateTime.now().minusDays(2), max);

        assertThat(ist.zuordnungen()).filteredOn(z -> z.arbeitsgangId().equals(schweissen)).singleElement().satisfies(z -> {
            assertThat(z.mehrdeutig()).isTrue();
            assertThat(z.typId()).isNull();
        });
        assertThat(vorgang(0).istStunden()).as("ohne Zuordnung zählt alles für die erste Art der Abteilung").isEqualByComparingTo("16");
        assertThat(vorgang(1).istStunden()).isNull();

        ist.zuordnen(montageGang, montageArt);
        assertThat(vorgang(0).istStunden()).isEqualByComparingTo("10");
        assertThat(vorgang(1).istStunden()).isEqualByComparingTo("6");

        ist.zuordnen(montageGang, null);
        assertThat(vorgang(1).istStunden()).as("Zuordnung aufgehoben: wieder automatisch").isNull();
    }

    @Test
    void mehrereVorgaengeDerselbenArtWerdenNacheinanderGefuellt() {
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Teil 1", null, null, null, null, new BigDecimal("10")));
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Teil 2", null, null, null, null, new BigDecimal("10")));
        stempel(schweissen, "14", BuchungsTyp.ARBEIT, LocalDateTime.now().minusDays(2), max);

        assertThat(vorgang(0).istStunden()).isEqualByComparingTo("10");
        assertThat(vorgang(1).istStunden()).isEqualByComparingTo("4");
        stempel(schweissen, "20", BuchungsTyp.ARBEIT, LocalDateTime.now().minusDays(1), max);
        planung.neuBerechnenAlle();
        assertThat(vorgang(1).istStunden()).as("was in keinen Vorgang mehr passt, steht beim letzten").isEqualByComparingTo("24");
        assertThat(vorgang(1).dauerTage()).as("nichts mehr offen: ein Tag Meilenstein-Dauer, mindestens 1").isEqualTo(1);
    }

    @Test
    void heuteGestempeltesVerbrauchtDieKapazitaetVonHeute() {
        planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigungArt, "Geländer fertigen", null, null, null, null, new BigDecimal("16")));
        zuweisung.zuweisen(vorgang(0).id(), new PlanungsZuweisung.Zuweisung(List.of(max), null, null));
        LocalDate heute = LocalDate.now();
        boolean arbeitstag = heute.getDayOfWeek().getValue() <= 5;
        if (!arbeitstag) {
            return; // am Wochenende gibt es keine Kapazität von heute, die sich verbrauchen ließe
        }
        stempel(schweissen, "5", BuchungsTyp.ARBEIT, heute.atTime(7, 0), max);
        planung.neuBerechnenAlle();

        // 16 h Aufwand, 5 h davon heute schon gestempelt (zählen als Fortschritt): 11 h offen; heute bleiben 3 h (8 − 5)
        var v = vorgang(0);
        assertThat(v.istStunden()).isEqualByComparingTo("5");
        assertThat(v.start()).isEqualTo(heute);
        assertThat(v.dauerTage()).as("heute 3 h, morgen 8 h = zwei Arbeitstage (bei freiem Folgetag mehr)").isGreaterThanOrEqualTo(2);
    }

    @Test
    void zuordnungWirdGeprueft() {
        assertThatThrownBy(() -> ist.zuordnen(999999L, fertigungArt)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Arbeitsgang");
        assertThatThrownBy(() -> ist.zuordnen(schweissen, 999999L)).hasMessageContaining("Vorgangsart");
        Long material = planung.typSpeichern(null, "Material", "#d97706", 20, 3, true, null, null, true, false).id();
        assertThatThrownBy(() -> ist.zuordnen(schweissen, material)).hasMessageContaining("Wartevorgang");
    }
}
