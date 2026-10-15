package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Aufstellung;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Eingabe;
import org.junit.jupiter.api.Test;

/** Der Kostensatz einer Maschine als Handrechnung: jede Zahl lässt sich im Kopf nachrechnen. */
class MaschinenKostenRechnerTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    /** Anschaffung 60.000, Rest 6.000, 9 Jahre, 5 % Zins, Wartung 1.500, Platz 750, Versicherung 300 je Jahr, 1.500 Laufstunden, 20 kW zu 50 % mit 0,30 €/kWh. */
    private static Eingabe schweissanlage() {
        return new Eingabe(d("60000"), d("6000"), d("9"), d("5"), d("1500"), d("750"), d("300"), d("20"), d("0.30"), d("50"), d("1500"));
    }

    @Test
    void handrechnungMitAllenKostenarten() {
        Aufstellung a = MaschinenKostenRechner.rechne(schweissanlage());

        // (60.000 - 6.000) / 9 Jahre / 1.500 Stunden = 4,00
        assertThat(a.abschreibung()).isEqualByComparingTo("4.00");
        // ((60.000 + 6.000) / 2) * 5 % / 1.500 = 33.000 * 0,05 / 1.500 = 1,10
        assertThat(a.zins()).isEqualByComparingTo("1.10");
        assertThat(a.wartung()).isEqualByComparingTo("1.00");
        assertThat(a.platz()).isEqualByComparingTo("0.50");
        assertThat(a.versicherung()).isEqualByComparingTo("0.20");
        // 20 kW * 50 % * 0,30 = 3,00 je Stunde
        assertThat(a.energie()).isEqualByComparingTo("3.00");
        assertThat(a.summe()).isEqualByComparingTo("9.80");
        assertThat(a.hinweis()).isNull();
    }

    @Test
    void ohneLaufstundenIstDerSatzNichtBerechenbarUndDerHinweisSagtWarum() {
        Eingabe ohne = new Eingabe(d("60000"), d("6000"), d("9"), d("5"), d("1500"), null, null, d("20"), d("0.30"), d("50"), null);
        Aufstellung a = MaschinenKostenRechner.rechne(ohne);

        assertThat(a.summe()).isNull();
        assertThat(a.hinweis()).contains("Laufstunden");
        // Die Energie je Stunde braucht keine Laufstunden und wird trotzdem gezeigt
        assertThat(a.energie()).isEqualByComparingTo("3.00");
    }

    @Test
    void ohneNutzungsdauerFehltDieAbschreibung() {
        Eingabe e = new Eingabe(d("12000"), null, null, null, null, null, null, null, null, null, d("1000"));
        Aufstellung a = MaschinenKostenRechner.rechne(e);
        assertThat(a.summe()).isNull();
        assertThat(a.hinweis()).contains("Nutzungsdauer");
    }

    @Test
    void nurStromBrauchtWederLaufstundenNochAnschaffung() {
        Eingabe e = new Eingabe(null, null, null, null, null, null, null, d("5"), d("0.20"), null, null);
        Aufstellung a = MaschinenKostenRechner.rechne(e);
        // Auslastung leer = 100 %: 5 kW * 1,00 * 0,20 = 1,00
        assertThat(a.summe()).isEqualByComparingTo("1.00");
        assertThat(a.hinweis()).isNull();
    }

    @Test
    void fehlendeAngabenZaehlenAlsNullUndRestwertUeberAnschaffungGibtKeineAbschreibung() {
        Eingabe leer = new Eingabe(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(MaschinenKostenRechner.rechne(leer).summe()).isEqualByComparingTo("0.00");

        Eingabe rest = new Eingabe(d("1000"), d("1500"), d("5"), null, null, null, null, null, null, null, d("1000"));
        Aufstellung a = MaschinenKostenRechner.rechne(rest);
        assertThat(a.abschreibung()).isEqualByComparingTo("0.00");
    }

    @Test
    void dieSummeWirdAusUngerundetenTeilenGebildetUndAufCentGerundet() {
        // 1.000 / 3 Jahre / 700 Stunden = 0,476190...; Wartung 100 / 700 = 0,142857...; zusammen 0,619047... = 0,62
        Eingabe e = new Eingabe(d("1000"), null, d("3"), null, d("100"), null, null, null, null, null, d("700"));
        Aufstellung a = MaschinenKostenRechner.rechne(e);
        assertThat(a.abschreibung()).isEqualByComparingTo("0.48");
        assertThat(a.wartung()).isEqualByComparingTo("0.14");
        assertThat(a.summe()).isEqualByComparingTo("0.62");
    }
}
