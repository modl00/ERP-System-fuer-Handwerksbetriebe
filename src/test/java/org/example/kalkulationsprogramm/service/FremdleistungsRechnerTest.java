package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit;
import org.example.kalkulationsprogramm.domain.Fremdleistung.TransportArt;
import org.junit.jupiter.api.Test;

class FremdleistungsRechnerTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private Fremdleistung verzinken() {
        Fremdleistung f = new Fremdleistung();
        f.setEinheit(Einheit.KG);
        f.setPreisJeEinheit(d("1.20"));
        return f;
    }

    @Test
    void einheitspreisMalMenge() {
        var e = FremdleistungsRechner.rechne(verzinken(), d("120"), null);
        assertThat(e.summe()).isEqualByComparingTo("144.00");
        assertThat(e.kostenarten()).extracting("art").containsExactly("EINHEITSPREIS");
        assertThat(e.kostenarten().get(0).text()).isEqualTo("120 kg × 1,2 €");
    }

    @Test
    void derMindestpreisHebtDenEinheitspreisAnWennDieMengeZuKleinIst() {
        Fremdleistung f = verzinken();
        f.setMindestpreis(d("45.00"));

        var klein = FremdleistungsRechner.rechne(f, d("10"), null);
        assertThat(klein.summe()).isEqualByComparingTo("45.00");
        assertThat(klein.kostenarten()).extracting("art").containsExactly("MINDESTPREIS");

        var gross = FremdleistungsRechner.rechne(f, d("100"), null);
        assertThat(gross.summe()).isEqualByComparingTo("120.00");
        assertThat(gross.kostenarten()).extracting("art").containsExactly("EINHEITSPREIS");
    }

    @Test
    void nurMindestpreisOhneEinheitspreisGiltJeAuftrag() {
        Fremdleistung f = new Fremdleistung();
        f.setEinheit(Einheit.STUECK);
        f.setMindestpreis(d("30.00"));
        assertThat(FremdleistungsRechner.rechne(f, d("7"), null).summe()).isEqualByComparingTo("30.00");
    }

    @Test
    void einmaligeUndAnteiligeKostenKommenDazuAnteiligVerteiltAufDieMenge() {
        Fremdleistung f = verzinken();
        f.setEinmalBetrag(d("25.00"));
        f.setAnteilBetrag(d("80.00"));
        f.setAnteilChargenmenge(d("200"));

        var e = FremdleistungsRechner.rechne(f, d("50"), null);

        // 50 kg x 1,20 = 60 + einmalig 25 + 80 x 50/200 = 20
        assertThat(e.kostenarten()).extracting("art").containsExactly("EINHEITSPREIS", "EINMALIG", "ANTEILIG");
        assertThat(e.summe()).isEqualByComparingTo("105.00");
    }

    @Test
    void transportKennt4Arten() {
        Fremdleistung f = verzinken();
        f.setTransportArt(TransportArt.PAUSCHAL);
        f.setTransportBetrag(d("35"));
        assertThat(FremdleistungsRechner.rechne(f, d("100"), null).summe()).isEqualByComparingTo("155.00");

        f.setTransportArt(TransportArt.JE_FAHRT);
        f.setTransportBetrag(d("40"));
        f.setTransportFahrten(2);
        assertThat(FremdleistungsRechner.rechne(f, d("100"), null).summe()).isEqualByComparingTo("200.00");

        f.setTransportArt(TransportArt.JE_KM);
        f.setTransportBetrag(d("0.80"));
        f.setTransportKm(d("25"));
        f.setTransportFahrten(1);
        // 25 km hin und zurück = 50 km x 0,80 = 40
        assertThat(FremdleistungsRechner.rechne(f, d("100"), null).summe()).isEqualByComparingTo("160.00");

        f.setTransportArt(TransportArt.JE_KG);
        f.setTransportBetrag(d("0.05"));
        // Der Veredler rechnet nach kg: die Menge ist das Gewicht
        assertThat(FremdleistungsRechner.rechne(f, d("100"), null).summe()).isEqualByComparingTo("125.00");
    }

    @Test
    void transportJeKgBrauchtBeiAndererEinheitDasGewichtSonstIstDieSummeUnvollstaendig() {
        Fremdleistung f = new Fremdleistung();
        f.setEinheit(Einheit.QUADRATMETER);
        f.setPreisJeEinheit(d("12"));
        f.setTransportArt(TransportArt.JE_KG);
        f.setTransportBetrag(d("0.10"));

        var ohne = FremdleistungsRechner.rechne(f, d("10"), null);
        assertThat(ohne.gewichtFehlt()).isTrue();
        assertThat(ohne.summe()).isEqualByComparingTo("120.00");
        assertThat(ohne.kostenarten()).extracting("art").doesNotContain("TRANSPORT");

        var mit = FremdleistungsRechner.rechne(f, d("10"), d("300"));
        assertThat(mit.gewichtFehlt()).isFalse();
        assertThat(mit.summe()).isEqualByComparingTo("150.00");
    }

    @Test
    void sonstigeKostenMitBezeichnung() {
        Fremdleistung f = verzinken();
        f.setSonstigeBetrag(d("15.00"));
        f.setSonstigeBezeichnung("Entsorgung");
        var e = FremdleistungsRechner.rechne(f, d("100"), null);
        assertThat(e.kostenarten()).extracting("text").contains("Entsorgung");
        assertThat(e.summe()).isEqualByComparingTo("135.00");
    }

    @Test
    void derProzentzuschlagGiltAufDieSummeAllerUebrigenKostenUndKommtZuletzt() {
        Fremdleistung f = verzinken();
        f.setEinmalBetrag(d("20.00"));
        f.setTransportArt(TransportArt.PAUSCHAL);
        f.setTransportBetrag(d("30.00"));
        f.setProzentZuschlag(d("10"));

        var e = FremdleistungsRechner.rechne(f, d("100"), null);

        // (120 + 20 + 30) = 170, 10 % = 17
        assertThat(e.kostenarten()).extracting("art").containsExactly("EINHEITSPREIS", "EINMALIG", "TRANSPORT", "PROZENT");
        assertThat(e.kostenarten().get(3).betrag()).isEqualByComparingTo("17.00");
        assertThat(e.summe()).isEqualByComparingTo("187.00");
    }

    @Test
    void dieSummeIstDieSummeDerGerundetenBetraege() {
        Fremdleistung f = new Fremdleistung();
        f.setEinheit(Einheit.KG);
        f.setPreisJeEinheit(d("0.333"));
        f.setAnteilBetrag(d("10"));
        f.setAnteilChargenmenge(d("3"));
        var e = FremdleistungsRechner.rechne(f, d("1"), null);
        BigDecimal summeDerZeilen = e.kostenarten().stream().map(k -> k.betrag()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(e.summe()).isEqualByComparingTo(summeDerZeilen);
    }
}
