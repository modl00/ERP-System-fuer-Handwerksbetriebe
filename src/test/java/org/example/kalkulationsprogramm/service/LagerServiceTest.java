package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Lager.ArtikelAenderungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortEingabe;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

/** Lager gegen eine echte (H2-)Datenbank: die Summe über die Buchungen ist der Kern. Nur Dummy-Daten (DSGVO). */
@DataJpaTest
@Import({ LagerService.class, LagerModulSchalterService.class })
class LagerServiceTest {

    @Autowired LagerService lager;
    @Autowired LagerModulSchalterService modulSchalter;
    @Autowired ArtikelRepository artikel;
    @Autowired ProjektRepository projekte;
    @Autowired ArtikelInProjektRepository positionen;

    Artikel pfosten;
    Artikel rohr;
    Long haupt;
    Long fahrzeug;

    @BeforeEach
    void setUp() {
        pfosten = artikel("Pfosten verzinkt", Verrechnungseinheit.STUECK);
        rohr = artikel("Rundrohr 42,4", Verrechnungseinheit.LAUFENDE_METER);
        haupt = lager.ortAnlegen(new LagerortEingabe("Hauptlager", "Halle")).id();
        fahrzeug = lager.ortAnlegen(new LagerortEingabe("Montagefahrzeug", null)).id();
    }

    private Artikel artikel(String name, Verrechnungseinheit einheit) {
        Artikel a = new Artikel();
        a.setKurzbeschreibung(name);
        a.setVerrechnungseinheit(einheit);
        return artikel.saveAndFlush(a);
    }

    private LagerBestandDto buche(String art, Artikel a, Long ort, String menge) {
        return lager.buche(new LagerBuchungEingabe(art, a.getId(), ort, new BigDecimal(menge), null, null, null, null));
    }

    private LagerBestandDto zeile(Artikel a) {
        return lager.bestand(null, false).stream().filter(z -> z.artikelId().equals(a.getId())).findFirst().orElseThrow();
    }

    @Test
    void haeufigBenutzteArtikelZaehlenZugangUndEntnahmeAberKeineInventurUndZuerstDieDesBenutzers() {
        Artikel kabel = artikel("Kabel 3x1,5", Verrechnungseinheit.LAUFENDE_METER);
        String max = "Max Mustermann (App)";
        String erika = "Erika Mustermann (App)";
        buche("ZUGANG", pfosten, haupt, "100");
        for (int i = 0; i < 3; i++) {
            lager.buche(new LagerBuchungEingabe("ABGANG", pfosten.getId(), haupt, new BigDecimal("1"), null, null, null, null), max);
        }
        lager.buche(new LagerBuchungEingabe("ZUGANG", rohr.getId(), haupt, new BigDecimal("10"), null, null, null, null), erika);
        lager.buche(new LagerBuchungEingabe("ABGANG", rohr.getId(), haupt, new BigDecimal("2"), null, null, null, null), erika);
        // Eine Inventur zählt nicht als Benutzung
        lager.buche(new LagerBuchungEingabe("KORREKTUR", kabel.getId(), haupt, new BigDecimal("5"), null, null, null, null), max);

        // Max: seine eigenen zuerst (Pfosten), dann die anderen des Betriebs (Rohr); das Kabel hatte nur eine Inventur
        assertThat(lager.haeufigeArtikelIds(max, 8)).containsExactly(pfosten.getId(), rohr.getId());
        // Erika: ihr Rohr zuerst, dann der Pfosten, der im Betrieb sonst am häufigsten gebucht wurde
        assertThat(lager.haeufigeArtikelIds(erika, 8)).containsExactly(rohr.getId(), pfosten.getId());
        // Begrenzt auf die gewünschte Anzahl
        assertThat(lager.haeufigeArtikelIds(erika, 1)).containsExactly(rohr.getId());
        // Wer noch nichts gebucht hat, bekommt die des Betriebs; ohne Namen ebenfalls
        assertThat(lager.haeufigeArtikelIds("Neu Mustermann (App)", 8)).containsExactly(pfosten.getId(), rohr.getId());
        assertThat(lager.haeufigeArtikelIds(null, 8)).containsExactly(pfosten.getId(), rohr.getId());
    }

