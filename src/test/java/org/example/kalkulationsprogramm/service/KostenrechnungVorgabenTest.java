package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/** Gespeicherte Vorgaben der Kostenrechnung und die Schätzung der internen Zeit (#308). Nur Dummy-Daten. */
@DataJpaTest
@Import(KostenrechnungVorgaben.class)
class KostenrechnungVorgabenTest {

    @Autowired
    private KostenrechnungVorgaben vorgaben;
    @MockBean
    private VerrechnungslohnService verrechnungslohn;

    private static MitarbeiterStundenZeile zeile(String soll, String intern, String verkaeuflich) {
        MitarbeiterStundenZeile z = new MitarbeiterStundenZeile();
        z.setSollstunden(new BigDecimal(soll));
        z.setInterneStunden(new BigDecimal(intern));
        z.setVerkaeuflicheStunden(new BigDecimal(verkaeuflich));
        return z;
    }

    private void jahrMit(int jahr, MitarbeiterStundenZeile... zeilen) {
        VerrechnungslohnErgebnisDto e = new VerrechnungslohnErgebnisDto();
        e.setStundenzeilen(List.of(zeilen));
        when(verrechnungslohn.berechne(jahr, null)).thenReturn(e);
    }

    @Test
    void ohneSpeichernIstNichtsVorgegeben() {
        var v = vorgaben.lese();
        assertThat(v.wagnis()).isNull();
        assertThat(v.gewinn()).isNull();
        assertThat(v.interneZeit()).isNull();
    }

    @Test
    void gespeichertesWirdGelesenUndEinLeeresFeldLoeschtDieVorgabe() {
        vorgaben.speichere(new BigDecimal("2.5"), new BigDecimal("12"), 6);
        var v = vorgaben.lese();
        assertThat(v.wagnis()).isEqualByComparingTo("2.5");
        assertThat(v.gewinn()).isEqualByComparingTo("12");
        assertThat(v.interneZeit()).isEqualTo(6);

        var danach = vorgaben.speichere(new BigDecimal("2.5"), null, null);
        assertThat(danach.gewinn()).isNull();
        assertThat(danach.interneZeit()).isNull();
        assertThat(danach.wagnis()).isEqualByComparingTo("2.5");
    }

    @Test
    void unsinnigeWerteWerdenAbgelehntUndNichtGespeichert() {
        assertThatThrownBy(() -> vorgaben.speichere(new BigDecimal("101"), null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wagnis");
        assertThatThrownBy(() -> vorgaben.speichere(null, new BigDecimal("-1"), null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Gewinn");
        assertThatThrownBy(() -> vorgaben.speichere(null, null, 101)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("interne Zeit");
        assertThat(vorgaben.lese().wagnis()).isNull();
    }

    @Test
    void dieInternenStundenGebenImVerhaeltnisZumSollDieSchaetzung() {
        int jahr = Year.now().getValue() - 1;
        jahrMit(jahr, zeile("1800", "90", "1400"), zeile("1800", "180", "1300"));

        var s = vorgaben.schaetze(jahr);

        assertThat(s.prozent()).as("270 von 3600 Sollstunden").isEqualTo(8);
        assertThat(s.interneStunden()).isEqualByComparingTo("270");
        assertThat(s.sollstunden()).isEqualByComparingTo("3600");
        assertThat(s.hinweis()).isNull();
    }

    @Test
    void fuerDasLaufendeJahrUndOhneBuchungenGibtEsKeineSchaetzung() {
        int jahr = Year.now().getValue();
        assertThat(vorgaben.schaetze(jahr).prozent()).isNull();
        assertThat(vorgaben.schaetze(jahr).hinweis()).contains("abgeschlossenes Jahr");

        int vorjahr = jahr - 1;
        jahrMit(vorjahr, zeile("1800", "0", "0"));
        var s = vorgaben.schaetze(vorjahr);
        assertThat(s.prozent()).isNull();
        assertThat(s.hinweis()).contains("keine Zeitbuchungen");
    }

    @Test
    void sindNurProduktiveStundenGebuchtWirdNullProzentMitWarnungGeschaetzt() {
        int jahr = Year.now().getValue() - 1;
        jahrMit(jahr, zeile("1800", "0", "1500"));

        var s = vorgaben.schaetze(jahr);

        assertThat(s.prozent()).isZero();
        assertThat(s.hinweis()).contains("keine internen Stunden gebucht");
    }
}
