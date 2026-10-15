package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Metallbau-Vorlagen: jede Formel des Katalogs muss gültig sein, Maße müssen rechnen, ein zweiter Lauf legt nichts doppelt an. Nur Dummy-Daten. */
class MetallbauVorlagenServiceTest {

    private final ArbeitsgangRepository arbeitsgaenge = mock(ArbeitsgangRepository.class);
    private final AbteilungRepository abteilungen = mock(AbteilungRepository.class);
    private final ArtikelRepository artikel = mock(ArtikelRepository.class);
    private final ArtikelService artikelService = mock(ArtikelService.class);
    private final KategorieRepository kategorien = mock(KategorieRepository.class);
    private final WerkstoffRepository werkstoffe = mock(WerkstoffRepository.class);
    private final MetallbauVorlagenService service = new MetallbauVorlagenService(arbeitsgaenge, abteilungen, artikel, artikelService, kategorien, werkstoffe, new ProfilTabelle());

    @BeforeEach
    void setUp() {
        when(arbeitsgaenge.findAll()).thenReturn(List.of());
        when(artikel.alleArtikelnummern()).thenReturn(new HashSet<>());
        when(artikel.alleProduktnamenKlein()).thenReturn(new HashSet<>());
        when(werkstoffe.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    }

    @Test
    void jederArbeitsgangDesKatalogsHatGueltigeFormelUndRechnetMitDenStandardwerten() {
        var katalog = service.arbeitsgangKatalog();

        assertThat(katalog).hasSizeGreaterThanOrEqualTo(20);
        assertThat(katalog).extracting(MetallbauVorlagenService.ArbeitsgangVorlage::name).doesNotHaveDuplicates();
        for (var v : katalog) {
            var zeiten = ArbeitsgangFormeln.berechne(MetallbauVorlagenService.formelDerVorlage(v), Map.of());
            assertThat(zeiten.bearbeitungMinuten()).as(v.name()).isPositive();
        }
    }

    @Test
    void bohrenRechnetNachDerLehrbuchformel() {
        var bohren = service.arbeitsgangKatalog().stream().filter(v -> v.name().startsWith("Bohren")).findFirst().orElseThrow();
        // d=10, Tiefe 8, vc 25, f=0,15 mm/U: n = 25*1000/(pi*10) = 795,8/min; t = (8+3)/(0,15*795,8) = 0,0922 min + 0,4 Nebenzeit
        var zeiten = ArbeitsgangFormeln.berechne(MetallbauVorlagenService.formelDerVorlage(bohren), Map.of());
        assertThat(zeiten.bearbeitungMinuten().doubleValue()).isBetween(0.49, 0.50);
    }

    @Test
    void schweissenRechnetNahtlaengeDurchGeschwindigkeitMitZuschlag() {
        var schweissen = service.arbeitsgangKatalog().stream().filter(v -> v.name().startsWith("Schweißen")).findFirst().orElseThrow();
        // 100 cm / 30 cm/min * 1,4
        var zeiten = ArbeitsgangFormeln.berechne(MetallbauVorlagenService.formelDerVorlage(schweissen), Map.of());
        assertThat(zeiten.bearbeitungMinuten().doubleValue()).isBetween(4.66, 4.67);
    }

    @Test
    void dieVorschauKenntVorhandeneUndSchreibtNichts() {
        Arbeitsgang da = new Arbeitsgang();
        da.setBeschreibung("sägen (band- oder kreissäge)");
        when(arbeitsgaenge.findAll()).thenReturn(List.of(da));

        var v = service.arbeitsgangVorschau();

        assertThat(v.zeilen().stream().filter(z -> z.vorhanden()).count()).isEqualTo(1);
        assertThat(v.hinweis()).contains("Stundensätze");
        verify(arbeitsgaenge, never()).saveAll(any());
    }

    @Test
    void uebernehmenLegtNeueAnUeberspringtVorhandeneUndSetztKeineZeitenVonHand() {
        Abteilung werkstatt = new Abteilung();
        werkstatt.setName("Werkstatt");
        when(abteilungen.findByName("Werkstatt")).thenReturn(Optional.of(werkstatt));
        Arbeitsgang da = new Arbeitsgang();
        da.setBeschreibung("Entgraten");
        when(arbeitsgaenge.findAll()).thenReturn(List.of(da));

        var e = service.arbeitsgaengeUebernehmen(List.of("Entgraten", "Richten"), null);

        assertThat(e.angelegt()).isEqualTo(1);
        assertThat(e.uebersprungen()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(arbeitsgaenge).saveAll(captor.capture());
        Arbeitsgang neu = (Arbeitsgang) captor.getValue().get(0);
        assertThat(neu.getBeschreibung()).isEqualTo("Richten");
        assertThat(neu.getFormelBearbeitung()).isNotBlank();
        assertThat(neu.getFormelFelderJson()).contains("stellen");
    }

    @Test
    void ohneAbteilungScheitertDerLaufBevorGeschriebenWird() {
        when(abteilungen.findByName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.arbeitsgaengeUebernehmen(List.of("Richten"), null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Abteilung");
        verify(arbeitsgaenge, never()).saveAll(any());
    }

    @Test
    void einUnbekannterNameOderEineLeereAuswahlWirdAbgelehnt() {
        assertThatThrownBy(() -> service.arbeitsgaengeUebernehmen(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.arbeitsgaengeUebernehmen(List.of("Gibt es nicht"), null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Katalog");
    }

    @Test
    void alleProfilmasseRechnenUndHabenEindeutigeNummern() {
        var katalog = service.profilKatalog();

        assertThat(katalog).hasSizeGreaterThan(250);
        assertThat(katalog).extracting(MetallbauVorlagenService.ProfilVorlage::nummer).doesNotHaveDuplicates();
        assertThat(katalog).extracting(MetallbauVorlagenService.ProfilVorlage::name).doesNotHaveDuplicates();
        for (var v : katalog) {
            assertThat(v.kg()).as(v.name()).isPositive();
            assertThat(v.flaeche()).as(v.name()).isPositive();
        }
    }

    @Test
    void bekannteProfileHabenDasRichtigeGewicht() {
        var katalog = service.profilKatalog();
        // Rundrohr 48,3 x 3,2: 3,56 kg/m laut Normtabelle (EN 10219 / DIN EN 10220)
        var rohr = katalog.stream().filter(v -> v.name().equals("Rundrohr 48,3 x 3,2")).findFirst().orElseThrow();
        assertThat(rohr.kg().doubleValue()).isBetween(3.54, 3.58);
        // Quadratrohr 40 x 40 x 3: 3,41 kg/m
        var quadrat = katalog.stream().filter(v -> v.name().equals("Quadratrohr 40 x 40 x 3")).findFirst().orElseThrow();
        assertThat(quadrat.kg().doubleValue()).isBetween(3.3, 3.5);
        // Blech 2 mm: 15,7 kg/m²
        var blech = katalog.stream().filter(v -> v.name().equals("Blech 2 mm")).findFirst().orElseThrow();
        assertThat(blech.blech()).isTrue();
        assertThat(blech.kg().doubleValue()).isBetween(15.6, 15.8);
        // IPE 200 aus der Tabelle: 22,4 kg/m
        var ipe = katalog.stream().filter(v -> v.name().equals("IPE 200")).findFirst().orElseThrow();
        assertThat(ipe.herkunft()).isEqualTo("TABELLE");
        assertThat(ipe.kg()).isEqualByComparingTo(new BigDecimal("22.4"));
    }

    @Test
    void profileUebernehmenLegtNurNeueAnUndTragtKeinenPreisEin() {
        Set<String> nummern = new HashSet<>(Set.of("VL-BL-2"));
        when(artikel.alleArtikelnummern()).thenReturn(nummern);
        var kat = new org.example.kalkulationsprogramm.domain.Kategorie();
        kat.setId(7);
        kat.setBeschreibung("Stahlprofile");
        when(kategorien.findAll()).thenReturn(List.of(kat));
        Artikel neu = new Artikel();
        neu.setId(99L);
        when(artikelService.erstelleArtikel(any())).thenReturn(neu);

        var e = service.profileUebernehmen(List.of("VL-RS-12", "VL-BL-2"));

        assertThat(e.angelegt()).isEqualTo(1);
        assertThat(e.uebersprungen()).isEqualTo(1);
        verify(artikelService).erstelleArtikel(org.mockito.ArgumentMatchers.argThat(d -> d.getPreis() == null && d.getLieferantId() == null && "Rundstahl 12".equals(d.getProduktname())));
        verify(artikelService).aktualisiereStammdaten(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.argThat(r -> "VL-RS-12".equals(r.getArtikelnummer()) && r.getKgProMeter() != null));
    }
}
