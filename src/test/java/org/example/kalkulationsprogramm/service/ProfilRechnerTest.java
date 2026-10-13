package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.service.ProfilRechner.Masse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Prüft die Rechnung gegen bekannte Katalogwerte (Stahl, 7,85 kg/dm³). */
class ProfilRechnerTest {

    private final ProfilRechner rechner = new ProfilRechner();

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Masse masse(String durchmesser, String hoehe, String breite, String wand) {
        return new Masse(durchmesser == null ? null : d(durchmesser), hoehe == null ? null : d(hoehe), breite == null ? null : d(breite),
                wand == null ? null : d(wand));
    }

    @Test
    void rundrohrDe42Komma4x2WiegtWieImKatalog() {
        var e = rechner.berechne(Profilform.RUNDROHR, masse("42.4", null, null, "2"), null);

        assertThat(e.kgProMeter().doubleValue()).isEqualTo(1.9926);
        assertThat(e.mantelflaecheQm().doubleValue()).isCloseTo(0.1332, within(0.0001));
    }

    @Test
    void quadratrohrMitEckenradienStimmtMitKatalogUeberein() {
        assertThat(rechner.berechne(Profilform.QUADRATROHR, masse(null, "40", null, "2"), null).kgProMeter().doubleValue()).isCloseTo(2.31, within(0.02));
        assertThat(rechner.berechne(Profilform.QUADRATROHR, masse(null, "100", null, "5"), null).kgProMeter().doubleValue()).isCloseTo(14.4, within(0.1));
    }

    @Test
    void rechteckrohr60x40x3() {
        var e = rechner.berechne(Profilform.RECHTECKROHR, masse(null, "60", "40", "3"), null);

        assertThat(e.kgProMeter().doubleValue()).isCloseTo(4.25, within(0.05));
        assertThat(e.querschnittCm2().doubleValue()).isCloseTo(5.41, within(0.06));
    }

    @Test
    void vollmaterial() {
        assertThat(rechner.berechne(Profilform.RUNDSTAB, masse("20", null, null, null), null).kgProMeter().doubleValue()).isCloseTo(2.47, within(0.01));
        assertThat(rechner.berechne(Profilform.VIERKANTSTAB, masse(null, "12", null, null), null).kgProMeter().doubleValue()).isCloseTo(1.13, within(0.01));
        assertThat(rechner.berechne(Profilform.FLACHSTAB, masse(null, null, "40", "8"), null).kgProMeter().doubleValue()).isCloseTo(2.51, within(0.01));
        assertThat(rechner.berechne(Profilform.SECHSKANTSTAB, masse(null, "19", null, null), null).kgProMeter().doubleValue()).isCloseTo(2.45, within(0.02));
    }

    @Test
    void winkelIstEineNaeherungMitHinweis() {
        var e = rechner.berechne(Profilform.WINKEL_GLEICHSCHENKLIG, masse(null, "40", null, "4"), null);
        assertThat(e.kgProMeter().doubleValue()).isCloseTo(2.42, within(0.06));
        assertThat(e.hinweis()).contains("Näherung");

        var ungleich = rechner.berechne(Profilform.WINKEL_UNGLEICHSCHENKLIG, masse(null, "60", "40", "5"), null);
        assertThat(ungleich.kgProMeter().doubleValue()).isCloseTo(3.76, within(0.1));
    }

    @Test
    void blechRechnetKiloProQuadratmeter() {
        var e = rechner.berechne(Profilform.BLECH, masse(null, null, null, "2"), null);

        assertThat(e.kgProQm().doubleValue()).isEqualTo(15.7);
        assertThat(e.kgProMeter()).isNull();
    }

    @Test
    void dichteDesWerkstoffsGiltStattStahl() {
        var edelstahl = rechner.berechne(Profilform.RUNDSTAB, masse("20", null, null, null), d("7.9"));
        var alu = rechner.berechne(Profilform.RUNDSTAB, masse("20", null, null, null), d("2.7"));

        assertThat(edelstahl.kgProMeter().doubleValue()).isCloseTo(2.482, within(0.001));
        assertThat(alu.kgProMeter().doubleValue()).isCloseTo(0.848, within(0.001));
    }

