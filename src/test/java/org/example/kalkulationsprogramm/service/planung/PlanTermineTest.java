package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Kalendertermin in der Plantafel verschieben (#323): nur Tag, Zeit und Teilnehmer ändern sich. Nur Dummy-Daten. */
@DataJpaTest
@Import(PlanTermine.class)
class PlanTermineTest {

    @Autowired
    private PlanTermine termine;
    @Autowired
    private TestEntityManager em;

    private Long max;
    private Long erika;
    private Long termin;

    @BeforeEach
    void setUp() {
        max = mitarbeiter("Max", "Mustermann", true);
        erika = mitarbeiter("Erika", "Musterfrau", true);
        KalenderEintrag k = new KalenderEintrag();
        k.setTitel("Aufmaß Familie Muster");
        k.setBeschreibung("Bitte Maßband mitnehmen");
        k.setDatum(LocalDate.of(2026, 10, 12));
        k.setStartZeit(LocalTime.of(9, 0));
        k.setEndeZeit(LocalTime.of(11, 0));
        k.getTeilnehmer().add(em.find(Mitarbeiter.class, max));
        termin = em.persistAndFlush(k).getId();
    }

    private Long mitarbeiter(String vor, String nach, boolean aktiv) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vor);
        m.setNachname(nach);
        m.setAktiv(aktiv);
        return em.persistAndFlush(m).getId();
    }

    private KalenderEintrag neu() {
        em.flush();
        em.clear();
        return em.find(KalenderEintrag.class, termin);
    }

    @Test
    void ändertTagUndUhrzeitUndLässtDenRestUnberührt() {
        termine.verschiebe(termin, LocalDate.of(2026, 10, 14), LocalTime.of(13, 30), LocalTime.of(15, 30), null, null);

        KalenderEintrag k = neu();
        assertThat(k.getDatum()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(k.getStartZeit()).isEqualTo(LocalTime.of(13, 30));
        assertThat(k.getEndeZeit()).isEqualTo(LocalTime.of(15, 30));
        assertThat(k.getTitel()).isEqualTo("Aufmaß Familie Muster");
        assertThat(k.getBeschreibung()).isEqualTo("Bitte Maßband mitnehmen");
        assertThat(k.getTeilnehmer()).extracting(Mitarbeiter::getId).containsExactly(max);
    }

    @Test
    void einGanztägigerTerminÄndertNurDenTag() {
        KalenderEintrag k = em.find(KalenderEintrag.class, termin);
        k.setGanztaegig(true);
        k.setStartZeit(null);
        k.setEndeZeit(null);
        em.persistAndFlush(k);

        termine.verschiebe(termin, LocalDate.of(2026, 10, 15), LocalTime.of(8, 0), LocalTime.of(9, 0), null, null);

        KalenderEintrag neu = neu();
        assertThat(neu.getDatum()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(neu.getStartZeit()).isNull();
    }

    @Test
    void einMehrtaegigerTerminBehaeltBeimVerschiebenSeineLaenge() {
        KalenderEintrag k = em.find(KalenderEintrag.class, termin);
        k.setGanztaegig(true);
        k.setStartZeit(null);
        k.setEndeZeit(null);
        k.setDatumBis(LocalDate.of(2026, 10, 14));
        em.persistAndFlush(k);

        termine.verschiebe(termin, LocalDate.of(2026, 10, 19), null, null, null, null);

        KalenderEintrag neu = neu();
        assertThat(neu.getDatum()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(neu.getDatumBis()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void beimWechselInDieZeileEinesAnderenMitarbeitersWirdDerTeilnehmerErsetzt() {
        termine.verschiebe(termin, LocalDate.of(2026, 10, 12), LocalTime.of(9, 0), LocalTime.of(11, 0), max, erika);

        assertThat(neu().getTeilnehmer()).extracting(Mitarbeiter::getId).containsExactly(erika);
    }

    @Test
    void einAusgeblendeterMitarbeiterIstKeinZiel() {
        Long weg = mitarbeiter("Ehemals", "Beschäftigt", false);

        assertThatThrownBy(() -> termine.verschiebe(termin, LocalDate.of(2026, 10, 12), null, null, max, weg)).hasMessageContaining("nicht gefunden");
        assertThat(neu().getTeilnehmer()).extracting(Mitarbeiter::getId).containsExactly(max);
    }

    @Test
    void falscheEingabenKommenMitKlartext() {
        assertThatThrownBy(() -> termine.verschiebe(termin, null, null, null, null, null)).hasMessage("Bitte einen Tag angeben.");
        assertThatThrownBy(() -> termine.verschiebe(termin, LocalDate.of(2026, 10, 12), LocalTime.of(11, 0), LocalTime.of(10, 0), null, null)).hasMessage("Das Ende muss nach dem Beginn liegen.");
        assertThatThrownBy(() -> termine.verschiebe(999_999L, LocalDate.of(2026, 10, 12), null, null, null, null)).hasMessage("Der Termin wurde nicht gefunden.");
    }
}
