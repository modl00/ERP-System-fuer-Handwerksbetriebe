package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Gewichte im Artikelstamm aus den Maßen ergänzen: Vorschau schreibt nichts, Übernehmen überschreibt nie. Nur Dummy-Daten. */
class ArtikelGewichtErgaenzenServiceTest {

    private final ArtikelRepository repo = mock(ArtikelRepository.class);
    private final ArtikelGewichtErgaenzenService service = new ArtikelGewichtErgaenzenService(repo);
    private ArtikelWerkstoffe rohr;
    private ArtikelWerkstoffe ohneMasse;
    private ArtikelWerkstoffe blech;
    private ArtikelWerkstoffe riffel;

    private static ArtikelWerkstoffe artikel(long id, String name, Profilform form) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setId(id);
        a.setProduktname(name);
        a.setProfilform(form);
        return a;
    }

    @BeforeEach
    void setUp() {
        rohr = artikel(1, "Rundrohr 42,4 x 2", Profilform.RUNDROHR);
        rohr.setDurchmesser(new BigDecimal("42.4"));
        rohr.setWandstaerke(new BigDecimal("2"));
        ohneMasse = artikel(2, "Rundrohr ohne Maße", Profilform.RUNDROHR);
        blech = artikel(3, "Blech 3 mm", Profilform.BLECH);
        blech.setWandstaerke(new BigDecimal("3"));
        riffel = artikel(4, "Riffelblech", Profilform.RIFFELBLECH);
        when(repo.zaehleOhneGewicht()).thenReturn(4L);
        when(repo.findeOhneGewicht(any())).thenReturn(List.of(ohneMasse, rohr, blech, riffel));
        for (ArtikelWerkstoffe a : List.of(rohr, ohneMasse, blech, riffel)) {
            when(repo.findById(a.getId())).thenReturn(Optional.of(a));
        }
    }

    @Test
    void dieVorschauRechnetBerechenbareUndNenntFehlendeMasseAnderen_ohneZuSchreiben() {
        var v = service.vorschau();

        assertThat(v.gesamt()).isEqualTo(4);
        assertThat(v.zeilen()).extracting(ArtikelGewichtErgaenzenService.Zeile::status).containsExactly("BERECHENBAR", "BERECHENBAR", "MASSE_FEHLEN", "MASSE_FEHLEN");
        var r = v.zeilen().stream().filter(z -> z.id() == 1L).findFirst().orElseThrow();
        assertThat(r.kg()).as("Rundrohr 42,4 x 2 aus Stahl ca. 1,99 kg/m").isBetween(new BigDecimal("1.99"), new BigDecimal("1.995"));
        assertThat(r.einheit()).isEqualTo("je Meter");
        var b = v.zeilen().stream().filter(z -> z.id() == 3L).findFirst().orElseThrow();
        assertThat(b.kg()).as("Blech 3 mm: 7,85 × 3 = 23,55 kg/m²").isEqualByComparingTo("23.55");
        assertThat(b.einheit()).isEqualTo("je m²");
        assertThat(v.zeilen().stream().filter(z -> z.id() == 2L).findFirst().orElseThrow().hinweis()).contains("Durchmesser");
        assertThat(v.zeilen().stream().filter(z -> z.id() == 4L).findFirst().orElseThrow().hinweis()).contains("Datenblatt");
        verify(repo, never()).save(any());
    }

    @Test
    void uebernommenWirdBeiMeterwareUndBlechAnDerRichtigenStelleMitHerkunftUndDichte() {
        var e = service.uebernehme(List.of(1L, 3L));

        assertThat(e.ergaenzt()).isEqualTo(2);
        assertThat(rohr.getMasse()).isBetween(new BigDecimal("1.99"), new BigDecimal("1.995"));
        assertThat(rohr.getMantelflaeche()).isNotNull();
        assertThat(rohr.getMassePerQm()).isNull();
        assertThat(rohr.getGewichtHerkunft()).isEqualTo("BERECHNET");
        assertThat(rohr.getGewichtDichte()).isEqualByComparingTo("7.85");
        assertThat(blech.getMassePerQm()).isEqualByComparingTo("23.55");
        assertThat(blech.getMasse()).isNull();
        verify(repo).save(rohr);
        verify(repo).save(blech);
    }

    @Test
    void einArtikelMitInzwischenVorhandenemGewichtWirdNieUeberschrieben() {
        rohr.setMasse(new BigDecimal("2.5"));
        var e = service.uebernehme(List.of(1L));
        assertThat(e.ergaenzt()).isZero();
        assertThat(e.uebersprungen()).isEqualTo(1);
        assertThat(rohr.getMasse()).isEqualByComparingTo("2.5");
        verify(repo, never()).save(any());
    }

    @Test
    void nichtBerechenbareUnbekannteUndDoppelteAuswahlenWerdenUebersprungenOderEinmalGezaehlt() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        var e = service.uebernehme(List.of(2L, 4L, 99L, 1L, 1L));
        assertThat(e.ergaenzt()).isEqualTo(1);
        assertThat(e.uebersprungen()).isEqualTo(3);
    }

    @Test
    void leereOderZuGrosseAuswahlWirdAbgewiesen() {
        assertThatThrownBy(() -> service.uebernehme(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.uebernehme(java.util.stream.LongStream.rangeClosed(1, 501).boxed().toList())).hasMessageContaining("höchstens 500");
    }

    @Test
    void eineAbweichendeDichteDesWerkstoffsGiltUndWirdVermerkt() {
        var w = new org.example.kalkulationsprogramm.domain.Werkstoff();
        w.setDichte(new BigDecimal("2.7"));
        rohr.setWerkstoff(w);
        service.uebernehme(List.of(1L));
        assertThat(rohr.getGewichtDichte()).isEqualByComparingTo("2.7");
        assertThat(rohr.getMasse()).as("Aluminium ist leichter als Stahl").isLessThan(new BigDecimal("0.7"));
    }
}
