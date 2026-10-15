package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.junit.jupiter.api.Test;

/** Schätzung der Gemeinkosten (#237): Welche Zahl gilt, Speichern mit Prüfung, Hinweis in den Kostensätzen. Nur Dummy-Daten. */
class GemeinkostenSchaetzungTest {

    private static final int HEUTE = 2026;

    @Test
    void ohneSchaetzungZaehlenNurDieEchtenKosten() {
        var w = GemeinkostenSchaetzung.waehle(HEUTE, new BigDecimal("1200"), null, HEUTE);
        assertThat(w.verwendet()).isEqualByComparingTo("1200");
        assertThat(w.schaetzungGilt()).isFalse();
    }

    @Test
    void frischeInstallationNimmtDieSchaetzungStattNull() {
        var w = GemeinkostenSchaetzung.waehle(HEUTE, BigDecimal.ZERO, new BigDecimal("36000"), HEUTE);
        assertThat(w.verwendet()).isEqualByComparingTo("36000");
        assertThat(w.ist()).isEqualByComparingTo("0");
        assertThat(w.schaetzungGilt()).isTrue();
    }

    @Test
    void sindDieEchtenKostenHoeherGeltenSie() {
        var w = GemeinkostenSchaetzung.waehle(HEUTE, new BigDecimal("40000"), new BigDecimal("36000"), HEUTE);
        assertThat(w.verwendet()).isEqualByComparingTo("40000");
        assertThat(w.schaetzungGilt()).isFalse();
    }

    @Test
    void einVergangenesJahrZaehltNurMitEchtenKosten() {
        var w = GemeinkostenSchaetzung.waehle(HEUTE - 1, BigDecimal.ZERO, new BigDecimal("36000"), HEUTE);
        assertThat(w.verwendet()).isEqualByComparingTo("0");
        assertThat(w.schaetzungGilt()).isFalse();
    }

    @Test
    void einSpaeteresJahrNimmtDieSchaetzung() {
        assertThat(GemeinkostenSchaetzung.waehle(HEUTE + 1, new BigDecimal("100"), new BigDecimal("36000"), HEUTE).schaetzungGilt()).isTrue();
    }

    @Test
    void einFehlenderEchterWertGiltAlsNull() {
        var w = GemeinkostenSchaetzung.waehle(HEUTE, null, new BigDecimal("500"), HEUTE);
        assertThat(w.verwendet()).isEqualByComparingTo("500");
    }

    @Test
    void textWirdGelesenMitKommaOderPunktUndUnsinnIgnoriert() {
        assertThat(GemeinkostenSchaetzung.parse("36000,50")).isEqualByComparingTo("36000.50");
        assertThat(GemeinkostenSchaetzung.parse(" 1200.5 ")).isEqualByComparingTo("1200.5");
        assertThat(GemeinkostenSchaetzung.parse("")).isNull();
        assertThat(GemeinkostenSchaetzung.parse(null)).isNull();
        assertThat(GemeinkostenSchaetzung.parse("viel")).isNull();
        assertThat(GemeinkostenSchaetzung.parse("-5")).isNull();
    }

    @Test
    void speichernPruehftBetragUndLoeschtMitLeer() {
        SystemSettingsService einstellungen = mock(SystemSettingsService.class);
        var schaetzung = new GemeinkostenSchaetzung(einstellungen);

        schaetzung.speichere(new BigDecimal("36000"));
        verify(einstellungen).save(eq(GemeinkostenSchaetzung.SCHLUESSEL), eq("36000.00"), anyString());
        schaetzung.speichere(null);
        verify(einstellungen).save(eq(GemeinkostenSchaetzung.SCHLUESSEL), eq(""), anyString());

        assertThatThrownBy(() -> schaetzung.speichere(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht negativ");
        assertThatThrownBy(() -> schaetzung.speichere(new BigDecimal("100000001"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zu hoch");
    }

    @Test
    void leseGibtDenGespeichertenWertZurueck() {
        SystemSettingsService einstellungen = mock(SystemSettingsService.class);
        when(einstellungen.get(GemeinkostenSchaetzung.SCHLUESSEL, "")).thenReturn("24000.00");
        assertThat(new GemeinkostenSchaetzung(einstellungen).lese()).isEqualByComparingTo("24000");
    }

    @Test
    void dieKostensaetzeNennenDenHinweis_wennDieSchaetzungGilt() {
        VerrechnungslohnErgebnisDto e = new VerrechnungslohnErgebnisDto();
        e.setJahr(HEUTE);
        e.setGemeinkostenGesamt(new BigDecimal("36000.00"));
        e.setGemeinkostenIst(new BigDecimal("1200.00"));
        e.setSchaetzungGilt(true);
        e.setEnthaeltSchaetzwerte(true);

        var r = KostensatzService.rechne(e, java.util.Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(r.hinweise()).anyMatch(h -> h.contains("Schätzwerte") && h.contains("36000") && h.contains("1200"));
    }

    @Test
    void ohneSchaetzungKommtKeinSchaetzungsHinweis() {
        VerrechnungslohnErgebnisDto e = new VerrechnungslohnErgebnisDto();
        e.setJahr(HEUTE);
        assertThat(KostensatzService.rechne(e, java.util.Map.of(), BigDecimal.ZERO, BigDecimal.ZERO).hinweise()).noneMatch(h -> h.contains("Schätzwerte"));
    }
}
