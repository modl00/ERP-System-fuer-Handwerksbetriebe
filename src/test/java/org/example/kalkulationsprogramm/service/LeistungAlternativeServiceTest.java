package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LeistungPosition;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/** Alternative Kalkulation an einer Position: Kopie der Leistung bzw. Leistung aus einem Artikel, jeweils temporär (nicht im Katalog). Nur Dummy-Daten. */
class LeistungAlternativeServiceTest {

    private final LeistungRepository leistungen = mock(LeistungRepository.class);
    private final ArtikelRepository artikel = mock(ArtikelRepository.class);
    private final LeistungMapper mapper = mock(LeistungMapper.class);
    private final LeistungAlternativeService service = new LeistungAlternativeService(leistungen, artikel, mapper);

    @BeforeEach
    void setUp() {
        when(leistungen.save(any(Leistung.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toDto(any(Leistung.class))).thenReturn(new LeistungDto());
    }

    private Leistung original() {
        Leistung o = new Leistung();
        o.setBezeichnung("Geländer, Stahl, gestrichen");
        o.setBeschreibung("mit Montage");
        o.setEinheit(Verrechnungseinheit.LAUFENDE_METER);
        o.setPreis(new BigDecimal("180.00"));
        o.setKalkuliert(true);
        o.setZuschlagProzent(new BigDecimal("15.00"));
        o.setLosgroesse(new BigDecimal("10"));
        o.setKategorie(new Produktkategorie());
        o.setBaugruppe(true);
        LeistungPosition arbeit = new LeistungPosition();
        arbeit.setLeistung(o);
        arbeit.setTyp(LeistungPosition.Typ.ARBEIT);
        arbeit.setArbeitsgang(new Arbeitsgang());
        arbeit.setMenge(new BigDecimal("0.5"));
        arbeit.setRuestzeitMinuten(new BigDecimal("15"));
        arbeit.setBearbeitungszeitMinuten(new BigDecimal("30"));
        arbeit.setAnzahlVorgaenge(BigDecimal.ONE);
        arbeit.setSortOrder(0);
        LeistungPosition material = new LeistungPosition();
        material.setLeistung(o);
        material.setTyp(LeistungPosition.Typ.ARTIKEL);
        material.setArtikel(mock(Artikel.class));
        material.setMenge(new BigDecimal("1.1"));
        material.setVerschnittProzent(new BigDecimal("4"));
        material.setSortOrder(1);
        o.getPositionen().add(arbeit);
        o.getPositionen().add(material);
        return o;
    }

    @Test
    void eineAlternativeKopiertDieKalkulationAlsTemporaereLeistungOhneKatalogUndOhneBaugruppe() {
        Leistung o = original();
        when(leistungen.findById(7L)).thenReturn(Optional.of(o));

        service.alternativeZuLeistung(7L);

        ArgumentCaptor<Leistung> gespeichert = ArgumentCaptor.forClass(Leistung.class);
        verify(leistungen).save(gespeichert.capture());
        Leistung k = gespeichert.getValue();
        assertThat(k).isNotSameAs(o);
        assertThat(k.getBezeichnung()).isEqualTo("Geländer, Stahl, gestrichen (Alternative)");
        assertThat(k.isTemporaer()).isTrue();
        assertThat(k.isBaugruppe()).isFalse();
        assertThat(k.getKategorie()).as("ohne Kategorie, bis sie übernommen wird").isNull();
        assertThat(k.getEinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(k.getPreis()).isEqualByComparingTo("180.00");
        assertThat(k.isKalkuliert()).isTrue();
        assertThat(k.getZuschlagProzent()).isEqualByComparingTo("15");
        assertThat(k.getLosgroesse()).isEqualByComparingTo("10");
        assertThat(k.getPositionen()).hasSize(2);
        assertThat(k.getPositionen()).allSatisfy(p -> assertThat(p.getLeistung()).isSameAs(k));
        LeistungPosition arbeit = k.getPositionen().get(0);
        assertThat(arbeit).isNotSameAs(o.getPositionen().get(0));
        assertThat(arbeit.getRuestzeitMinuten()).isEqualByComparingTo("15");
        assertThat(arbeit.getBearbeitungszeitMinuten()).isEqualByComparingTo("30");
        assertThat(k.getPositionen().get(1).getVerschnittProzent()).isEqualByComparingTo("4");
        assertThat(o.isTemporaer()).as("das Original bleibt unberührt").isFalse();
        assertThat(o.getPositionen()).hasSize(2);
    }

    @Test
    void eineUnbekannteLeistungGibt404() {
        when(leistungen.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.alternativeZuLeistung(9L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("nicht gefunden");
        verify(leistungen, never()).save(any());
    }

    @Test
    void ausEinemArtikelEntstehtEineTemporaereLeistungMitDemArtikelAlsMaterial() {
        Artikel a = mock(Artikel.class);
        when(a.getProduktname()).thenReturn("Quadratrohr 40 x 40 x 3");
        when(artikel.findById(5L)).thenReturn(Optional.of(a));

        service.alternativeZuArtikel(5L, null, Verrechnungseinheit.LAUFENDE_METER, new BigDecimal("12.50"));

        ArgumentCaptor<Leistung> gespeichert = ArgumentCaptor.forClass(Leistung.class);
        verify(leistungen).save(gespeichert.capture());
        Leistung k = gespeichert.getValue();
        assertThat(k.getBezeichnung()).isEqualTo("Quadratrohr 40 x 40 x 3");
        assertThat(k.isTemporaer()).isTrue();
        assertThat(k.getEinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(k.getPreis()).isEqualByComparingTo("12.50");
        assertThat(k.getPositionen()).singleElement().satisfies(p -> {
            assertThat(p.getTyp()).isEqualTo(LeistungPosition.Typ.ARTIKEL);
            assertThat(p.getArtikel()).isSameAs(a);
            assertThat(p.getMenge()).isEqualByComparingTo("1");
        });
    }

    @Test
    void einEigenerNameUndFehlendeAngabenWerdenVernuenftigBehandelt() {
        Artikel a = mock(Artikel.class);
        when(artikel.findById(5L)).thenReturn(Optional.of(a));
        service.alternativeZuArtikel(5L, "  Rahmen nach Maß  ", null, new BigDecimal("-3"));

        ArgumentCaptor<Leistung> gespeichert = ArgumentCaptor.forClass(Leistung.class);
        verify(leistungen).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getBezeichnung()).isEqualTo("Rahmen nach Maß");
        assertThat(gespeichert.getValue().getEinheit()).isEqualTo(Verrechnungseinheit.STUECK);
        assertThat(gespeichert.getValue().getPreis()).isEqualByComparingTo("0");
        when(artikel.findById(6L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.alternativeZuArtikel(6L, "x", null, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Artikel");
    }

    @Test
    void einSehrLangerNameWirdGekuerzt() {
        Leistung o = original();
        o.setBezeichnung("X".repeat(250));
        when(leistungen.findById(7L)).thenReturn(Optional.of(o));
        service.alternativeZuLeistung(7L);
        ArgumentCaptor<Leistung> gespeichert = ArgumentCaptor.forClass(Leistung.class);
        verify(leistungen).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getBezeichnung()).hasSize(200);
    }
}
