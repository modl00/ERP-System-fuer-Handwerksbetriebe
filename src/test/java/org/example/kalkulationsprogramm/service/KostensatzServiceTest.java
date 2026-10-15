package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Qualifikation;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile;
import org.example.kalkulationsprogramm.service.KostensatzService.Ergebnis;
import org.example.kalkulationsprogramm.service.KostensatzService.Zeile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Kostensatz und Verrechnungssatz als Handrechnung. Nur Dummy-Daten (DSGVO). */
class KostensatzServiceTest {

    private VerrechnungslohnErgebnisDto e;
    private final Map<Long, Mitarbeiter> personen = new LinkedHashMap<>();

    private void person(long id, String vorname, String nachname, Qualifikation q, String lohn, String stunden) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setQualifikation(q);
        personen.put(id, m);
        MitarbeiterLohnZeile l = new MitarbeiterLohnZeile();
        l.setMitarbeiterId(id);
        l.setGesamtkosten(new BigDecimal(lohn));
        e.getLohnzeilen().add(l);
        MitarbeiterStundenZeile s = new MitarbeiterStundenZeile();
        s.setMitarbeiterId(id);
        s.setVerkaeuflicheStunden(new BigDecimal(stunden));
        e.getStundenzeilen().add(s);
    }

    @BeforeEach
    void setUp() {
        e = new VerrechnungslohnErgebnisDto();
        e.setJahr(2026);
        e.setModus(VerrechnungslohnErgebnisDto.Modus.HOCHRECHNUNG);
        // Meister Max: 60.000 € Lohn, 1.500 h; Facharbeiter Erika: 40.000 €, 1.600 h; Büro (Facharbeiter) Anna: 20.000 €, 0 h; Gemeinkosten 30.000 €
        person(1, "Max", "Mustermann", Qualifikation.MEISTER, "60000", "1500");
        person(2, "Erika", "Mustermann", Qualifikation.FACHARBEITER, "40000", "1600");
        person(3, "Anna", "Beispiel", Qualifikation.FACHARBEITER, "20000", "0");
        e.setGemeinkostenGesamt(new BigDecimal("30000"));
    }

    private Ergebnis rechne(String wagnis, String gewinn) {
        return KostensatzService.rechne(e, personen, new BigDecimal(wagnis), new BigDecimal(gewinn));
    }

    @Test
    void betriebHandrechnungMitUmlageKostensatzWagnisUndGewinn() {
        Ergebnis r = rechne("5", "10");
        Zeile b = r.betrieb();

        // Lohn 120.000 / 3.100 h = 38,7097 €; Umlage 30.000 / 3.100 = 9,6774 €; Kostensatz 48,3871 €
        assertThat(r.lohnkostenGesamt()).isEqualByComparingTo("120000.00");
        assertThat(r.stundenGesamt()).isEqualByComparingTo("3100.00");
        assertThat(b.personalkostensatz()).isEqualByComparingTo("38.71");
        assertThat(b.umlage()).isEqualByComparingTo("9.68");
        assertThat(b.kostensatz()).isEqualByComparingTo("48.39");
        // Wagnis 5 %: 48,3871 × 1,05 = 50,8065 (+2,42); Gewinn 10 % darauf: 55,8871 (+5,08)
        assertThat(b.wagnisBetrag()).isEqualByComparingTo("2.42");
        assertThat(b.gewinnBetrag()).isEqualByComparingTo("5.08");
        assertThat(b.verrechnungssatz()).isEqualByComparingTo("55.89");
        assertThat(b.anzahl()).isEqualTo(3);
        assertThat(b.hinweis()).isNull();
    }

    @Test
    void dieZeileBetriebIstGenauDerSelbstkostensatzDesBisherigenRechners() {
        // Bisher: (Lohn + Gemeinkosten) / Stunden = 150.000 / 3.100 = 48,39
        BigDecimal bisher = new BigDecimal("120000").add(new BigDecimal("30000")).divide(new BigDecimal("3100"), 2, java.math.RoundingMode.HALF_UP);
        assertThat(rechne("0", "0").betrieb().kostensatz()).isEqualByComparingTo(bisher);
        // Ohne Wagnis und Gewinn ist der Verrechnungssatz der Kostensatz
        assertThat(rechne("0", "0").betrieb().verrechnungssatz()).isEqualByComparingTo(bisher);
    }

    @Test
    void gruppenNachQualifikationRechnenMitDemLohnUndDenStundenDerGruppe() {
        Ergebnis r = rechne("0", "0");

        assertThat(r.gruppen()).extracting(Zeile::name).containsExactly("Facharbeiter", "Meister");
        Zeile meister = r.gruppen().stream().filter(z -> z.name().equals("Meister")).findFirst().orElseThrow();
        // 60.000 / 1.500 = 40,00 + Umlage 9,68 = 49,68
        assertThat(meister.personalkostensatz()).isEqualByComparingTo("40.00");
        assertThat(meister.kostensatz()).isEqualByComparingTo("49.68");
        Zeile fach = r.gruppen().stream().filter(z -> z.name().equals("Facharbeiter")).findFirst().orElseThrow();
        // Erika und Anna zusammen: 60.000 / 1.600 = 37,50 + 9,68 = 47,18 (Annas Büro-Lohn ohne verkäufliche Stunden steckt in der Gruppe)
        assertThat(fach.anzahl()).isEqualTo(2);
        assertThat(fach.personalkostensatz()).isEqualByComparingTo("37.50");
        assertThat(fach.kostensatz()).isEqualByComparingTo("47.18");
    }

    @Test
    void jedePersonHatEineEigeneZeileUndOhneVerkaeuflicheStundenGibtEsKeinenSatzSondernEinenHinweis() {
        Ergebnis r = rechne("0", "0");

        assertThat(r.personen()).extracting(Zeile::name).containsExactly("Anna Beispiel", "Erika Mustermann", "Max Mustermann");
        Zeile anna = r.personen().get(0);
        assertThat(anna.personalkostensatz()).isNull();
        assertThat(anna.verrechnungssatz()).isNull();
        assertThat(anna.hinweis()).contains("Keine verkäuflichen Stunden");
        assertThat(anna.lohnkosten()).isEqualByComparingTo("20000.00");
        Zeile erika = r.personen().get(1);
        assertThat(erika.personalkostensatz()).isEqualByComparingTo("25.00");
        assertThat(erika.kostensatz()).isEqualByComparingTo("34.68");
    }

    @Test
    void mitarbeiterOhneQualifikationStehenUnterOhneAngabeMitHinweis() {
        person(4, "Karl", "Muster", null, "30000", "1000");
        Ergebnis r = rechne("0", "0");

        assertThat(r.gruppen()).extracting(Zeile::name).containsExactly("Facharbeiter", "Meister", "Ohne Angabe");
        assertThat(r.hinweise()).anyMatch(h -> h.contains("keine Qualifikation eingetragen"));
    }

    @Test
    void ohneVerkaeufticheStundenImBetriebGibtEsKeineSaetzeUndDenHinweis() {
        e.getStundenzeilen().forEach(s -> s.setVerkaeuflicheStunden(BigDecimal.ZERO));
        Ergebnis r = rechne("0", "0");

        assertThat(r.betrieb().kostensatz()).isNull();
        assertThat(r.umlage()).isNull();
        assertThat(r.hinweise()).anyMatch(h -> h.contains("keine verkäuflichen Stunden"));
    }

    @Test
    void fehlendeDatenAusDemRechnerWerdenAlsHinweisWeitergegeben() {
        e.getDatenLuecken().add(new VerrechnungslohnErgebnisDto.DatenLuecke());
        assertThat(rechne("0", "0").hinweise()).anyMatch(h -> h.contains("Schätzung"));
    }
}
