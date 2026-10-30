package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Urlaub;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Werte;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** Planungs-Einstellungen gegen die echte Datenbank-Abbildung (H2): Vorgaben, Speichern, Prüfung, Arbeitstag. Nur Dummy-Daten. */
@DataJpaTest
@Import(PlanungsEinstellungen.class)
class PlanungsEinstellungenTest {

    private static final Set<DayOfWeek> MO_FR = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    @Autowired
    private PlanungsEinstellungen service;

    @Test
    void ohneEintragGeltenDieBisherigenWerte() {
        Werte w = service.lesen();

        assertThat(w.arbeitstage()).containsExactlyInAnyOrderElementsOf(MO_FR);
        assertThat(w.ampelSchwelleTage()).isEqualTo(2);
        assertThat(w.betriebsurlaub()).isEmpty();
    }

    @Test
    void gespeichertesKommtWiederUndDerBetriebsurlaubIstNachDatumSortiert() {
        service.speichern(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), 4, List.of(
                new Urlaub(LocalDate.of(2026, 12, 24), LocalDate.of(2027, 1, 3), "  Weihnachten "),
                new Urlaub(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 14), "Sommer")));

        Werte w = service.lesen();

        assertThat(w.arbeitstage()).hasSize(6).contains(DayOfWeek.SATURDAY);
        assertThat(w.ampelSchwelleTage()).isEqualTo(4);
        assertThat(w.betriebsurlaub()).extracting(Urlaub::bezeichnung).containsExactly("Sommer", "Weihnachten");
        assertThat(w.betriebsurlaub().get(1).bis()).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    void einArbeitstagIstEinTagDerArbeitswocheOhneFeiertagUndOhneBetriebsurlaub() {
        Werte w = service.speichern(MO_FR, 2, List.of(new Urlaub(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), "Sommer")));
        Set<LocalDate> feiertage = Set.of(LocalDate.of(2026, 8, 17));

        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 12), feiertage)).as("Mittwoch").isTrue();
        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 8), feiertage)).as("Samstag").isFalse();
        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 5), feiertage)).as("im Betriebsurlaub").isFalse();
        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 7), feiertage)).as("letzter Urlaubstag zählt mit").isFalse();
        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 10), feiertage)).as("Tag nach dem Urlaub").isTrue();
        assertThat(w.istArbeitstag(LocalDate.of(2026, 8, 17), feiertage)).as("Feiertag").isFalse();
    }

    @Test
    void ungueltigesWirdMitKlartextAbgelehntUndNichtGespeichert() {
        LocalDate tag = LocalDate.of(2026, 8, 3);

        assertThatThrownBy(() -> service.speichern(Set.of(), 2, List.of())).hasMessageContaining("Mindestens ein Arbeitstag");
        assertThatThrownBy(() -> service.speichern(MO_FR, -1, List.of())).hasMessageContaining("zwischen 0 und");
        assertThatThrownBy(() -> service.speichern(MO_FR, 31, List.of())).hasMessageContaining("zwischen 0 und");
        assertThatThrownBy(() -> service.speichern(MO_FR, 2, List.of(new Urlaub(tag, tag.minusDays(1), "")))).hasMessageContaining("Ende vor dem Beginn");
        assertThatThrownBy(() -> service.speichern(MO_FR, 2, List.of(new Urlaub(tag, null, "")))).hasMessageContaining("fehlt ein Datum");
        assertThatThrownBy(() -> service.speichern(MO_FR, 2, List.of(new Urlaub(tag, tag.plusDays(400), "")))).hasMessageContaining("höchstens ein Jahr");

        assertThat(service.lesen().ampelSchwelleTage()).as("nichts gespeichert").isEqualTo(2);
    }
}
