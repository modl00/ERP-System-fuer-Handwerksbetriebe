package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.junit.jupiter.api.Test;

/** Mehrtägige Kalendertermine: Wie Tage, Uhrzeit und ganztägig aus der Anfrage übernommen werden. */
class KalenderMehrtaegigTest {

    private static KalenderController.KalenderEintragRequest anfrage(LocalDate bis, LocalTime von, boolean ganztaegig) {
        return new KalenderController.KalenderEintragRequest("Messe", null, LocalDate.of(2026, 10, 12), bis, von, von == null ? null : von.plusHours(1), ganztaegig,
                null, null, null, null, null, null, null);
    }

    private static KalenderEintrag eintrag() {
        KalenderEintrag k = new KalenderEintrag();
        k.setDatum(LocalDate.of(2026, 10, 12));
        return k;
    }

    @Test
    void einMehrtaegigerTerminIstGanztaegigUndHatKeineUhrzeit() {
        KalenderEintrag k = eintrag();

        KalenderController.uebernehmeTage(k, anfrage(LocalDate.of(2026, 10, 15), LocalTime.of(9, 0), false));

        assertThat(k.getDatumBis()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(k.isGanztaegig()).isTrue();
        assertThat(k.getStartZeit()).isNull();
        assertThat(k.getEndeZeit()).isNull();
    }

    @Test
    void einLetzterTagAmStarttagOderDavorMachtEinenEintaegigenTermin() {
        KalenderEintrag k = eintrag();

        KalenderController.uebernehmeTage(k, anfrage(LocalDate.of(2026, 10, 12), LocalTime.of(9, 0), false));

        assertThat(k.getDatumBis()).isNull();
        assertThat(k.isGanztaegig()).isFalse();
        assertThat(k.getStartZeit()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void ohneAngabeBleibtDerLetzteTagErhalten() {
        KalenderEintrag k = eintrag();
        k.setDatumBis(LocalDate.of(2026, 10, 14));

        KalenderController.uebernehmeTage(k, anfrage(null, null, true));

        assertThat(k.getDatumBis()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void wirdDerStarttagHinterDenLetztenTagVerlegtIstDerTerminEintaegig() {
        KalenderEintrag k = eintrag();
        k.setDatumBis(LocalDate.of(2026, 10, 10));

        KalenderController.uebernehmeTage(k, anfrage(null, LocalTime.of(8, 0), false));

        assertThat(k.getDatumBis()).isNull();
        assertThat(k.getStartZeit()).isEqualTo(LocalTime.of(8, 0));
    }
}
