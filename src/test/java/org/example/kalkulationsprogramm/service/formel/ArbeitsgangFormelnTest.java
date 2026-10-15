package org.example.kalkulationsprogramm.service.formel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Formel;
import org.junit.jupiter.api.Test;

/** Eingabefelder und Formeln eines Arbeitsgangs (#216). Nur Dummy-Daten. */
class ArbeitsgangFormelnTest {

    private static Feld f(String name, String standard) {
        return new Feld(name, "Feld " + name, "mm", standard == null ? null : new BigDecimal(standard));
    }

    private static Formel drehen() {
        return new Formel(List.of(f("V1", "60"), f("V2", "200"), f("V3", "3"), f("V4", "200"), f("V5", "0.2"), f("V6", "1")), "(V2*V3)/(V5*(V4/(3,14*V1/1000)))+V6", "30");
    }

    @Test
    void mitStandardwertenRechnetDieFormelOhneEingabe() {
        ArbeitsgangFormeln.Zeiten z = ArbeitsgangFormeln.berechne(ArbeitsgangFormeln.pruefe(drehen()), Map.of());
        assertThat(z.bearbeitungMinuten()).isEqualByComparingTo("3.83");
        assertThat(z.ruestMinuten()).isEqualByComparingTo("30.00");
    }

    @Test
    void eingegebeneWerteGehenVorDemStandardwert() {
        Formel f = ArbeitsgangFormeln.pruefe(drehen());
        BigDecimal dick = ArbeitsgangFormeln.berechne(f, Map.of("V1", new BigDecimal("120"))).bearbeitungMinuten();
        BigDecimal duenn = ArbeitsgangFormeln.berechne(f, Map.of("v1", new BigDecimal("30"))).bearbeitungMinuten();
        assertThat(dick).isGreaterThan(new BigDecimal("3.83"));   // größerer Durchmesser: niedrigere Drehzahl bei gleicher Schnittgeschwindigkeit, längere Zeit
        assertThat(duenn).isLessThan(new BigDecimal("3.83"));
    }

    @Test
    void ohneRuestFormelBleibtDieRuestzeitLeer() {
        Formel f = ArbeitsgangFormeln.pruefe(new Formel(List.of(f("L", "10")), "L*2", null));
        assertThat(ArbeitsgangFormeln.berechne(f, Map.of()).ruestMinuten()).isNull();
    }

    @Test
    void einFeldOhneWertUndOhneStandardWirdGemeldet() {
        Formel f = ArbeitsgangFormeln.pruefe(new Formel(List.of(f("L", null)), "L*2", null));
        assertThatThrownBy(() -> ArbeitsgangFormeln.berechne(f, Map.of())).hasMessageContaining("Bitte einen Wert für „Feld L“");
    }

    @Test
    void ungueltigeEingabenWerdenAbgelehnt() {
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("1x", "1")), "1", null))).hasMessageContaining("ungültig");
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("pi", "1")), "1", null))).hasMessageContaining("reserviert");
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1"), f("a", "2")), "A", null))).hasMessageContaining("doppelt");
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), "A*B", null))).hasMessageContaining("„B“ gibt es nicht");
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), "A", "A*"))).hasMessageContaining("nicht zu Ende");
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), null, "A"))).hasMessageContaining("Formel für die Bearbeitungszeit");
        List<Feld> neun = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            neun.add(f("V" + i, "1"));
        }
        assertThatThrownBy(() -> ArbeitsgangFormeln.pruefe(new Formel(neun, "V1", null))).hasMessageContaining("höchstens 8");
    }

    @Test
    void eineLeereFormelEntferntAlles() {
        Formel f = ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), "  ", ""));
        assertThat(f.felder()).isEmpty();
        assertThat(f.bearbeitung()).isNull();
    }

    @Test
    void negativeOderRiesigeZeitenWerdenAbgelehnt() {
        Formel negativ = ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), "A-10", null));
        assertThatThrownBy(() -> ArbeitsgangFormeln.berechne(negativ, Map.of())).hasMessageContaining("negativ");
        Formel riesig = ArbeitsgangFormeln.pruefe(new Formel(List.of(f("A", "1")), "A*1000000", null));
        assertThatThrownBy(() -> ArbeitsgangFormeln.berechne(riesig, Map.of())).hasMessageContaining("100.000 Minuten");
        assertThatThrownBy(() -> ArbeitsgangFormeln.berechne(negativ, Map.of("A", new BigDecimal("99999999999")))).hasMessageContaining("unrealistisch");
    }

    @Test
    void felderUndWerteUeberstehenDieSpeicherungAlsJson() {
        Formel f = ArbeitsgangFormeln.pruefe(drehen());
        Arbeitsgang g = new Arbeitsgang();
        g.setFormelFelderJson(ArbeitsgangFormeln.felderAlsJson(f.felder()));
        g.setFormelBearbeitung(f.bearbeitung());
        g.setFormelRuest(f.ruest());
        assertThat(ArbeitsgangFormeln.hat(g)).isTrue();
        Formel zurueck = ArbeitsgangFormeln.lese(g);
        assertThat(zurueck.felder()).hasSize(6);
        assertThat(zurueck.felder().get(4).standard()).isEqualByComparingTo("0.2");
        assertThat(ArbeitsgangFormeln.werteAusJson(ArbeitsgangFormeln.werteAlsJson(Map.of("v1", new BigDecimal("2.5"))))).containsEntry("v1", new BigDecimal("2.5"));
        assertThat(ArbeitsgangFormeln.hat(new Arbeitsgang())).isFalse();
        assertThat(ArbeitsgangFormeln.werteAusJson("kaputt{")).isNull();
    }
}
