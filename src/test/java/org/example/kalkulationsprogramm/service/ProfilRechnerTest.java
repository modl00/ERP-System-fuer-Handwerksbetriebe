package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.service.ProfilRechner.Ergebnis;
import org.junit.jupiter.api.Test;

/** Gewicht und Oberfläche aus den Maßen, als Handrechnung (Stahl 7,85 kg/dm³). */
class ProfilRechnerTest {

    private static BigDecimal d(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static Ergebnis rechne(Profilform f, String dm, String h, String b, String t) {
        return ProfilRechner.rechne(f, d(dm), d(h), d(b), d(t), null);
    }

    @Test
    void rundrohrHandlauf42_4x2() {
        // Fläche = π × (42,4 − 2) × 2 = 253,85 mm²; × 7,85 / 1000 = 1,9927 kg/m; Umfang π × 42,4 = 133,2 mm
        Ergebnis e = rechne(Profilform.RUNDROHR, "42.4", null, null, "2");
        assertThat(e.kgJeMeter().doubleValue()).isCloseTo(1.9927, within(0.0002));
        assertThat(e.m2JeMeter().doubleValue()).isCloseTo(0.1332, within(0.0002));
        assertThat(e.hinweis()).contains("7,85".replace(',', '.')).contains("Außenfläche");
    }

    @Test
    void quadratrohrUndRechteckrohr() {
        // Quadrat 40 x 40 x 2: 4 × 2 × 38 = 304 mm²; × 7,85 / 1000 = 2,3864; Umfang 160 mm
        Ergebnis q = rechne(Profilform.QUADRATROHR, null, "40", null, "2");
        assertThat(q.kgJeMeter()).isEqualByComparingTo("2.3864");
        assertThat(q.m2JeMeter()).isEqualByComparingTo("0.1600");
        // Rechteck 60 x 40 x 3: 2 × 3 × (100 − 6) = 564 mm²; × 7,85 / 1000 = 4,4274; Umfang 200 mm
        Ergebnis r = rechne(Profilform.RECHTECKROHR, null, "60", "40", "3");
        assertThat(r.kgJeMeter()).isEqualByComparingTo("4.4274");
        assertThat(r.m2JeMeter()).isEqualByComparingTo("0.2000");
    }

    @Test
    void staebeUndFlachstahl() {
        // Rundstab 12: π × 36 = 113,1 mm²; × 7,85 / 1000 = 0,8878
        assertThat(rechne(Profilform.RUNDSTAB, "12", null, null, null).kgJeMeter().doubleValue()).isCloseTo(0.8878, within(0.0002));
        // Vierkant 12 x 12: 144 mm²; × 7,85 / 1000 = 1,1304; Umfang 48 mm
        Ergebnis v = rechne(Profilform.VIERKANTSTAB, null, "12", null, null);
        assertThat(v.kgJeMeter()).isEqualByComparingTo("1.1304");
        assertThat(v.m2JeMeter()).isEqualByComparingTo("0.0480");
        // Flachstahl 40 x 8: 320 mm²; × 7,85 / 1000 = 2,5120; Umfang 96 mm
        Ergebnis f = rechne(Profilform.FLACHSTAB, null, "8", "40", null);
        assertThat(f.kgJeMeter()).isEqualByComparingTo("2.5120");
        assertThat(f.m2JeMeter()).isEqualByComparingTo("0.0960");
    }

    @Test
    void winkelSindAngenaehertUndSagenDasDazu() {
        // L 40 x 40 x 4 ohne Rundungen: 4 × (80 − 4) = 304 mm²; × 7,85 / 1000 = 2,3864 (Normtabelle: 2,42)
        Ergebnis w = rechne(Profilform.WINKEL_GLEICHSCHENKLIG, null, "40", null, "4");
        assertThat(w.kgJeMeter()).isEqualByComparingTo("2.3864");
        assertThat(w.m2JeMeter()).isEqualByComparingTo("0.1600");
        assertThat(w.hinweis()).contains("Angenähert").contains("2 %");
        // Ungleichschenklig 60 x 40 x 5: 5 × (100 − 5) = 475 mm²
        assertThat(rechne(Profilform.WINKEL_UNGLEICHSCHENKLIG, null, "60", "40", "5").kgJeMeter()).isEqualByComparingTo("3.7288");
    }

    @Test
    void dieDichteDesWerkstoffsGiltStattStahl() {
        // Flachstahl 40 x 8 aus Aluminium (2,7 kg/dm³): 320 × 2,7 / 1000 = 0,864
        Ergebnis e = ProfilRechner.rechne(Profilform.FLACHSTAB, null, d("8"), d("40"), null, d("2.7"));
        assertThat(e.kgJeMeter()).isEqualByComparingTo("0.8640");
    }

    @Test
    void fehlendeMasseUnmoeglicheWandUndFormenOhneFormelWerdenMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> rechne(Profilform.RUNDROHR, "42.4", null, null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wandstärke");
        assertThatThrownBy(() -> rechne(Profilform.RUNDROHR, null, null, null, "2")).hasMessageContaining("Durchmesser");
        assertThatThrownBy(() -> rechne(Profilform.QUADRATROHR, null, "40", null, "20")).hasMessageContaining("zu groß");
        assertThatThrownBy(() -> rechne(Profilform.IPE_PROFIL, null, "200", "100", null)).hasMessageContaining("Profiltabelle");
        assertThatThrownBy(() -> rechne(Profilform.SONSTIGES, null, null, null, null)).hasMessageContaining("keine Formel");
        assertThatThrownBy(() -> rechne(null, null, null, null, null)).hasMessageContaining("Form wählen");
        assertThatThrownBy(() -> rechne(Profilform.RUNDSTAB, "0", null, null, null)).hasMessageContaining("Durchmesser");
    }

    @Test
    void blechRechnetDickeMalDichteUndBeideSeiten() {
        // 10 mm Stahlblech: 10 × 7,85 = 78,5 kg/m²; beide Seiten: 2 m² je m²
        Ergebnis e = rechne(Profilform.BLECH, null, null, null, "10");
        assertThat(e.kgJeQm()).isEqualByComparingTo("78.5");
        assertThat(e.m2JeQm()).isEqualByComparingTo("2");
        assertThat(e.kgJeMeter()).isNull();
        assertThat(e.dichte()).isEqualByComparingTo("7.85");
        assertThat(e.hinweis()).contains("Dicke").contains("beide Seiten");
    }

    @Test
    void blechAusEdelstahlNimmtDieDichteDesWerkstoffs() {
        Ergebnis e = ProfilRechner.rechne(Profilform.BLECH, null, null, null, d("5"), d("7.9"));
        assertThat(e.kgJeQm()).isEqualByComparingTo("39.5");
        assertThat(e.dichte()).isEqualByComparingTo("7.9");
    }

    @Test
    void blechBrauchtDieDickeUndPruftSieAufSinn() {
        assertThatThrownBy(() -> rechne(Profilform.BLECH, null, null, null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Blechdicke");
        assertThatThrownBy(() -> rechne(Profilform.BLECH, null, null, null, "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rechne(Profilform.BLECH, null, null, null, "5000")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500 mm");
    }

    @Test
    void riffelUndLochblechVerweisenAufDasDatenblatt() {
        for (Profilform f : new Profilform[] { Profilform.RIFFELBLECH, Profilform.LOCHBLECH }) {
            assertThatThrownBy(() -> rechne(f, null, null, null, "5")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Datenblatt");
        }
    }

    @Test
    void auchMeterwareLiefertDieDichteMit() {
        assertThat(rechne(Profilform.RUNDSTAB, "12", null, null, null).dichte()).isEqualByComparingTo("7.85");
        assertThat(rechne(Profilform.RUNDSTAB, "12", null, null, null).kgJeQm()).isNull();
    }
}
