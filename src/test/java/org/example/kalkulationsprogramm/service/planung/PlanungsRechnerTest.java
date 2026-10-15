package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Kante;
import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Knoten;
import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Zeitraum;
import org.junit.jupiter.api.Test;

/** Die Terminrechnung der Plantafel: Arbeitstage, Reihenfolge, Puffer, feste Termine, Fehler. Nur Dummy-Daten. */
class PlanungsRechnerTest {

    // Montag, 5. Oktober 2026
    private static final LocalDate MO = LocalDate.of(2026, 10, 5);
    private static final Predicate<LocalDate> ARBEITSTAG = PlanungsRechner.arbeitstag(Set.of());

    private static Knoten k(long id, int tage, Kante... vorgaenger) {
        return new Knoten(id, tage, null, List.of(vorgaenger));
    }

    private static Kante nach(long id) {
        return new Kante(id, 0);
    }

    @Test
    void eineKetteLaeuftHintereinanderUndSpringtUeberDasWochenende() {
        // Planung 2 Tage (Mo-Di), Material 3 Tage (Mi-Fr), Fertigung 2 Tage (Mo-Di)
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 2), k(2, 3, nach(1)), k(3, 2, nach(2))), MO, ARBEITSTAG);

        assertThat(z.get(1L)).extracting(Zeitraum::start, Zeitraum::ende).containsExactly(MO, MO.plusDays(1));
        assertThat(z.get(2L)).extracting(Zeitraum::start, Zeitraum::ende).containsExactly(MO.plusDays(2), MO.plusDays(4));
        assertThat(z.get(3L)).extracting(Zeitraum::start, Zeitraum::ende).containsExactly(MO.plusDays(7), MO.plusDays(8));
    }

    @Test
    void einBeginnAmWochenendeRutschtAufDenMontag() {
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 1)), LocalDate.of(2026, 10, 10), ARBEITSTAG);
        assertThat(z.get(1L).start()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void feiertageZaehlenNichtAlsArbeitstag() {
        // 3. Oktober 2026 ist ein Samstag; wir setzen den Freitag, 9. Oktober, als Feiertag
        Predicate<LocalDate> mitFeiertag = PlanungsRechner.arbeitstag(Set.of(LocalDate.of(2026, 10, 9)));
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 4)), MO, mitFeiertag);
        // Mo, Di, Mi, Do = 4 Arbeitstage
        assertThat(z.get(1L).ende()).isEqualTo(MO.plusDays(3));
        Map<Long, Zeitraum> danach = PlanungsRechner.berechne(List.of(k(1, 4), k(2, 1, nach(1))), MO, mitFeiertag);
        assertThat(danach.get(2L).start()).as("Fr ist Feiertag, Sa/So frei").isEqualTo(MO.plusDays(7));
    }

    @Test
    void einVorgangWartetAufDenSpaetestenSeinerVorgaenger() {
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 1), k(2, 4), k(3, 1, nach(1), nach(2))), MO, ARBEITSTAG);
        assertThat(z.get(3L).start()).isEqualTo(MO.plusDays(4));
    }

    @Test
    void einPufferVerschiebtDenNachfolgerUmArbeitstage() {
        // Veredelung endet Mo, danach 2 Tage Transport/Trocknung -> Montage beginnt Donnerstag
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 1), new Knoten(2L, 1, null, List.of(new Kante(1L, 2)))), MO, ARBEITSTAG);
        assertThat(z.get(2L).start()).isEqualTo(MO.plusDays(3));
    }

    @Test
    void einMeilensteinHatKeineDauer() {
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 2), k(2, 0, nach(1))), MO, ARBEITSTAG);
        assertThat(z.get(2L).start()).isEqualTo(z.get(2L).ende()).isEqualTo(MO.plusDays(2));
    }

    @Test
    void einFesterStartWirdNichtVerschobenKommtAberEinKonfliktWennDieVorgaengerZuSpaetSind() {
        Knoten fest = new Knoten(2L, 1, MO.plusDays(1), List.of(nach(1)));
        Map<Long, Zeitraum> z = PlanungsRechner.berechne(List.of(k(1, 3), fest), MO, ARBEITSTAG);

        assertThat(z.get(2L).start()).isEqualTo(MO.plusDays(1));
        assertThat(z.get(2L).fixiert()).isTrue();
        assertThat(z.get(2L).konflikt()).as("Vorgänger sind erst am Mittwoch fertig").isTrue();
    }

    @Test
    void einFesterStartInDerZukunftHaeltDenVorgangDortOhneKonflikt() {
        Knoten fest = new Knoten(2L, 1, MO.plusDays(14), List.of(nach(1)));
        Zeitraum z = PlanungsRechner.berechne(List.of(k(1, 1), fest), MO, ARBEITSTAG).get(2L);
        assertThat(z.start()).isEqualTo(MO.plusDays(14));
        assertThat(z.konflikt()).isFalse();
    }

    @Test
    void einKreisWirdMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> PlanungsRechner.berechne(List.of(k(1, 1, nach(2)), k(2, 1, nach(1))), MO, ARBEITSTAG))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("im Kreis");
    }

    @Test
    void einUnbekannterVorgaengerUndNegativeWerteWerdenAbgelehnt() {
        assertThatThrownBy(() -> PlanungsRechner.berechne(List.of(k(1, 1, nach(9))), MO, ARBEITSTAG)).hasMessageContaining("Vorgänger");
        assertThatThrownBy(() -> PlanungsRechner.berechne(List.of(k(1, -1)), MO, ARBEITSTAG)).hasMessageContaining("Dauer");
        assertThatThrownBy(() -> PlanungsRechner.berechne(List.of(k(1, 1), new Knoten(2L, 1, null, List.of(new Kante(1L, -1)))), MO, ARBEITSTAG)).hasMessageContaining("Puffer");
    }

    @Test
    void ohneVorgaengeKommtEineLeereListe() {
        assertThat(PlanungsRechner.berechne(List.of(), MO, ARBEITSTAG)).isEmpty();
    }
}
