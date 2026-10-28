package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelStammdatenRequest;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArtikelStammdatenServiceTest {

    @Mock ArtikelRepository artikelRepository;
    @Mock KategorieRepository kategorieRepository;
    @Mock WerkstoffRepository werkstoffRepository;
    @InjectMocks ArtikelService service;

    private ArtikelWerkstoffe artikel;

    @BeforeEach
    void setUp() {
        artikel = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(artikel, "id", 7L);
        ReflectionTestUtils.setField(artikel, "version", 3L);
        artikel.setProduktname("Alt");
        artikel.setBeschreibung("<p>Kundentext</p>");
    }

    private void gefunden() {
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
    }

    private void speichernAktiv() {
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ArtikelStammdatenRequest anfrage() {
        ArtikelStammdatenRequest r = new ArtikelStammdatenRequest();
        r.setVersion(3L);
        r.setProduktname("Rundrohr 42,4 x 2");
        return r;
    }

    private static void abgelehnt(Runnable aufruf, HttpStatus status, String teil) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(status);
                    assertThat(e.getReason()).contains(teil);
                });
    }

    @Test
    void ersetztAlleStammdatenUndLaesstTexteUndPreiseUnangetastet() {
        gefunden();
        speichernAktiv();
        Kategorie kategorie = new Kategorie();
        Werkstoff werkstoff = new Werkstoff();
        when(kategorieRepository.findById(5)).thenReturn(Optional.of(kategorie));
        when(werkstoffRepository.findById(2L)).thenReturn(Optional.of(werkstoff));
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktlinie("Geländer");
        r.setArtikelnummer("  ST-RR-042 ");
        r.setEan("4006381333931");
        r.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        r.setKategorieId(5L);
        r.setWerkstoffId(2L);
        r.setProfilform(Profilform.RUNDROHR);
        r.setDurchmesser(new BigDecimal("42.40"));
        r.setWandstaerke(new BigDecimal("2.00"));
        r.setStandardlaenge(6000);

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getProduktname()).isEqualTo("Rundrohr 42,4 x 2");
        assertThat(e.getProduktlinie()).isEqualTo("Geländer");
        assertThat(e.getArtikelnummer()).isEqualTo("ST-RR-042");
        assertThat(e.getEan()).isEqualTo("4006381333931");
        assertThat(e.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(e.getKategorie()).isSameAs(kategorie);
        assertThat(e.getWerkstoff()).isSameAs(werkstoff);
        assertThat(((ArtikelWerkstoffe) e).getDurchmesser()).isEqualByComparingTo("42.4");
        assertThat(((ArtikelWerkstoffe) e).getStandardlaenge()).isEqualTo(6000);
        assertThat(e.getBeschreibung()).isEqualTo("<p>Kundentext</p>");
    }

    @Test
    void gewichtUndOberflaecheJeMeterWerdenGeprueftUndGespeichert() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setKgProMeter(new BigDecimal("1.99271"));
        r.setMantelflaeche(new BigDecimal("0.1332"));

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);
        assertThat(e.getMasse()).isEqualByComparingTo("1.9927");
        assertThat(e.getMantelflaeche()).isEqualByComparingTo("0.1332");

        // Leer = keine Angabe
        r.setKgProMeter(null);
        r.setMantelflaeche(null);
        e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);
        assertThat(e.getMasse()).isNull();
        assertThat(e.getMantelflaeche()).isNull();

        r.setKgProMeter(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Das Gewicht je Meter");
        r.setKgProMeter(null);
        r.setMantelflaeche(new BigDecimal("100001"));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Die Außenfläche je Meter");
    }

    @Test
    void wasFehltWirdGeleert() {
        artikel.setProduktlinie("Alt");
        artikel.setArtikelnummer("A-1");
        artikel.setEan("4006381333931");
        artikel.setDurchmesser(new BigDecimal("10"));
        gefunden();
        speichernAktiv();

        Artikel e = service.aktualisiereStammdaten(7L, anfrage());

        assertThat(e.getProduktlinie()).isNull();
        assertThat(e.getArtikelnummer()).isNull();
        assertThat(e.getEan()).isNull();
        assertThat(((ArtikelWerkstoffe) e).getDurchmesser()).isNull();
        assertThat(e.getKategorie()).isNull();
        assertThat(e.getWerkstoff()).isNull();
    }

    @Test
    void baueSuchtextNeuAuf() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProfilform(Profilform.RUNDROHR);
        r.setArtikelnummer("ST-RR-042");
        r.setEan("4006381333931");
        r.setDurchmesser(new BigDecimal("42.40"));

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getSuchtext())
                .contains("rundrohr", "st-rr-042", "4006381333931", "42.4", "42,4");
    }

    @Test
    void ohneProduktnameWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("   ");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Produktnamen");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void ueberlangeTexteWerdenAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("x".repeat(256));

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Der Produktname darf höchstens 255");

        // So lang ist die Spalte in der Datenbank: mehr als 255 Zeichen müssen vorher mit Klartext abgelehnt werden
        r.setProduktname("ok");
        r.setProdukttext("y".repeat(256));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Die Beschreibung darf höchstens 255");
    }

    @Test
    void produkttextMitGenau255ZeichenIstErlaubt() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProdukttext("y".repeat(255));

        assertThat(service.aktualisiereStammdaten(7L, r).getProdukttext()).hasSize(255);
    }

    @Test
    void ohneStandWirdAbgelehntDamitNichtStillUeberschriebenWird() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(null);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "neu laden");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void zuGrosseOderNegativeKategorieIdGibt400StattServerfehler() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setKategorieId(Integer.MAX_VALUE + 1L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");

        r.setKategorieId(-1L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");
        r.setKategorieId(Long.MAX_VALUE);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");
    }

    @Test
    void sonderzeichenWerdenAlsTextGespeichertNichtAusgefuehrt() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("<script>alert(1)</script>");
        r.setProduktlinie("'; DROP TABLE artikel; --");

        Artikel e = service.aktualisiereStammdaten(7L, r);

        // Der Wert bleibt Text; Ausgabe und Abfragen sind parametrisiert bzw. von React escaped
        assertThat(e.getProduktname()).isEqualTo("<script>alert(1)</script>");
        assertThat(e.getProduktlinie()).isEqualTo("'; DROP TABLE artikel; --");
    }

    @Test
    void doppelteArtikelnummerGibt409() {
        gefunden();
        // Ein anderer Artikel hat "a-1" - Gross-/Kleinschreibung zaehlt nicht.
        when(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("A-1", 7L)).thenReturn(true);
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("A-1");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "Artikelnummer A-1");
    }

    @Test
    void eigeneArtikelnummerUndEanDuerfenStehenBleiben() {
        gefunden();
        speichernAktiv();
        when(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("A-1", 7L)).thenReturn(false);
        when(artikelRepository.findByEan("4006381333931")).thenReturn(Optional.of(artikel));
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("A-1");
        r.setEan("4006381333931");

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getArtikelnummer()).isEqualTo("A-1");
        assertThat(e.getEan()).isEqualTo("4006381333931");
    }

    @Test
    void eanMitFalscherPruefzifferGibt400() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setEan("4006381333932");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Prüfziffer");
    }

    @Test
    void doppelteEanGibt409() {
        gefunden();
        Artikel anderer = new Artikel();
        anderer.setId(8L);
        when(artikelRepository.findByEan("4006381333931")).thenReturn(Optional.of(anderer));
        ArtikelStammdatenRequest r = anfrage();
        r.setEan("4006381333931");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "EAN 4006381333931");
    }

    @Test
    void veralteterStandGibt409() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(2L);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "neu laden");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void unbekannteKategorieUndWerkstoffGeben400() {
        gefunden();
        when(kategorieRepository.findById(99)).thenReturn(Optional.empty());
        ArtikelStammdatenRequest r = anfrage();
        r.setKategorieId(99L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");

        r.setKategorieId(null);
        r.setWerkstoffId(99L);
        when(werkstoffRepository.findById(99L)).thenReturn(Optional.empty());
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Werkstoff");
    }

    @Test
    void masseUndLaengeHabenGrenzen() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setDurchmesser(new BigDecimal("-1"));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Der Durchmesser");

        r.setDurchmesser(null);
        r.setHoehe(new BigDecimal("100001"));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Die Höhe");

        r.setHoehe(null);
        r.setStandardlaenge(0);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Standardlänge");
        r.setStandardlaenge(30_001);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Standardlänge");
    }

    @Test
    void negativeVerpackungseinheitWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVerpackungseinheit(-1L);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Verpackungseinheit");
    }

    @Test
    void artikelOhneHalbzeugDatenIgnorierenMasse() {
        Artikel einfach = new Artikel();
        ReflectionTestUtils.setField(einfach, "id", 9L);
        einfach.setProduktname("Schraube");
        when(artikelRepository.findById(9L)).thenReturn(Optional.of(einfach));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(null);
        r.setDurchmesser(new BigDecimal("8"));

        Artikel e = service.aktualisiereStammdaten(9L, r);

        assertThat(e).isNotInstanceOf(ArtikelWerkstoffe.class);
        assertThat(e.getProduktname()).isEqualTo("Rundrohr 42,4 x 2");
    }

    @Test
    void unbekannteIdGibt404() {
        when(artikelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aktualisiereStammdaten(999L, anfrage())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void eignungFuerVerzinkenUndPulvernLaesstSichProArtikelFestlegenOderVomWerkstoffUebernehmen() {
        gefunden();
        speichernAktiv();
        Werkstoff stahl = new Werkstoff();
        stahl.setVerzinkungsgeeignet(true);
        stahl.setPulverbeschichtungsgeeignet(false);
        when(werkstoffRepository.findById(2L)).thenReturn(Optional.of(stahl));
        ArtikelStammdatenRequest r = anfrage();
        r.setWerkstoffId(2L);

        // Ohne Angabe gilt der Werkstoff (Verzinken ja, Pulvern nein)
        Artikel e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.getVerzinkungsgeeignet()).isNull();
        assertThat(e.getPulverbeschichtungsgeeignet()).isNull();
        assertThat(e.istVerzinkungsgeeignet()).isTrue();
        assertThat(e.istPulverbeschichtungsgeeignet()).isFalse();

        // Für diesen Artikel festgelegt: überstimmt den Werkstoff, in beide Richtungen
        r.setVerzinkungsgeeignet(false);
        r.setPulverbeschichtungsgeeignet(true);
        e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.istVerzinkungsgeeignet()).isFalse();
        assertThat(e.istPulverbeschichtungsgeeignet()).isTrue();

        // Wieder leer = zurück zum Werkstoff
        r.setVerzinkungsgeeignet(null);
        r.setPulverbeschichtungsgeeignet(null);
        e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.istVerzinkungsgeeignet()).isTrue();
        assertThat(e.istPulverbeschichtungsgeeignet()).isFalse();
    }

    // --- Tests retained from our version (weight/area/formula features) ---

    @Test
    void suchtextWirdNeuAufgebautMitSchreibweisenUndSynonymen() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("ST-RR-042");
        r.setEan("4006381333931");
        r.setProfilform(Profilform.RUNDROHR);
        r.setDurchmesser(new BigDecimal("42.40"));

        String suchtext = service.aktualisiereStammdaten(7L, r).getSuchtext();

        assertThat(suchtext).contains("rundrohr rohr rund", "st-rr-042", "4006381333931", "42.4", "42,4");
        assertThat(suchtext).doesNotContain(" alt ");
        assertThat(suchtext).isEqualTo(suchtext.toLowerCase());
    }

    @Test
    void ohneProduktnameKeinSpeichern() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("   ");

        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Produktnamen");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void ueberlangeUndGefaehrlicheEingabenWerdenBegrenztOderNurAlsTextGespeichert() {
        gefunden();
        ArtikelStammdatenRequest lang = anfrage();
        lang.setProduktname("x".repeat(256));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, lang)).hasMessageContaining("höchstens 255");

        ArtikelStammdatenRequest sehrLang = anfrage();
        sehrLang.setProdukttext("x".repeat(10_001));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, sehrLang)).hasMessageContaining("höchstens 10000");

        speichernAktiv();
        ArtikelStammdatenRequest inj = anfrage();
        inj.setProduktname("'; DROP TABLE artikel; -- <script>alert(1)</script>");
        assertThat(service.aktualisiereStammdaten(7L, inj).getProduktname()).contains("DROP TABLE");
    }

    @Test
    void doppelteArtikelnummerWirdMit409AbgelehntAberDerArtikelDarfSeineEigeneBehalten() {
        gefunden();
        Artikel anderer = new Artikel();
        ReflectionTestUtils.setField(anderer, "id", 8L);
        when(artikelRepository.findByArtikelnummer("A-1")).thenReturn(Optional.of(anderer));
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("A-1");

        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("A-1");

        when(artikelRepository.findByArtikelnummer("A-2")).thenReturn(Optional.of(artikel));
        speichernAktiv();
        r.setArtikelnummer("A-2");
        assertThat(service.aktualisiereStammdaten(7L, r).getArtikelnummer()).isEqualTo("A-2");
    }

    @Test
    void eanMitFalscherPruefzifferOderDoppeltWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest falsch = anfrage();
        falsch.setEan("4006381333932");
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, falsch)).hasMessageContaining("Prüfziffer");

        Artikel anderer = new Artikel();
        ReflectionTestUtils.setField(anderer, "id", 8L);
        when(artikelRepository.findByEan("4006381333931")).thenReturn(Optional.of(anderer));
        ArtikelStammdatenRequest doppelt = anfrage();
        doppelt.setEan("4006381333931");
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, doppelt))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("anderen Artikel");
    }

    @Test
    void einVeralteterStandWirdMit409AbgelehntOhneZuSpeichern() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(2L);

        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("neu laden");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void unbekannteKategorieUndUngueltigeMasseWerdenAbgelehnt() {
        gefunden();
        when(kategorieRepository.findById(99)).thenReturn(Optional.empty());
        ArtikelStammdatenRequest k = anfrage();
        k.setKategorieId(99L);
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, k)).hasMessageContaining("Kategorie");

        ArtikelStammdatenRequest m = anfrage();
        m.setDurchmesser(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, m)).hasMessageContaining("Durchmesser");
        m.setDurchmesser(null);
        m.setStandardlaenge(0);
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, m)).hasMessageContaining("Standardlänge");
    }

    @Test
    void unbekannteIdUndExtremeIdsGebenNotFound() {
        when(artikelRepository.findById(0L)).thenReturn(Optional.empty());
        when(artikelRepository.findById(-1L)).thenReturn(Optional.empty());
        when(artikelRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        for (long id : new long[] {0L, -1L, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> service.aktualisiereStammdaten(id, anfrage())).isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void einArtikelOhneHalbzeugIgnoriertDieAbmessungen() {
        Artikel schlicht = new Artikel();
        ReflectionTestUtils.setField(schlicht, "id", 9L);
        when(artikelRepository.findById(9L)).thenReturn(Optional.of(schlicht));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(null);
        r.setDurchmesser(new BigDecimal("10"));

        assertThat(service.aktualisiereStammdaten(9L, r).getProduktname()).isEqualTo("Rundrohr 42,4 x 2");
    }

    @Test
    void neuAngelegterArtikelBekommtEanUndSuchtext() {
        speichernAktiv();
        org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto dto = new org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto();
        dto.setProduktname("Schraube M8");
        dto.setEan("4006381333931");

        Artikel neu = service.erstelleArtikel(dto);

        assertThat(neu.getEan()).isEqualTo("4006381333931");
        assertThat(neu.getSuchtext()).contains("schraube m8", "4006381333931");
    }

    // ── Gewicht und Fläche eindeutig (#228) ──

    @Test
    void einBlechSpeichertNurWerteJeQuadratmeterUndVergisstAlteMeterwerte() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProfilform(Profilform.BLECH);
        r.setKgProMeter(new BigDecimal("1.5"));
        r.setMantelflaeche(new BigDecimal("0.2"));
        r.setKgProQm(new BigDecimal("78.5"));
        r.setFlaecheJeQm(new BigDecimal("2"));

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);

        assertThat(e.getMassePerQm()).isEqualByComparingTo("78.5");
        assertThat(e.getFlaecheJeQm()).isEqualByComparingTo("2");
        assertThat(e.getMasse()).as("ein Blech wird nicht nach Meter gerechnet").isNull();
        assertThat(e.getMantelflaeche()).isNull();
    }

    @Test
    void meterwareUndBlechZugleichIstUneindeutigUndWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProfilform(Profilform.RUNDROHR);
        r.setKgProMeter(new BigDecimal("2"));
        r.setKgProQm(new BigDecimal("40"));

        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("entweder das Gewicht je Meter oder das Gewicht je m²");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void stueckwareSpeichertGewichtUndAussenflaecheJeStueck() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        r.setKgJeStueck(new BigDecimal("2.5"));
        r.setM2JeStueck(new BigDecimal("0.3"));

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);

        assertThat(e.getKgJeStueck()).isEqualByComparingTo("2.5");
        assertThat(e.getM2JeStueck()).isEqualByComparingTo("0.3");
        r.setKgJeStueck(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Das Gewicht je Stück");
    }

    @Test
    void dieFlaecheJeQuadratmeterBlechIstBegrenzt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProfilform(Profilform.BLECH);
        r.setKgProQm(new BigDecimal("40"));
        r.setFlaecheJeQm(new BigDecimal("11"));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("höchstens 10");
    }

    @Test
    void dieHerkunftDesGewichtsWirdMitDerDichteGemerktNurWennBerechnet() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setKgProMeter(new BigDecimal("2"));
        r.setGewichtHerkunft("berechnet");
        r.setGewichtDichte(new BigDecimal("7.85"));

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);
        assertThat(e.getGewichtHerkunft()).isEqualTo("BERECHNET");
        assertThat(e.getGewichtDichte()).isEqualByComparingTo("7.85");

        r.setGewichtHerkunft("HAND");
        e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);
        assertThat(e.getGewichtHerkunft()).isEqualTo("HAND");
        assertThat(e.getGewichtDichte()).as("von Hand eingetragen: keine Dichte merken").isNull();

        // Ohne Gewicht gibt es auch keine Herkunft
        r.setKgProMeter(null);
        e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);
        assertThat(e.getGewichtHerkunft()).isNull();
    }

    @Test
    void unbekannteHerkunftUndUnsinnigeDichteWerdenAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setKgProMeter(new BigDecimal("2"));
        r.setGewichtHerkunft("<script>alert(1)</script>");
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Herkunft");

        r.setGewichtHerkunft("BERECHNET");
        r.setGewichtDichte(new BigDecimal("31"));
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Dichte");
        r.setGewichtDichte(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("Dichte");
    }

    @Test
    void einImProgrammAngelegterArtikelBekommtBeiGewichtsangabenDieZeileFuerHalbzeugDaten() {
        Artikel einfach = new Artikel();
        ReflectionTestUtils.setField(einfach, "id", 8L);
        ReflectionTestUtils.setField(einfach, "version", 1L);
        einfach.setProduktname("Konsole");
        ArtikelWerkstoffe umgewandelt = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(umgewandelt, "id", 8L);
        ReflectionTestUtils.setField(umgewandelt, "version", 1L);
        umgewandelt.setProduktname("Konsole");
        when(artikelRepository.findById(8L)).thenReturn(Optional.of(einfach), Optional.of(umgewandelt));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(1L);
        r.setKgJeStueck(new BigDecimal("2.5"));

        Artikel e = service.aktualisiereStammdaten(8L, r);

        verify(artikelRepository).legeWerkstoffDatenAn(8L);
        assertThat(e).isInstanceOf(ArtikelWerkstoffe.class);
        assertThat(((ArtikelWerkstoffe) e).getKgJeStueck()).isEqualByComparingTo("2.5");
    }

    @Test
    void einEinfacherArtikelOhneGewichtsangabenBleibtEinfach() {
        Artikel einfach = new Artikel();
        ReflectionTestUtils.setField(einfach, "id", 8L);
        ReflectionTestUtils.setField(einfach, "version", 1L);
        when(artikelRepository.findById(8L)).thenReturn(Optional.of(einfach));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(1L);

        Artikel e = service.aktualisiereStammdaten(8L, r);

        verify(artikelRepository, never()).legeWerkstoffDatenAn(any());
        assertThat(e).isNotInstanceOf(ArtikelWerkstoffe.class);
    }

    // ── Zuschnitt-Formel (#228, Schritt 2) ──

    private static java.util.List<org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld> felder() {
        return java.util.List.of(
                new org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld("L", "Länge", "mm", new BigDecimal("1000")),
                new org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld("B", "Breite", "mm", new BigDecimal("500")));
    }

    @Test
    void eineZuschnittFormelWirdGeprueftUndMitFeldernGespeichert() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setFormelFelder(felder());
        r.setFormelGewicht("  L * B / 1000000 * 10 * dichte ");
        r.setFormelFlaeche("L * B / 1000000 * 2");

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, r);

        assertThat(e.getFormelGewicht()).isEqualTo("L * B / 1000000 * 10 * dichte");
        assertThat(e.getFormelFlaeche()).isEqualTo("L * B / 1000000 * 2");
        assertThat(e.getFormelFelderJson()).contains("\"name\":\"L\"").contains("Länge");
    }

    @Test
    void ohneFormelnWerdenAltFormelnUndFelderGeleert() {
        gefunden();
        speichernAktiv();
        artikel.setFormelGewicht("L");
        artikel.setFormelFelderJson("[]");

        ArtikelWerkstoffe e = (ArtikelWerkstoffe) service.aktualisiereStammdaten(7L, anfrage());

        assertThat(e.getFormelGewicht()).isNull();
        assertThat(e.getFormelFlaeche()).isNull();
        assertThat(e.getFormelFelderJson()).isNull();
    }

    @Test
    void eineFehlerhafteFormelWirdMitKlartextAbgelehntUndNichtGespeichert() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setFormelFelder(felder());
        r.setFormelGewicht("L * Unbekannt");

        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Formel:");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void einEingebauterNameAlsFeldWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setFormelFelder(java.util.List.of(new org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld("dichte", "Dichte", "", null)));
        r.setFormelGewicht("dichte");
        assertThatThrownBy(() -> service.aktualisiereStammdaten(7L, r)).hasMessageContaining("reserviert");
    }

    @Test
    void auchEineFormelAlleinLegtBeimEinfachenArtikelDieHalbzeugZeileAn() {
        Artikel einfach = new Artikel();
        ReflectionTestUtils.setField(einfach, "id", 8L);
        ReflectionTestUtils.setField(einfach, "version", 1L);
        ArtikelWerkstoffe umgewandelt = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(umgewandelt, "id", 8L);
        ReflectionTestUtils.setField(umgewandelt, "version", 1L);
        when(artikelRepository.findById(8L)).thenReturn(Optional.of(einfach), Optional.of(umgewandelt));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(1L);
        r.setFormelFelder(felder());
        r.setFormelGewicht("L * B / 1000000");

        Artikel e = service.aktualisiereStammdaten(8L, r);

        verify(artikelRepository).legeWerkstoffDatenAn(8L);
        assertThat(((ArtikelWerkstoffe) e).getFormelGewicht()).isEqualTo("L * B / 1000000");
    }
}