    @Test
    void ohneBuchungenGibtEsKeineHaeufigenArtikel() {
        assertThat(lager.haeufigeArtikelIds("Max Mustermann (App)", 8)).isEmpty();
    }

    @Test
    void bestandIstDieSummeDerBuchungenJeLagerort() {
        buche("ZUGANG", pfosten, haupt, "50");
        buche("ZUGANG", pfosten, fahrzeug, "10");
        LagerBestandDto z = buche("ABGANG", pfosten, haupt, "8,5".replace(',', '.'));

        assertThat(z.bestand()).isEqualByComparingTo("51.5");
        assertThat(z.einheit()).isEqualTo("Stk");
        assertThat(z.orte()).extracting(LagerBestandDto.OrtBestand::lagerortName).containsExactly("Hauptlager", "Montagefahrzeug");
        assertThat(z.orte().get(0).bestand()).isEqualByComparingTo("41.5");
        assertThat(z.orte().get(1).bestand()).isEqualByComparingTo("10");
        assertThat(z.negativ()).isFalse();
    }

    @Test
    void einheitFolgtDerVerrechnungseinheitDesArtikels() {
        buche("ZUGANG", rohr, haupt, "12");
        assertThat(zeile(rohr).einheit()).isEqualTo("m");
        assertThat(LagerService.einheit(artikel("Blech", Verrechnungseinheit.QUADRATMETER))).isEqualTo("m²");
        assertThat(LagerService.einheit(artikel("Stab", Verrechnungseinheit.KILOGRAMM))).isEqualTo("kg");
        assertThat(LagerService.einheit(artikel("Ohne Einheit", null))).isEqualTo("Stk");
    }

    @Test
    void literArtikelWerdenInLiternGefuehrtUndAusDerProjektpositionEntnommen() {
        Artikel oel = artikel("Hydrauliköl HLP 46", Verrechnungseinheit.LITER);
        buche("ZUGANG", oel, haupt, "40.5");
        assertThat(zeile(oel).einheit()).isEqualTo("l");
        assertThat(zeile(oel).bestand()).isEqualByComparingTo("40.5");

        ArtikelInProjekt aip = position(oel, true, 5, null, null);
        assertThat(LagerService.mengeAusPosition(aip)).isEqualByComparingTo("5");
        lager.synchronisierePosition(aip, true);

        assertThat(zeile(oel).bestand()).isEqualByComparingTo("35.5");
    }

    @Test
    void entnahmeWirdNieBlockiertDerBestandWirdNegativUndMarkiert() {
        LagerBestandDto z = buche("ABGANG", pfosten, haupt, "3");

        assertThat(z.bestand()).isEqualByComparingTo("-3");
        assertThat(z.negativ()).isTrue();
        assertThat(lager.zusammenfassung().negativ()).isEqualTo(1);
    }

    @Test
    void inventurBuchtNurDieDifferenzZumGezaehltenBestand() {
        buche("ZUGANG", pfosten, haupt, "50");

        buche("KORREKTUR", pfosten, haupt, "47");
        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("47");
        List<LagerBuchungDto> verlauf = lager.verlauf(pfosten.getId());
        assertThat(verlauf.get(0).art()).isEqualTo("KORREKTUR");
        assertThat(verlauf.get(0).menge()).isEqualByComparingTo("-3");

        int vorher = lager.verlauf(pfosten.getId()).size();
        buche("KORREKTUR", pfosten, haupt, "47");
        assertThat(lager.verlauf(pfosten.getId())).as("gleicher Bestand: keine Buchung").hasSize(vorher);

        buche("KORREKTUR", pfosten, haupt, "0");
        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("0");
    }

