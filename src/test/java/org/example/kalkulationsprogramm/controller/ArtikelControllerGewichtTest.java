package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.junit.jupiter.api.Test;

/** Wann gilt ein berechnetes Gewicht als veraltet (#228)? Nur Dummy-Werte. */
class ArtikelControllerGewichtTest {

    private static ArtikelWerkstoffe artikel(String herkunft, String dichte, String werkstoffDichte) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setGewichtHerkunft(herkunft);
        a.setGewichtDichte(dichte == null ? null : new BigDecimal(dichte));
        if (werkstoffDichte != null) {
            Werkstoff w = new Werkstoff();
            w.setDichte(new BigDecimal(werkstoffDichte));
            a.setWerkstoff(w);
        }
        return a;
    }

    @Test
    void geaenderteDichteDesWerkstoffsMachtDasGewichtVeraltet() {
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", "7.85", "7.9"))).isTrue();
    }

    @Test
    void gleicheDichteIstNichtVeraltet() {
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", "7.85", "7.85"))).isFalse();
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", "7.85", "7.8500"))).isFalse();
    }

    @Test
    void ohneWerkstoffGiltStahl() {
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", "7.85", null))).isFalse();
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", "7.9", null))).isTrue();
    }

    @Test
    void nurBerechnetesGewichtKannVeralten() {
        assertThat(ArtikelController.gewichtVeraltet(artikel("HAND", "7.85", "7.9"))).isFalse();
        assertThat(ArtikelController.gewichtVeraltet(artikel("TABELLE", null, "7.9"))).isFalse();
        assertThat(ArtikelController.gewichtVeraltet(artikel(null, null, "7.9"))).isFalse();
        assertThat(ArtikelController.gewichtVeraltet(artikel("BERECHNET", null, "7.9"))).isFalse();
    }
}