    @Test
    void walzprofileKommenAusDerNormtabelle() {
        var ipe = rechner.berechne(Profilform.IPE_PROFIL, masse(null, "200", null, null), null);
        assertThat(ipe.kgProMeter().doubleValue()).isEqualTo(22.4);
        assertThat(ipe.querschnittCm2().doubleValue()).isCloseTo(28.5, within(0.1));
        assertThat(ipe.herkunft()).isEqualTo(ProfilRechner.Herkunft.NORMTABELLE);
        assertThat(ipe.mantelflaecheQm()).isNull();

        assertThat(rechner.berechne(Profilform.HEA_PROFIL, masse(null, "100", null, null), null).kgProMeter().doubleValue()).isEqualTo(16.7);
        assertThat(rechner.berechne(Profilform.HEA_PROFIL, masse(null, "600", null, null), null).kgProMeter().doubleValue()).isEqualTo(178);
        assertThat(rechner.berechne(Profilform.HEB_PROFIL, masse(null, "100", null, null), null).kgProMeter().doubleValue()).isEqualTo(20.4);
        assertThat(rechner.berechne(Profilform.U_PROFIL, masse(null, "200", null, null), null).kgProMeter().doubleValue()).isEqualTo(25.3);
    }

    @Test
    void unbekannteWalzprofilGroesseWirdMitHinweisAbgelehnt() {
        assertThatThrownBy(() -> rechner.berechne(Profilform.IPE_PROFIL, masse(null, "205", null, null), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Nenngröße");
    }

    @Test
    void fehlendeOderUnmoeglicheMasseSagenKlarWasFehlt() {
        assertThatThrownBy(() -> rechner.berechne(Profilform.RUNDROHR, masse("42.4", null, null, null), null))
                .hasMessageContaining("fehlt die Wandstärke");
        assertThatThrownBy(() -> rechner.berechne(Profilform.QUADRATROHR, masse(null, "40", null, "20"), null))
                .hasMessageContaining("zu groß");
        assertThatThrownBy(() -> rechner.berechne(Profilform.RUNDROHR, masse("10", null, null, "-1"), null))
                .hasMessageContaining("fehlt die Wandstärke");
        assertThatThrownBy(() -> rechner.berechne(null, masse(null, null, null, null), null)).hasMessageContaining("Form");
    }

    @Test
    void formenOhneFormelWerdenAbgelehnt() {
        assertThatThrownBy(() -> rechner.berechne(Profilform.Z_PROFIL, masse(null, "100", "50", "4"), null))
                .hasMessageContaining("keine Berechnung");
        assertThatThrownBy(() -> rechner.berechne(Profilform.LOCHBLECH, masse(null, null, null, "2"), null))
                .hasMessageContaining("keine Berechnung");
        assertThatThrownBy(() -> rechner.berechne(Profilform.SONSTIGES, masse(null, null, null, "2"), null))
                .hasMessageContaining("keine Berechnung");
    }

    @Test
    void riesigeMasseGebenKlarenFehlerStattUeberlauf() {
        assertThatThrownBy(() -> rechner.berechne(Profilform.RUNDSTAB, masse("1e200", null, null, null), null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("zu groß");
        assertThatThrownBy(() -> rechner.berechne(Profilform.IPE_PROFIL, masse(null, "4294967496", null, null), null))
                .hasMessageContaining("zu groß");
        // erlaubtes Maß, aber das Ergebnis passt nicht mehr in die Datenbankspalten
        assertThatThrownBy(() -> rechner.berechne(Profilform.RUNDSTAB, masse("100000", null, null, null), null))
                .hasMessageContaining("Die Maße sind zu groß");
        assertThatThrownBy(() -> rechner.berechne(Profilform.BLECH, masse(null, null, null, "100000"), d("25")))
                .hasMessageContaining("Die Maße sind zu groß");
    }

    @Test
    void hohlprofilMitZuGrosserWandHatKeinePassendeEcke() {
        assertThatThrownBy(() -> rechner.berechne(Profilform.QUADRATROHR, masse(null, "15", null, "4"), null))
                .hasMessageContaining("Ecken");
        assertThat(rechner.berechne(Profilform.QUADRATROHR, masse(null, "20", null, "2"), null).kgProMeter().doubleValue()).isCloseTo(1.05, within(0.03));
    }

    @Test
    void kleineUndGrosseUProfileSindInDerTabelle() {
        assertThat(rechner.berechne(Profilform.U_PROFIL, masse(null, "50", null, null), null).kgProMeter().doubleValue()).isEqualTo(5.59);
        assertThat(rechner.berechne(Profilform.U_PROFIL, masse(null, "400", null, null), null).kgProMeter().doubleValue()).isEqualTo(71.8);
    }
}