    @Test
    void mindestbestandMarkiertArtikelUnterDerGrenzeUndLiefertSieInDerHandlungsliste() {
        buche("ZUGANG", pfosten, haupt, "5");
        buche("ZUGANG", rohr, haupt, "100");

        LagerBestandDto z = lager.setzeMindestbestand(pfosten.getId(), new BigDecimal("10"));

        assertThat(z.unterMindestbestand()).isTrue();
        assertThat(lager.zusammenfassung().unterMindestbestand()).isEqualTo(1);
        assertThat(lager.bestand(null, true)).extracting(LagerBestandDto::artikelId).containsExactly(pfosten.getId());

        buche("ZUGANG", pfosten, haupt, "5");   // genau am Mindestbestand: nicht darunter
        assertThat(zeile(pfosten).unterMindestbestand()).isFalse();
        assertThat(lager.bestand(null, true)).isEmpty();

        assertThat(lager.setzeMindestbestand(pfosten.getId(), null).mindestbestand()).isNull();
    }

    @Test
    void artikelNurMitMindestbestandErscheintAuchOhneBuchung() {
        lager.setzeMindestbestand(rohr.getId(), new BigDecimal("20"));

        LagerBestandDto z = zeile(rohr);

        assertThat(z.bestand()).isEqualByComparingTo("0");
        assertThat(z.unterMindestbestand()).isTrue();
    }

    @Test
    void sucheFiltertNachBezeichnung() {
        buche("ZUGANG", pfosten, haupt, "1");
        buche("ZUGANG", rohr, haupt, "1");

        assertThat(lager.bestand("pfost", false)).extracting(LagerBestandDto::artikelId).containsExactly(pfosten.getId());
        assertThat(lager.bestand("  ", false)).hasSize(2);
        assertThat(lager.bestand("gibtesnicht", false)).isEmpty();
    }

