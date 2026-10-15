package org.example.kalkulationsprogramm.service.formel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld;
import org.junit.jupiter.api.Test;

/** Zuschnitt-Formeln am Artikel (#228), als Handrechnung. Nur Dummy-Werte. */
class ArtikelFormelnTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static List<Feld> blechFelder() {
        return List.of(new Feld("L", "Länge", "mm", d("1000")), new Feld("B", "Breite", "mm", d("500")), new Feld("T", "Dicke", "mm", d("10")));
    }

    private static ArtikelFormeln.Formel blech() {
        return new ArtikelFormeln.Formel(blechFelder(), "(L/1000) * (B/1000) * T * dichte", "(L/1000) * (B/1000) * 2");
    }

    @Test
    void blechzuschnittRechnetGewichtUndFlaecheAusLaengeBreiteDicke() {
        // 1,0 m × 0,5 m × 10 mm × 7,85 kg/dm³ = 39,25 kg; beide Seiten 0,5 m² × 2 = 1 m²
        ArtikelFormeln.Ergebnis e = ArtikelFormeln.berechne(blech(), Map.of("l", d("1000"), "b", d("500"), "t", d("10")), ArtikelFormeln.eingebaut(null, null, null));
        assertThat(e.kg()).isEqualByComparingTo("39.25");
        assertThat(e.m2()).isEqualByComparingTo("1");
    }

    @Test
    void fehlendeWerteNehmenDenStandardwertUndEinWerkstoffMitAndererDichteAendertDasGewicht() {
        ArtikelFormeln.Ergebnis stahl = ArtikelFormeln.berechne(blech(), Map.of(), ArtikelFormeln.eingebaut(null, null, null));
        assertThat(stahl.kg()).isEqualByComparingTo("39.25");
        ArtikelFormeln.Ergebnis alu = ArtikelFormeln.berechne(blech(), Map.of(), ArtikelFormeln.eingebaut(d("2.7"), null, null));
        assertThat(alu.kg()).isEqualByComparingTo("13.5");
    }

    @Test
    void einAbschnittVonMeterwareNutztGewichtUndFlaecheJeMeterAusDenStammdaten() {
        ArtikelFormeln.Formel f = new ArtikelFormeln.Formel(List.of(new Feld("L", "Länge", "mm", d("500"))), "(L/1000) * kg_je_meter", "(L/1000) * m2_je_meter");
        ArtikelFormeln.Ergebnis e = ArtikelFormeln.berechne(f, Map.of("l", d("1500")), ArtikelFormeln.eingebaut(null, d("2.4"), d("0.16")));
        assertThat(e.kg()).isEqualByComparingTo("3.6");
        assertThat(e.m2()).isEqualByComparingTo("0.24");
    }

    @Test
    void fehlendeStammdatenWerdenInKlartextBenannt() {
        ArtikelFormeln.Formel f = new ArtikelFormeln.Formel(List.of(new Feld("L", "Länge", "mm", d("500"))), "(L/1000) * kg_je_meter", null);
        assertThatThrownBy(() -> ArtikelFormeln.berechne(f, Map.of(), ArtikelFormeln.eingebaut(null, null, null)))
                .isInstanceOf(FormelException.class).hasMessageContaining("Gewicht bzw. die Außenfläche je Meter");
    }

    @Test
    void nurEineGewichtsformelLaesstDieFlaecheUnbekannt() {
        ArtikelFormeln.Formel f = new ArtikelFormeln.Formel(blechFelder(), "L * B * T / 1000000 * dichte", null);
        ArtikelFormeln.Ergebnis e = ArtikelFormeln.berechne(f, Map.of(), ArtikelFormeln.eingebaut(null, null, null));
        assertThat(e.kg()).isNotNull();
        assertThat(e.m2()).isNull();
    }

    @Test
    void einFeldOhneWertUndOhneStandardWirdVerlangt() {
        ArtikelFormeln.Formel f = new ArtikelFormeln.Formel(List.of(new Feld("L", "Länge", "mm", null)), "L / 1000", null);
        assertThatThrownBy(() -> ArtikelFormeln.berechne(f, Map.of(), Map.of())).isInstanceOf(FormelException.class).hasMessageContaining("Länge");
    }

    @Test
    void negativeUndUnsinnigGrosseErgebnisseWerdenAbgelehnt() {
        ArtikelFormeln.Formel f = new ArtikelFormeln.Formel(List.of(new Feld("L", "Länge", "mm", null)), "L", null);
        assertThatThrownBy(() -> ArtikelFormeln.berechne(f, Map.of("l", d("-1")), Map.of())).hasMessageContaining("negativ");
        assertThatThrownBy(() -> ArtikelFormeln.berechne(f, Map.of("l", d("999999999")), Map.of())).hasMessageContaining("unrealistisch");
    }

    @Test
    void pruefenBereinigtUndErkenntEingebauteNamen() {
        ArtikelFormeln.Formel ok = ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(blechFelder(), "  L * B * T / 1000000 * dichte ", "  "));
        assertThat(ok.gewicht()).isEqualTo("L * B * T / 1000000 * dichte");
        assertThat(ok.flaeche()).isNull();
        assertThat(ok.felder()).hasSize(3);
    }

    @Test
    void ohneBeideFormelnGiltDerArtikelAlsOhneFormel() {
        ArtikelFormeln.Formel leer = ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(blechFelder(), "", null));
        assertThat(leer.felder()).isEmpty();
        assertThat(leer.gewicht()).isNull();
    }

    @Test
    void eingebauteNamenSindAlsEigenesFeldGesperrt() {
        for (String name : new String[] {"dichte", "kg_je_meter", "m2_je_meter", "DICHTE", "pi", "min"}) {
            assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(List.of(new Feld(name, "x", "", null)), "1", null)))
                    .as(name).isInstanceOf(FormelException.class).hasMessageContaining("reserviert");
        }
    }

    @Test
    void eineUnbekannteVariableInDerFormelWirdAbgelehnt() {
        assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(blechFelder(), "L * Q", null))).isInstanceOf(FormelException.class);
    }

    @Test
    void einschleusversucheWerdenAlsFormelfehlerAbgelehnt() {
        for (String boese : new String[] {"Runtime.getRuntime().exec('x')", "'; DROP TABLE artikel; --", "<script>alert(1)</script>", "L; 1", "${jndi:ldap://x}", "T.getClass()"}) {
            assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(blechFelder(), boese, null))).as(boese).isInstanceOf(FormelException.class);
        }
        assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(blechFelder(), "1 + ".repeat(300) + "1", null))).isInstanceOf(FormelException.class);
    }

    @Test
    void ueberlangeFeldnamenUndZuvieleFelderWerdenAbgelehnt() {
        assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(List.of(new Feld("x".repeat(21), "x", "", null)), "1", null))).isInstanceOf(FormelException.class);
        List<Feld> neun = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            neun.add(new Feld("V" + i, "V" + i, "", null));
        }
        assertThatThrownBy(() -> ArtikelFormeln.pruefe(new ArtikelFormeln.Formel(neun, "1", null))).hasMessageContaining("höchstens");
    }
}
