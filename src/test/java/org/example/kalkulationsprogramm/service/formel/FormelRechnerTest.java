package org.example.kalkulationsprogramm.service.formel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Der Formelrechner der Kalkulation: richtig rechnen, nie Code ausführen, Fehler im Klartext. */
class FormelRechnerTest {

    private static BigDecimal r(String formel, Object... paare) {
        Map<String, BigDecimal> w = new java.util.HashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            w.put((String) paare[i], new BigDecimal(paare[i + 1].toString()));
        }
        return FormelRechner.rechne(formel, w);
    }

    @Test
    void grundrechenartenKlammernUndPunktVorStrich() {
        assertThat(r("2+3*4")).isEqualByComparingTo("14");
        assertThat(r("(2+3)*4")).isEqualByComparingTo("20");
        assertThat(r("10/4")).isEqualByComparingTo("2.5");
        assertThat(r("10-2-3")).isEqualByComparingTo("5");
        assertThat(r("-3+5")).isEqualByComparingTo("2");
        assertThat(r("2*-3")).isEqualByComparingTo("-6");
        assertThat(r("2^3")).isEqualByComparingTo("8");
        assertThat(r("2^-2")).isEqualByComparingTo("0.25");
    }

    @Test
    void dezimalkommaUndPunktGehenBeide() {
        assertThat(r("1,5*2")).isEqualByComparingTo("3");
        assertThat(r("1.5*2")).isEqualByComparingTo("3");
    }

    @Test
    void dieFormelAusDemDrehenBeispielStimmt() {
        // Längsdrehen: Durchmesser 60, Länge 200, 3 Schritte, vc 200, f 0,2, Umspannzeit 1
        BigDecimal zeit = r("(V2*V3)/(V5*(V4/(3,14*V1/1000)))+V6", "v1", 60, "v2", 200, "v3", 3, "v4", 200, "v5", "0.2", "v6", 1);
        assertThat(zeit).isBetween(new BigDecimal("3.82"), new BigDecimal("3.83"));
    }

    @Test
    void einGroessererBohrerdurchmesserBrauchtLaengerBeiGleicherSchnittgeschwindigkeit() {
        String formel = "Tiefe/(Vorschub*(Vc*1000/(pi*D)))+Ruest";
        BigDecimal acht = r(formel, "tiefe", 20, "vorschub", "0.15", "vc", 30, "d", 8, "ruest", 1);
        BigDecimal fuenfunddreissig = r(formel, "tiefe", 20, "vorschub", "0.15", "vc", 30, "d", 35, "ruest", 1);
        assertThat(fuenfunddreissig).isGreaterThan(acht);
    }

    @Test
    void feldnamenSindUnabhaengigVonDerSchreibweise() {
        assertThat(r("V1*Durchmesser", "v1", 2, "durchmesser", 5)).isEqualByComparingTo("10");
        assertThat(r("v1*DURCHMESSER", "V1", 2, "Durchmesser", 5)).isEqualByComparingTo("10");
    }

    @Test
    void funktionenMitSemikolon() {
        assertThat(r("min(3;5)")).isEqualByComparingTo("3");
        assertThat(r("max(3,5;5,5)")).isEqualByComparingTo("5.5");
        assertThat(r("runden(2,567;1)")).isEqualByComparingTo("2.6");
        assertThat(r("wenn(V1>10;100;50)", "v1", 11)).isEqualByComparingTo("100");
        assertThat(r("wenn(V1>10;100;50)", "v1", 10)).isEqualByComparingTo("50");
        assertThat(r("wenn(V1<=10;1;2)", "v1", 10)).isEqualByComparingTo("1");
        assertThat(r("wenn(V1<>10;1;2)", "v1", 10)).isEqualByComparingTo("2");
        assertThat(r("pi")).isBetween(new BigDecimal("3.14159"), new BigDecimal("3.1416"));
    }

    @Test
    void fehlerSindVerstaendlich() {
        assertThatThrownBy(() -> r("V1/V2", "v1", 5, "v2", 0)).isInstanceOf(FormelException.class).hasMessageContaining("Division durch null");
        assertThatThrownBy(() -> r("V9*2", "v1", 1)).hasMessageContaining("„V9“ gibt es nicht");
        assertThatThrownBy(() -> r("(2+3")).hasMessageContaining("schließende Klammer");
        assertThatThrownBy(() -> r("2+")).hasMessageContaining("nicht zu Ende");
        assertThatThrownBy(() -> r("2 $ 3")).hasMessageContaining("Hier stimmt etwas nicht");
        assertThatThrownBy(() -> r("")).hasMessageContaining("leer");
        assertThatThrownBy(() -> r("sinus(2)")).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> r("min(1)")).hasMessageContaining("genau 2");
        assertThatThrownBy(() -> r("2^0,5")).hasMessageContaining("ganze Zahl");
        assertThatThrownBy(() -> r("2^11")).hasMessageContaining("ganze Zahl");
    }

    @Test
    void nichtsWirdAlsCodeAusgefuehrtUndAbsurdesWirdAbgefangen() {
        for (String boese : new String[] { "Runtime.getRuntime().exec(1)", "System.exit(0)", "1;DROP TABLE x", "${7*7}", "#{1+1}", "new java.io.File(1)", "T(java.lang.Runtime)" }) {
            assertThatThrownBy(() -> r(boese)).as(boese).isInstanceOf(FormelException.class);
        }
        assertThatThrownBy(() -> r("9".repeat(600))).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> r("((((((((((((((1))))))))))))))")).hasMessageContaining("Klammern");
        assertThatThrownBy(() -> r("999999999999*999999999999")).hasMessageContaining("unrealistisch");
    }

    @Test
    void pruefenFindetSchreibfehlerUndUnbekannteFelderOhneZuRechnen() {
        assertThat(FormelRechner.pruefe("(V2*V3)/(V5*V4)+V6", Set.of("V2", "V3", "V4", "V5", "V6"))).containsExactlyInAnyOrder("v2", "v3", "v4", "v5", "v6");
        // Division durch einen Wert, der erst bei der Eingabe feststeht, ist beim Prüfen kein Fehler
        assertThat(FormelRechner.pruefe("1/(V1-V1)", Set.of("V1"))).containsExactly("v1");
        assertThatThrownBy(() -> FormelRechner.pruefe("V2*V7", Set.of("V2"))).hasMessageContaining("„V7“ gibt es nicht");
        assertThatThrownBy(() -> FormelRechner.pruefe("V2*", Set.of("V2"))).hasMessageContaining("nicht zu Ende");
    }

    @Test
    void fehlendenWertMeldetDasFeld() {
        Map<String, BigDecimal> w = new java.util.HashMap<>();
        w.put("v1", null);
        assertThatThrownBy(() -> FormelRechner.rechne("V1*2", w)).hasMessageContaining("fehlt ein Wert");
    }
}