    @Test
    void ungueltigeBuchungenWerdenAbgelehnt() {
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ZERO, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ABGANG", pfosten.getId(), haupt, new BigDecimal("-1"), null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, new BigDecimal("10000001"), null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("KORREKTUR", pfosten.getId(), haupt, new BigDecimal("-1"), null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, null, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("UMBUCHUNG", pfosten.getId(), haupt, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe(null, pfosten.getId(), haupt, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", null, haupt, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", 0L, haupt, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", Long.MAX_VALUE, haupt, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), null, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), -5L, BigDecimal.ONE, null, null, null, null));
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ONE, 1L, null, null, null));      // Projekt nur bei Entnahme
        assertAbgelehnt(new LagerBuchungEingabe("ABGANG", pfosten.getId(), haupt, BigDecimal.ONE, 999999L, null, null, null)); // unbekanntes Projekt
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ONE, null, "x".repeat(256), null, null));
        assertAbgelehnt(null);
        assertThat(lager.verlauf(pfosten.getId())).isEmpty();
    }

    private void assertAbgelehnt(LagerBuchungEingabe e) {
        assertThatThrownBy(() -> lager.buche(e)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void notizMitSchadtextWirdAlsTextGespeichert() {
        String schad = "<script>alert(1)</script>'; DROP TABLE lager_buchung; --";

        lager.buche(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ONE, null, schad, null, null));

        assertThat(lager.verlauf(pfosten.getId()).get(0).notiz()).isEqualTo(schad);
        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("1");
    }

    @Test
    void lagerorteSindEindeutigUndDerLetzteAktiveBleibtErhalten() {
        assertThatThrownBy(() -> lager.ortAnlegen(new LagerortEingabe("  hauptlager ", null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> lager.ortAnlegen(new LagerortEingabe(" ", null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> lager.ortAnlegen(new LagerortEingabe("x".repeat(121), null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> lager.ortAendern(fahrzeug, new LagerortEingabe("Hauptlager", null))).isInstanceOf(ResponseStatusException.class);
        assertThat(lager.ortAendern(fahrzeug, new LagerortEingabe("Montagefahrzeug", "Sprinter")).beschreibung()).isEqualTo("Sprinter");

        lager.ortAktiv(fahrzeug, false);
        assertThatThrownBy(() -> lager.ortAktiv(haupt, false)).isInstanceOf(ResponseStatusException.class);
        assertThat(lager.orte()).extracting(LagerortDto::aktiv).containsExactly(true, false);

        // Auf einen deaktivierten Lagerort lässt sich nicht mehr buchen, der Bestand dort bleibt erhalten
        assertAbgelehnt(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), fahrzeug, BigDecimal.ONE, null, null, null, null));
        lager.ortAktiv(fahrzeug, true);
        buche("ZUGANG", pfosten, fahrzeug, "2");
        lager.ortAktiv(fahrzeug, false);
        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("2");
    }

    // ------------------------------------------------------------ Projektmaterial "aus Lager"

    private ArtikelInProjekt position(Artikel a, boolean ausLager, Integer stueck, BigDecimal meter, BigDecimal kg) {
        Projekt p = new Projekt();
        p.setBauvorhaben("Anbau Musterweg 1");
        p.setAuftragsnummer("2026-" + System.nanoTime());
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        p = projekte.saveAndFlush(p);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setProjekt(p);
        aip.setArtikel(a);
        aip.setStueckzahl(stueck);
        aip.setMeter(meter);
        aip.setKilogramm(kg);
        aip.setAusLager(ausLager);
        aip.setBestellt(ausLager);
        aip.setHinzugefuegtAm(LocalDate.now());
        return positionen.saveAndFlush(aip);
    }

    @Test
    void positionAusLagerEntnimmtEinmalUndBuchtBeimEntfernenZurueck() {
        buche("ZUGANG", pfosten, haupt, "20");
        ArtikelInProjekt aip = position(pfosten, true, 4, null, null);

        lager.synchronisierePosition(aip, true);
        lager.synchronisierePosition(aip, true);   // zweiter Aufruf bucht nichts mehr

        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("16");
        List<LagerBuchungDto> verlauf = lager.verlauf(pfosten.getId());
        assertThat(verlauf).hasSize(2);
        assertThat(verlauf.get(0).art()).isEqualTo("ABGANG");
        assertThat(verlauf.get(0).projekt()).contains("Anbau Musterweg 1");
        assertThat(verlauf.get(0).projektId()).isEqualTo(aip.getProjekt().getId());

        lager.synchronisierePosition(aip, false);
        lager.synchronisierePosition(aip, false);

        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("20");
        assertThat(lager.verlauf(pfosten.getId()).get(0).notiz()).startsWith("Rückbuchung");
    }

    @Test
    void positionOhneAusLagerBuchtNichts() {
        ArtikelInProjekt aip = position(pfosten, false, 4, null, null);

        lager.synchronisierePosition(aip, true);

        assertThat(lager.bestand(null, false)).isEmpty();
    }

    @Test
    void mengeEntsprichtDerEinheitDesArtikels() {
        ArtikelInProjekt stk = position(pfosten, true, 4, null, null);
        ArtikelInProjekt meter = position(rohr, true, 2, new BigDecimal("12.50"), null);
        Artikel stab = artikel("Vollstab", Verrechnungseinheit.KILOGRAMM);
        ArtikelInProjekt kg = position(stab, true, 3, null, new BigDecimal("7.80"));

        assertThat(LagerService.mengeAusPosition(stk)).isEqualByComparingTo("4");
        assertThat(LagerService.mengeAusPosition(meter)).isEqualByComparingTo("12.50");
        assertThat(LagerService.mengeAusPosition(kg)).isEqualByComparingTo("7.80");

        lager.synchronisierePosition(meter, true);
        assertThat(zeile(rohr).bestand()).isEqualByComparingTo("-12.5");
    }

    @Test
    void positionOhneErmittelbareMengeBuchtNichtUndWirftNicht() {
        Artikel stab = artikel("Vollstab ohne Gewicht", Verrechnungseinheit.KILOGRAMM);
        ArtikelInProjekt aip = position(stab, true, 3, null, null);

        lager.synchronisierePosition(aip, true);

        assertThat(lager.bestand(null, false)).isEmpty();
    }

    @Test
    void rueckbuchungGehtAnDenUrspruenglichenLagerort() {
        buche("ZUGANG", pfosten, fahrzeug, "5");
        ArtikelInProjekt aip = position(pfosten, true, 2, null, null);
        lager.synchronisierePosition(aip, true);   // Standardort = erster aktiver (Hauptlager)

        lager.ortAktiv(haupt, false);              // Hauptlager wird stillgelegt
        lager.synchronisierePosition(aip, false);  // Rückbuchung geht trotzdem dorthin zurück

        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("5");
        assertThat(zeile(pfosten).orte()).extracting(LagerBestandDto.OrtBestand::lagerortName).containsExactly("Montagefahrzeug");
    }

    @Test
    void ohneAktivenLagerortEntstehtHauptlagerBeimAutomatischenBuchen() {
        ArtikelInProjekt aip = position(pfosten, true, 1, null, null);
        lager.ortAktiv(fahrzeug, false);
        assertThatThrownBy(() -> lager.ortAktiv(haupt, false)).isInstanceOf(ResponseStatusException.class);

        lager.synchronisierePosition(aip, true);

        assertThat(zeile(pfosten).orte().get(0).lagerortName()).isEqualTo("Hauptlager");
    }

    @Test
    void umbuchungVerschiebtDieWareUndLaesstDenGesamtbestandGleich() {
        buche("ZUGANG", pfosten, haupt, "20");

        LagerBestandDto z = lager.umbuche(new UmbuchungEingabe(pfosten.getId(), haupt, fahrzeug, new BigDecimal("8"), "Für Montage"));

        assertThat(z.bestand()).isEqualByComparingTo("20");
        assertThat(z.orte()).extracting(LagerBestandDto.OrtBestand::lagerortName).containsExactly("Hauptlager", "Montagefahrzeug");
        assertThat(z.orte().get(0).bestand()).isEqualByComparingTo("12");
        assertThat(z.orte().get(1).bestand()).isEqualByComparingTo("8");
        List<LagerBuchungDto> verlauf = lager.verlauf(pfosten.getId());
        assertThat(verlauf).extracting(LagerBuchungDto::notiz).anyMatch(n -> n != null && n.startsWith("Umbuchung nach Montagefahrzeug") && n.contains("Für Montage"));
        assertThat(verlauf).extracting(LagerBuchungDto::notiz).anyMatch(n -> n != null && n.startsWith("Umbuchung von Hauptlager"));
    }

    @Test
    void umbuchungPruefteOrteMengeUndVorhandenenBestand() {
        buche("ZUGANG", pfosten, haupt, "5");
        Long pid = pfosten.getId();

        assertThatThrownBy(() -> lager.umbuche(new UmbuchungEingabe(pid, haupt, haupt, BigDecimal.ONE, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("zwei verschiedene");
        assertThatThrownBy(() -> lager.umbuche(new UmbuchungEingabe(pid, haupt, fahrzeug, BigDecimal.ZERO, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("größer als 0");
        assertThatThrownBy(() -> lager.umbuche(new UmbuchungEingabe(pid, haupt, fahrzeug, new BigDecimal("6"), null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nicht so viele");
        lager.ortAktiv(fahrzeug, false);
        assertThatThrownBy(() -> lager.umbuche(new UmbuchungEingabe(pid, haupt, fahrzeug, BigDecimal.ONE, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nicht mehr aktiv");
        assertThat(zeile(pfosten).bestand()).isEqualByComparingTo("5");
    }

    @Test
    void artikelAendernSetztBezeichnungUndEinheitUndBehaeltDenBestand() {
        buche("ZUGANG", pfosten, haupt, "7");

        LagerBestandDto z = lager.artikelAendern(pfosten.getId(), new ArtikelAenderungEingabe("  Pfosten 60x60  ", "LAUFENDE_METER"));

        assertThat(z.bezeichnung()).isEqualTo("Pfosten 60x60");
        assertThat(z.einheit()).isEqualTo("m");
        assertThat(z.bestand()).isEqualByComparingTo("7");
    }

    @Test
    void artikelAendernWeistLeereBezeichnungUndUnbekannteEinheitAb() {
        Long pid = pfosten.getId();
        assertThatThrownBy(() -> lager.artikelAendern(pid, new ArtikelAenderungEingabe("  ", "STUECK")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Bezeichnung");
        assertThatThrownBy(() -> lager.artikelAendern(pid, new ArtikelAenderungEingabe("Pfosten", "FUDER")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Einheit");
    }

    @Test
    void ausgeschaltetesModulBuchtProjektmaterialNichtAb() {
        ArtikelInProjekt aip = position(pfosten, true, 3, null, null);
        modulSchalter.setzeAktiv(false);

        lager.synchronisierePosition(aip, true);

        assertThat(lager.bestand(null, false)).isEmpty();
    }

    @Test
    void bestandVonLiefertAuchFuerEinenArtikelOhneBuchungenNullMitEinheit() {
        LagerBestandDto z = lager.bestandVon(rohr.getId());

        assertThat(z.artikelId()).isEqualTo(rohr.getId());
        assertThat(z.bestand()).isEqualByComparingTo("0");
        assertThat(z.einheit()).isEqualTo("m");
        assertThat(z.orte()).isEmpty();
    }

    @Test
    void bestandVonSummiertDieBuchungenDesArtikels() {
        buche("ZUGANG", pfosten, haupt, "20");
        buche("ABGANG", pfosten, haupt, "5");
        buche("ZUGANG", rohr, haupt, "99");

        LagerBestandDto z = lager.bestandVon(pfosten.getId());

        assertThat(z.bestand()).isEqualByComparingTo("15");
        assertThat(z.orte()).hasSize(1);
    }

    @Test
    void bestandVonEinemUnbekanntenArtikelWirdAbgelehnt() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lager.bestandVon(Long.MAX_VALUE))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void buchungenAusDerAppTragenDenNamenDesMitarbeiters() {
        lager.buche(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, new BigDecimal("5"), null, null, null, null), "Max Mustermann (App)");
        lager.umbuche(new UmbuchungEingabe(pfosten.getId(), haupt, fahrzeug, new BigDecimal("2"), null), "Max Mustermann (App)");

        assertThat(lager.verlauf(pfosten.getId())).hasSize(3).allSatisfy(b -> assertThat(b.benutzer()).isEqualTo("Max Mustermann (App)"));
    }

    @Test
    void einLeererOderZuLangerBenutzernameWirdSauberGespeichert() {
        lager.buche(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ONE, null, null, null, null), "   ");
        lager.buche(new LagerBuchungEingabe("ZUGANG", pfosten.getId(), haupt, BigDecimal.ONE, null, null, null, null), "x".repeat(300));

        assertThat(lager.verlauf(pfosten.getId())).extracting(b -> b.benutzer() == null ? 0 : b.benutzer().length()).contains(0, 120);
    }

    @Test
    void bestandFuerMehrereArtikelLiefertAuchArtikelOhneBuchungenMitNull() {
        buche("ZUGANG", pfosten, haupt, "7");

        List<LagerBestandDto> zeilen = lager.bestandFuer(List.of(pfosten.getId(), rohr.getId()));

        assertThat(zeilen).hasSize(2);
        assertThat(zeilen.stream().filter(z -> z.artikelId().equals(pfosten.getId())).findFirst().orElseThrow().bestand()).isEqualByComparingTo("7");
        assertThat(zeilen.stream().filter(z -> z.artikelId().equals(rohr.getId())).findFirst().orElseThrow().bestand()).isEqualByComparingTo("0");
        assertThat(lager.bestandFuer(List.of())).isEmpty();
    }
}
