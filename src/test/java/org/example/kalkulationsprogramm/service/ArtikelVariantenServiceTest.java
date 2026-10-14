package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.GrundartikelEingabe;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Kombination;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Reihen;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArtikelVariantenServiceTest {

    @Mock ArtikelRepository artikel;
    @Mock KategorieRepository kategorien;
    @Mock WerkstoffRepository werkstoffe;

    private ArtikelVariantenService service;
    private ArtikelWerkstoffe grundartikel;

    @BeforeEach
    void setUp() {
        service = new ArtikelVariantenService(artikel, kategorien, werkstoffe, new ProfilRechner());
        grundartikel = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(grundartikel, "id", 1L);
        grundartikel.setGrundartikel(true);
        grundartikel.setKurzbezeichnung("QH");
        grundartikel.setProduktname("Quadratrohr");
        grundartikel.setProfilform(Profilform.QUADRATROHR);
        grundartikel.setProduktlinie("Hohlprofile");
        grundartikel.setVerkaufsaufschlagProzent(new BigDecimal("25.00"));
        grundartikel.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        grundartikel.setStandardlaenge(6000);
        Werkstoff stahl = new Werkstoff();
        stahl.setName("S235JR");
        stahl.setDichte(new BigDecimal("7.850"));
        grundartikel.setWerkstoff(stahl);
    }

    private void gefunden() {
        when(artikel.findById(1L)).thenReturn(Optional.of(grundartikel));
        lenient().when(artikel.findByArtikelnummerIn(any())).thenReturn(List.of());
    }

    private static Reihen reihen(List<String> a, List<String> b) {
        return new Reihen(a, b, null);
    }

    private static Artikel mitNummer(String nummer) {
        Artikel a = new Artikel();
        a.setArtikelnummer(nummer);
        return a;
    }

    private static void abgelehnt(Runnable r, HttpStatus status, String teil) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).contains(teil);
        });
    }

    // ------------------------------------------------------------------ Vorschau

    @Test
    void vorschauBildetAlleKombinationenMitNamenNummernUndGewicht() {
        gefunden();

        List<Kombination> k = service.vorschau(1L, reihen(List.of("20 25", "40"), List.of("1,5, 2")));

        assertThat(k).hasSize(6).allMatch(x -> x.status() == Status.NEU);
        assertThat(k.get(0).name()).isEqualTo("QH 20x20x1,5");
        assertThat(k.get(0).artikelnummer()).isEqualTo("QH-20x20x1,5");
        assertThat(k.get(5).name()).isEqualTo("QH 40x40x2");
        assertThat(k.get(5).kgProMeter().doubleValue()).isBetween(2.28, 2.34);
    }

    @Test
    void vorhandeneArtikelnummerIstSchonDa() {
        gefunden();
        when(artikel.findByArtikelnummerIn(any())).thenReturn(List.of(mitNummer("QH-40x40x2")));

        List<Kombination> k = service.vorschau(1L, reihen(List.of("40"), List.of("2", "3")));

        assertThat(k).extracting(Kombination::status).containsExactly(Status.SCHON_DA, Status.NEU);
    }

    @Test
    void unmoeglicheKombinationenWerdenMitGrundGemeldet() {
        gefunden();

        List<Kombination> k = service.vorschau(1L, reihen(List.of("15", "40"), List.of("4")));

        assertThat(k.get(0).status()).isEqualTo(Status.NICHT_MOEGLICH);
        assertThat(k.get(0).grund()).contains("Wandstärke");
        assertThat(k.get(1).status()).isEqualTo(Status.NEU);
    }

    @Test
    void doppelteWerteInDerReiheFallenWeg() {
        gefunden();

        assertThat(service.vorschau(1L, reihen(List.of("40 40,0 040"), List.of("2")))).hasSize(1);
    }

    @Test
    void rechteckrohrBrauchtZweiMasseProEintrag() {
        grundartikel.setProfilform(Profilform.RECHTECKROHR);
        gefunden();

        List<Kombination> k = service.vorschau(1L, reihen(List.of("60x40 80×40"), List.of("3")));

        assertThat(k).extracting(Kombination::name).containsExactly("QH 60x40x3", "QH 80x40x3");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("60"), List.of("3"))), HttpStatus.BAD_REQUEST, "60x40");
    }

    @Test
    void blechUndTraegerHabenNurEineReihe() {
        grundartikel.setProfilform(Profilform.BLECH);
        grundartikel.setKurzbezeichnung("BL");
        gefunden();
        assertThat(service.vorschau(1L, reihen(null, List.of("2 3")))).extracting(Kombination::name).containsExactly("BL 2", "BL 3");

        grundartikel.setProfilform(Profilform.IPE_PROFIL);
        grundartikel.setKurzbezeichnung("IPE");
        List<Kombination> k = service.vorschau(1L, reihen(List.of("200 205"), null));
        assertThat(k).extracting(Kombination::status).containsExactly(Status.NEU, Status.NICHT_MOEGLICH);
        assertThat(k.get(0).kgProMeter().doubleValue()).isEqualTo(22.4);
    }

    @Test
    void schlechteEingabenWerdenMitKlartextAbgelehnt() {
        gefunden();
        abgelehnt(() -> service.vorschau(1L, reihen(List.of(), List.of("2"))), HttpStatus.BAD_REQUEST, "mindestens einen Wert");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("abc"), List.of("2"))), HttpStatus.BAD_REQUEST, "kein gültiges Maß");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("-5"), List.of("2"))), HttpStatus.BAD_REQUEST, "kein gültiges Maß");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("1e200"), List.of("2"))), HttpStatus.BAD_REQUEST, "kein gültiges Maß");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("40,123"), List.of("2"))), HttpStatus.BAD_REQUEST, "zwei Nachkommastellen");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("20 25 x"), List.of("2"))), HttpStatus.BAD_REQUEST, "kein gültiges Maß");
    }

    @Test
    void zuVieleKombinationenWerdenAbgelehnt() {
        gefunden();
        String a = String.join(" ", java.util.stream.IntStream.rangeClosed(10, 59).mapToObj(String::valueOf).toList());
        String b = String.join(" ", java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(String::valueOf).toList());

        abgelehnt(() -> service.vorschau(1L, reihen(List.of(a), List.of(b))), HttpStatus.BAD_REQUEST, "mehr als 400");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of(a + " 60 61 62 63 64 65 66 67 68 69 70"), List.of("2"))), HttpStatus.BAD_REQUEST, "Zu viele Werte");
    }

    @Test
    void riesigeEingabenWerdenVorDemParsenAbgelehnt() {
        gefunden();
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("1".repeat(2_001)), List.of("2"))), HttpStatus.BAD_REQUEST, "zu lang");
        abgelehnt(() -> service.vorschau(1L, reihen(List.of("1".repeat(25)), List.of("2"))), HttpStatus.BAD_REQUEST, "kein gültiges Maß");
        String viele = String.join(" ", java.util.Collections.nCopies(500, "40"));
        abgelehnt(() -> service.vorschau(1L, reihen(List.of(viele), List.of("2"))), HttpStatus.BAD_REQUEST, "Zu viele Werte");
    }

    @Test
    void nachkommanullenSindErlaubt() {
        gefunden();

        assertThat(service.vorschau(1L, reihen(List.of("40,00"), List.of("2,500")))).extracting(Kombination::name).containsExactly("QH 40x40x2,5");
    }

    @Test
    void kurzbezeichnungWirdGemeinsamGeprueft() {
        assertThat(ArtikelVariantenService.pruefeKurzbezeichnung(" QH-2 ")).isEqualTo("QH-2");
        abgelehnt(() -> ArtikelVariantenService.pruefeKurzbezeichnung(" "), HttpStatus.BAD_REQUEST, "Kurzbezeichnung");
        abgelehnt(() -> ArtikelVariantenService.pruefeKurzbezeichnung("Q/H"), HttpStatus.BAD_REQUEST, "Buchstaben");
    }

    @Test
    void keinGrundartikelOderUnbekannteIdWirdAbgelehnt() {
        when(artikel.findById(2L)).thenReturn(Optional.of(new ArtikelWerkstoffe()));
        when(artikel.findById(3L)).thenReturn(Optional.empty());

        abgelehnt(() -> service.vorschau(2L, reihen(List.of("40"), List.of("2"))), HttpStatus.BAD_REQUEST, "kein Grundartikel");
        abgelehnt(() -> service.vorschau(3L, reihen(List.of("40"), List.of("2"))), HttpStatus.NOT_FOUND, "gibt es nicht");
        abgelehnt(() -> service.vorschau(null, reihen(List.of("40"), List.of("2"))), HttpStatus.NOT_FOUND, "gibt es nicht");
    }

    // ------------------------------------------------------------------ Anlegen

    @Test
    @SuppressWarnings("unchecked")
    void variantenUebernehmenDieAngabenDesGrundartikelsUndRechnenDasGewicht() {
        gefunden();

        service.legeVariantenAn(1L, reihen(List.of("40"), List.of("2")));

        ArgumentCaptor<List<Artikel>> captor = ArgumentCaptor.forClass(List.class);
        verify(artikel).saveAll(captor.capture());
        ArtikelWerkstoffe v = (ArtikelWerkstoffe) captor.getValue().get(0);
        assertThat(v.getProduktname()).isEqualTo("QH 40x40x2");
        assertThat(v.getArtikelnummer()).isEqualTo("QH-40x40x2");
        assertThat(v.getGrundartikelRef()).isSameAs(grundartikel);
        assertThat(v.isGrundartikel()).isFalse();
        assertThat(v.getKurzbezeichnung()).isNull();
        assertThat(v.getProfilform()).isEqualTo(Profilform.QUADRATROHR);
        assertThat(v.getProduktlinie()).isEqualTo("Hohlprofile");
        assertThat(v.getVerkaufsaufschlagProzent()).isEqualByComparingTo("25");
        assertThat(v.getWerkstoff()).isSameAs(grundartikel.getWerkstoff());
        assertThat(v.getStandardlaenge()).isEqualTo(6000);
        assertThat(v.getHoehe()).isEqualByComparingTo("40");
        assertThat(v.getBreite()).isEqualByComparingTo("40");
        assertThat(v.getWandstaerke()).isEqualByComparingTo("2");
        assertThat(v.getMasse().doubleValue()).isBetween(2.28, 2.34);
        assertThat(v.getSuchtext()).isNotBlank();
        assertThat(v.getEan()).isNull();
        assertThat(v.getArtikelpreis()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void abgewaehlteUndVorhandeneVariantenWerdenNichtAngelegt() {
        gefunden();
        when(artikel.findByArtikelnummerIn(any())).thenReturn(List.of(mitNummer("QH-25x25x2")));

        List<Kombination> ergebnis = service.legeVariantenAn(1L, new Reihen(List.of("20 25 30"), List.of("2"), List.of("20|2")));

        // Schlüssel haben die Form "20|2" (Reihe a | Reihe b)
        ArgumentCaptor<List<Artikel>> captor = ArgumentCaptor.forClass(List.class);
        verify(artikel).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Artikel::getProduktname).containsExactly("QH 30x30x2");
        assertThat(ergebnis).hasSize(3);
    }

    @Test
    void unterEinemArchiviertenGrundartikelEntstehenKeineVarianten() {
        grundartikel.setArchiviertAm(java.time.LocalDateTime.now());
        gefunden();

        abgelehnt(() -> service.legeVariantenAn(1L, reihen(List.of("40"), List.of("2"))), HttpStatus.CONFLICT, "im Archiv");
        verify(artikel, org.mockito.Mockito.never()).saveAll(anyList());
    }

    @Test
    void ohneAlleNeuenVariantenGibtEsEineVerstaendlicheMeldung() {
        gefunden();
        when(artikel.findByArtikelnummerIn(any())).thenReturn(List.of(mitNummer("QH-40x40x2")));

        abgelehnt(() -> service.legeVariantenAn(1L, reihen(List.of("40"), List.of("2"))), HttpStatus.BAD_REQUEST, "nichts Neues");
        verify(artikel, org.mockito.Mockito.never()).saveAll(anyList());
    }

    // ------------------------------------------------------------------ Grundartikel anlegen

    private GrundartikelEingabe eingabe(String kurz) {
        return new GrundartikelEingabe(kurz, "Quadratrohr", Profilform.QUADRATROHR, null, null, Verrechnungseinheit.LAUFENDE_METER, new BigDecimal("25"), 6000);
    }

    @Test
    void grundartikelWirdMitKurzbezeichnungAngelegt() {
        when(artikel.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));

        Artikel g = service.legeGrundartikelAn(eingabe(" QH "));

        assertThat(g.isGrundartikel()).isTrue();
        assertThat(g.getKurzbezeichnung()).isEqualTo("QH");
        assertThat(g.getProduktname()).isEqualTo("Quadratrohr");
        assertThat(g.getVerkaufsaufschlagProzent()).isEqualByComparingTo("25.00");
        assertThat(g.getSuchtext()).isNotBlank();
    }

    @Test
    void grundartikelPruefungen() {
        abgelehnt(() -> service.legeGrundartikelAn(eingabe(null)), HttpStatus.BAD_REQUEST, "Kurzbezeichnung");
        abgelehnt(() -> service.legeGrundartikelAn(eingabe("Q H!")), HttpStatus.BAD_REQUEST, "Buchstaben");
        abgelehnt(() -> service.legeGrundartikelAn(eingabe("x".repeat(33))), HttpStatus.BAD_REQUEST, "höchstens 32");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", " ", Profilform.QUADRATROHR, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "Namen");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Z", Profilform.Z_PROFIL, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "keine Varianten");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "keine Varianten");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", Profilform.QUADRATROHR, null, null, null, new BigDecimal("1000"), null)), HttpStatus.BAD_REQUEST, "Aufschlag");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", Profilform.QUADRATROHR, null, null, null, null, 0)), HttpStatus.BAD_REQUEST, "Standardlänge");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", Profilform.QUADRATROHR, 9L, null, null, null, null)), HttpStatus.BAD_REQUEST, "Werkstoff");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", Profilform.QUADRATROHR, null, 9L, null, null, null)), HttpStatus.BAD_REQUEST, "Kategorie");
        abgelehnt(() -> service.legeGrundartikelAn(new GrundartikelEingabe("QH", "Q", Profilform.QUADRATROHR, null, Long.MAX_VALUE, null, null, null)), HttpStatus.BAD_REQUEST, "Kategorie");
    }

    @Test
    void doppelteKurzbezeichnungIstEinKonflikt() {
        when(artikel.existsByGrundartikelTrueAndKurzbezeichnungIgnoreCase("QH")).thenReturn(true);

        abgelehnt(() -> service.legeGrundartikelAn(eingabe("QH")), HttpStatus.CONFLICT, "schon einen Grundartikel");
    }

    // ------------------------------------------------------------------ Liste

    @Test
    void variantenListeIstNachMassenSortiert() {
        gefunden();
        ArtikelWerkstoffe a = variante("QH 40x40x3", "40", "3");
        ArtikelWerkstoffe b = variante("QH 20x20x2", "20", "2");
        ArtikelWerkstoffe c = variante("QH 40x40x2", "40", "2");
        when(artikel.findByGrundartikelRef(grundartikel)).thenReturn(List.of(a, b, c));

        b.setArchiviertAm(java.time.LocalDateTime.now());

        assertThat(service.varianten(1L)).extracting(ArtikelVariantenService.VarianteZeile::produktname)
                .containsExactly("QH 20x20x2", "QH 40x40x2", "QH 40x40x3");
        assertThat(service.varianten(1L)).extracting(ArtikelVariantenService.VarianteZeile::archiviert).containsExactly(true, false, false);
    }

    private static ArtikelWerkstoffe variante(String name, String kante, String wand) {
        ArtikelWerkstoffe v = new ArtikelWerkstoffe();
        v.setProduktname(name);
        v.setHoehe(new BigDecimal(kante));
        v.setBreite(new BigDecimal(kante));
        v.setWandstaerke(new BigDecimal(wand));
        return v;
    }

    @Test
    void vorgabenGibtEsNurFuerBerechenbareFormen() {
        assertThat(service.vorgabe(Profilform.QUADRATROHR).achseB()).isEqualTo("Wandstärke (mm)");
        assertThat(service.vorgabe(Profilform.QUADRATROHR).profilform()).isEqualTo("QUADRATROHR");
        gefunden();
        assertThat(service.rasterVorgabe(1L).form()).isEqualTo("Quadratrohr");
        assertThat(service.vorgabe(Profilform.RECHTECKROHR).aMitZweitemMass()).isTrue();
        assertThat(service.vorgabe(Profilform.IPE_PROFIL).achseB()).isNull();
        assertThat(service.vorgabe(Profilform.BLECH).achseA()).isNull();
        assertThat(service.vorgabe(Profilform.Z_PROFIL)).isNull();
        assertThat(service.vorgabe(null)).isNull();
    }
}
