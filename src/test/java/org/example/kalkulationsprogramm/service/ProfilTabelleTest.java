package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.service.ProfilTabelle.Eintrag;
import org.junit.jupiter.api.Test;

/** Die mitgelieferte Profiltabelle: Stichproben bekannter Werte und Plausibilitätsprüfungen gegen Tippfehler. */
class ProfilTabelleTest {

    private final ProfilTabelle tabelle = new ProfilTabelle();

    private Eintrag e(Profilform f, String name) {
        return tabelle.finde(f, name).orElseThrow(() -> new AssertionError(name + " fehlt"));
    }

    @Test
    void bekannteWerteStimmen() {
        assertThat(e(Profilform.IPE_PROFIL, "IPE 120").kgJeMeter()).isEqualByComparingTo("10.4");
        assertThat(e(Profilform.IPE_PROFIL, "IPE 200").kgJeMeter()).isEqualByComparingTo("22.4");
        assertThat(e(Profilform.IPE_PROFIL, "IPE 200").m2JeMeter()).isEqualByComparingTo("0.768");
        assertThat(e(Profilform.HEA_PROFIL, "HEA 200").kgJeMeter()).isEqualByComparingTo("42.3");
        assertThat(e(Profilform.HEB_PROFIL, "HEB 200").kgJeMeter()).isEqualByComparingTo("61.3");
        assertThat(e(Profilform.U_PROFIL, "UPN 200").kgJeMeter()).isEqualByComparingTo("25.3");
        assertThat(e(Profilform.IPE_PROFIL, "ipe 120").hoeheMm()).isEqualByComparingTo("120");
    }

    @Test
    void dieFormenUndAnzahlenStehenImmerNochSo() {
        assertThat(tabelle.formen()).containsExactlyInAnyOrder(Profilform.IPE_PROFIL, Profilform.HEA_PROFIL, Profilform.HEB_PROFIL, Profilform.U_PROFIL);
        assertThat(tabelle.fuer(Profilform.IPE_PROFIL)).hasSize(18);
        assertThat(tabelle.fuer(Profilform.HEA_PROFIL)).hasSize(19);
        assertThat(tabelle.fuer(Profilform.HEB_PROFIL)).hasSize(19);
        assertThat(tabelle.fuer(Profilform.U_PROFIL)).hasSize(17);
        assertThat(tabelle.fuer(Profilform.BLECH)).isEmpty();
    }

    @Test
    void jedeReiheWaechstMitDerGroesseUndDieBezeichnungenSindEindeutig() {
        for (Profilform f : tabelle.formen()) {
            List<Eintrag> reihe = tabelle.fuer(f);
            for (int i = 1; i < reihe.size(); i++) {
                Eintrag vor = reihe.get(i - 1);
                Eintrag jetzt = reihe.get(i);
                assertThat(jetzt.kgJeMeter()).as(jetzt.bezeichnung() + " muss schwerer sein als " + vor.bezeichnung()).isGreaterThan(vor.kgJeMeter());
                assertThat(jetzt.m2JeMeter()).as(jetzt.bezeichnung() + " darf nicht weniger Oberfläche haben als " + vor.bezeichnung()).isGreaterThanOrEqualTo(vor.m2JeMeter());
                assertThat(jetzt.hoeheMm()).as(jetzt.bezeichnung()).isGreaterThan(vor.hoeheMm());
            }
        }
        List<Eintrag> alle = tabelle.formen().stream().flatMap(f -> tabelle.fuer(f).stream()).toList();
        assertThat(new HashSet<>(alle.stream().map(Eintrag::bezeichnung).toList())).hasSameSizeAs(alle);
    }

    @Test
    void dieWerteSindPlausibelGegenEinenGrobenQuerschnitt() {
        for (Profilform f : tabelle.formen()) {
            for (Eintrag p : tabelle.fuer(f)) {
                // Das Gewicht darf nicht größer sein als ein voller Rechteckquerschnitt aus Höhe x Breite (Stahl) und nicht lächerlich klein
                BigDecimal voll = p.hoeheMm().multiply(p.breiteMm()).multiply(new BigDecimal("7.85")).divide(new BigDecimal("1000"), 4, java.math.RoundingMode.HALF_UP);
                assertThat(p.kgJeMeter()).as(p.bezeichnung()).isLessThan(voll);
                assertThat(p.kgJeMeter()).as(p.bezeichnung()).isGreaterThan(voll.multiply(new BigDecimal("0.10")));
                // Oberfläche je Meter ist rund der Umfang: nicht kleiner als 2 x (Höhe + Breite) abzüglich der Einzüge, nicht größer als 2 x Höhe + 5 x Breite (U-Profile haben geneigte, längere Flansche)
                BigDecimal umfang = p.hoeheMm().add(p.breiteMm()).multiply(new BigDecimal("2")).divide(new BigDecimal("1000"), 4, java.math.RoundingMode.HALF_UP);
                assertThat(p.m2JeMeter()).as(p.bezeichnung()).isGreaterThan(umfang.multiply(new BigDecimal("0.8")));
                assertThat(p.m2JeMeter()).as(p.bezeichnung()).isLessThan(p.hoeheMm().multiply(new BigDecimal("2")).add(p.breiteMm().multiply(new BigDecimal("5"))).divide(new BigDecimal("1000"), 4, java.math.RoundingMode.HALF_UP));
            }
        }
    }

    @Test
    void ebenesBreiterTraegerHEBIstSchwererAlsHEAGleicherGroesse() {
        for (Eintrag a : tabelle.fuer(Profilform.HEA_PROFIL)) {
            String nr = a.bezeichnung().substring(4);
            Eintrag b = e(Profilform.HEB_PROFIL, "HEB " + nr);
            assertThat(b.kgJeMeter()).as("HEB " + nr + " gegen HEA " + nr).isGreaterThan(a.kgJeMeter());
        }
    }
}
