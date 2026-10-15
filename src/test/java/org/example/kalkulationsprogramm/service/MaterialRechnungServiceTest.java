package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.MaterialRechnungZuordnung;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.Position;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.Rechnung;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.ZeileEingabe;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.MaterialRechnungZuordnungRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Muster GmbH, Stahlhandel Muster). */
@ExtendWith(MockitoExtension.class)
class MaterialRechnungServiceTest {

    @Mock MaterialRechnungZuordnungRepository zuordnungen;
    @Mock LieferantDokumentProjektAnteilRepository anteile;
    @Mock ArtikelInProjektRepository positionen;
    @Mock ProjektRepository projekte;
    @Mock ProjektMapper mapper;
    @Mock SystemSettingsService einstellungen;

    MaterialRechnungService service;
    Projekt projekt;
    Lieferanten stahl;
    LieferantDokument rechnung;
    ArtikelInProjekt rohr;
    ArtikelInProjekt schraube;
    List<MaterialRechnungZuordnung> gespeichert = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MaterialRechnungService(zuordnungen, anteile, positionen, projekte, mapper, einstellungen);
        projekt = new Projekt();
        projekt.setId(7L);
        stahl = new Lieferanten();
        stahl.setId(3L);
        stahl.setLieferantenname("Stahlhandel Muster");
        rechnung = dokument(50L, "RE-2026-77", "1000.00");
        rohr = position(1L, 10L, "Rohr 40x40", Verrechnungseinheit.LAUFENDE_METER, false, false);
        schraube = position(2L, 11L, "Schraube M8", Verrechnungseinheit.STUECK, true, false);
        lenient().when(projekte.findById(7L)).thenReturn(Optional.of(projekt));
        lenient().when(einstellungen.get(anyString(), anyString())).thenAnswer(a -> a.getArgument(1));
        lenient().when(anteile.findByProjektIdEager(7L)).thenReturn(List.of(anteil(rechnung, 50, null)));
        lenient().when(positionen.findByProjekt_Id(7L)).thenReturn(List.of(rohr, schraube));
        lenient().when(positionen.findById(1L)).thenReturn(Optional.of(rohr));
        lenient().when(positionen.findById(2L)).thenReturn(Optional.of(schraube));
        lenient().when(mapper.geplanterGesamtpreis(rohr)).thenReturn(new BigDecimal("300.00"));
        lenient().when(mapper.geplanterGesamtpreis(schraube)).thenReturn(new BigDecimal("200.00"));
        lenient().when(mapper.geplanteMenge(rohr)).thenReturn(new BigDecimal("6.5"));
        lenient().when(mapper.geplanteMenge(schraube)).thenReturn(new BigDecimal("40"));
        lenient().when(zuordnungen.findByProjektId(7L)).thenAnswer(a -> List.copyOf(gespeichert));
        lenient().when(zuordnungen.findByDokument_IdAndProjekt_Id(50L, 7L)).thenAnswer(a -> List.copyOf(gespeichert));
        lenient().when(zuordnungen.saveAll(any())).thenAnswer(a -> {
            Iterable<MaterialRechnungZuordnung> neu = a.getArgument(0);
            gespeichert.clear();
            neu.forEach(gespeichert::add);
            return gespeichert;
        });
    }

    private LieferantDokument dokument(Long id, String nummer, String netto) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setLieferant(stahl);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentNummer(nummer);
        gd.setDokumentDatum(LocalDate.of(2026, 10, 1));
        gd.setBetragNetto(new BigDecimal(netto));
        d.setGeschaeftsdaten(gd);
        return d;
    }

    private LieferantDokumentProjektAnteil anteil(LieferantDokument d, Integer prozent, BigDecimal gespeicherterBetrag) {
        LieferantDokumentProjektAnteil a = new LieferantDokumentProjektAnteil();
        a.setDokument(d);
        a.setProjekt(projekt);
        a.setProzent(prozent);
        a.setBerechneterBetrag(gespeicherterBetrag);
        return a;
    }

    private ArtikelInProjekt position(Long id, Long artikelId, String name, Verrechnungseinheit e, boolean bestellt, boolean ausLager) {
        Artikel a = new Artikel();
        a.setId(artikelId);
        a.setKurzbeschreibung(name);
        a.setVerrechnungseinheit(e);
        ArtikelInProjekt p = new ArtikelInProjekt();
        p.setId(id);
        p.setArtikel(a);
        p.setProjekt(projekt);
        p.setBestellt(bestellt);
        p.setAusLager(ausLager);
        p.setLieferant(stahl);
        return p;
    }

    private MaterialRechnungZuordnung link(ArtikelInProjekt aip, String betrag, String beschreibung) {
        MaterialRechnungZuordnung z = new MaterialRechnungZuordnung();
        z.setDokument(rechnung);
        z.setProjekt(projekt);
        z.setArtikelInProjekt(aip);
        z.setBetrag(new BigDecimal(betrag));
        z.setBeschreibung(beschreibung);
        gespeichert.add(z);
        return z;
    }

    private static ZeileEingabe zeile(Long aip, String betrag) {
        return new ZeileEingabe(aip, new BigDecimal(betrag), null);
    }

    // ---------------------------------------------------------------- Übersicht

    @Test
    @DisplayName("Anteil des Projekts kommt aus dem Nettobetrag mal Prozent (wie in der Projektansicht), ohne Prozent der gespeicherte Betrag")
    void anteilBetrag() {
        assertThat(service.uebersicht(7L).rechnungen().get(0).anteil()).isEqualByComparingTo("500.00");

        when(anteile.findByProjektIdEager(7L)).thenReturn(List.of(anteil(rechnung, null, new BigDecimal("123.45"))));
        assertThat(service.uebersicht(7L).rechnungen().get(0).anteil()).isEqualByComparingTo("123.45");
    }

    @Test
    @DisplayName("Ohne Zuordnung: alle Nicht-Lager-Positionen sind offen, mit Plan-Summe; Lagerware und Positionen ohne Artikel fehlen")
    void offenePositionen() {
        ArtikelInProjekt ausLager = position(3L, 12L, "Winkel", Verrechnungseinheit.STUECK, true, true);
        when(positionen.findByProjekt_Id(7L)).thenReturn(List.of(rohr, schraube, ausLager));

        MaterialAbrechnungDto d = service.uebersicht(7L);

        assertThat(d.positionen()).extracting(Position::bezeichnung).containsExactly("Rohr 40x40", "Schraube M8");
        assertThat(d.offenAnzahl()).isEqualTo(2);
        assertThat(d.offenGeplant()).isEqualByComparingTo("500.00");
        Position p = d.positionen().get(0);
        assertThat(p.geplant()).isEqualByComparingTo("300.00");
        assertThat(p.menge()).isEqualByComparingTo("6.5");
        assertThat(p.einheit()).isEqualTo("m");
        assertThat(p.lieferantName()).isEqualTo("Stahlhandel Muster");
        assertThat(p.ist()).isNull();
        assertThat(d.positionen().get(1).bestellt()).isTrue();
        assertThat(d.schwelleProzent()).isEqualTo(5);
        Rechnung r = d.rechnungen().get(0);
        assertThat(r.dokumentNummer()).isEqualTo("RE-2026-77");
        assertThat(r.rest()).isEqualByComparingTo("500.00");
        assertThat(r.lieferantName()).isEqualTo("Stahlhandel Muster");
    }

    @Test
    @DisplayName("Zugeordnet: Ist-Betrag, Rest der Rechnung, Rechnungsnummer an der Position; abgerechnete stehen unten")
    void zugeordnet() {
        link(rohr, "300.00", null);
        link(null, "50.00", "Kleinteile im Baumarkt");

        MaterialAbrechnungDto d = service.uebersicht(7L);

        assertThat(d.positionen()).extracting(Position::bezeichnung).containsExactly("Schraube M8", "Rohr 40x40");
        Position r = d.positionen().get(1);
        assertThat(r.ist()).isEqualByComparingTo("300.00");
        assertThat(r.rechnungen()).containsExactly("RE-2026-77");
        assertThat(r.abweichung()).isNull();
        assertThat(d.offenAnzahl()).isEqualTo(1);
        assertThat(d.offenGeplant()).isEqualByComparingTo("200.00");
        Rechnung re = d.rechnungen().get(0);
        assertThat(re.zugeordnet()).isEqualByComparingTo("350.00");
        assertThat(re.rest()).isEqualByComparingTo("150.00");
        assertThat(re.zeilen()).extracting(z -> z.beschreibung()).containsExactly(null, "Kleinteile im Baumarkt");
        assertThat(re.zeilen().get(0).bezeichnung()).isEqualTo("Rohr 40x40");
    }

    @Test
    @DisplayName("Abweichung: ab mehr als 5 Prozent markiert (teurer/günstiger), genau 5 Prozent nicht; die Schwelle ist einstellbar")
    void abweichung() {
        link(rohr, "345.00", null);   // +15 %
        link(schraube, "190.00", null);   // -5 % (genau die Schwelle: nicht markiert)

        MaterialAbrechnungDto d = service.uebersicht(7L);

        Position teurer = d.positionen().stream().filter(p -> p.bezeichnung().startsWith("Rohr")).findFirst().orElseThrow();
        Position gleich = d.positionen().stream().filter(p -> p.bezeichnung().startsWith("Schraube")).findFirst().orElseThrow();
        assertThat(teurer.abweichung()).isEqualTo("TEURER");
        assertThat(teurer.abweichungProzent()).isEqualByComparingTo("15.0");
        assertThat(gleich.abweichungProzent()).isEqualByComparingTo("-5.0");
        assertThat(gleich.abweichung()).isNull();

        when(einstellungen.get(anyString(), anyString())).thenReturn("2");
        MaterialAbrechnungDto streng = service.uebersicht(7L);
        assertThat(streng.schwelleProzent()).isEqualTo(2);
        assertThat(streng.positionen().stream().filter(p -> p.bezeichnung().startsWith("Schraube")).findFirst().orElseThrow().abweichung()).isEqualTo("GUENSTIGER");
    }

    @Test
    @DisplayName("Ohne Plan-Preis wird nicht verglichen; Verknüpfungen zu Rechnungen, die dem Projekt nicht mehr zugeordnet sind, zählen nicht")
    void ohnePlanpreisUndVeraltet() {
        when(mapper.geplanterGesamtpreis(rohr)).thenReturn(null);
        link(rohr, "300.00", null);
        LieferantDokument fremd = dokument(99L, "RE-ALT", "10.00");
        MaterialRechnungZuordnung alt = new MaterialRechnungZuordnung();
        alt.setDokument(fremd);
        alt.setProjekt(projekt);
        alt.setArtikelInProjekt(schraube);
        alt.setBetrag(new BigDecimal("10.00"));
        gespeichert.add(alt);

        MaterialAbrechnungDto d = service.uebersicht(7L);

        Position r = d.positionen().stream().filter(p -> p.bezeichnung().startsWith("Rohr")).findFirst().orElseThrow();
        assertThat(r.ist()).isEqualByComparingTo("300.00");
        assertThat(r.abweichungProzent()).isNull();
        assertThat(r.abweichung()).isNull();
        assertThat(d.positionen().stream().filter(p -> p.bezeichnung().startsWith("Schraube")).findFirst().orElseThrow().ist()).isNull();
        assertThat(d.rechnungen()).hasSize(1);
    }

    @Test
    @DisplayName("Unbekanntes Projekt: 404")
    void unbekanntesProjekt() {
        assertThatThrownBy(() -> service.uebersicht(99L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Projekt gibt es nicht");
    }

    // ---------------------------------------------------------------- Speichern

    @Test
    @DisplayName("Speichern: Positionen und Material ohne Plan werden gespeichert, alte Zeilen ersetzt, die Übersicht zeigt den neuen Stand")
    void speichern() {
        link(schraube, "10.00", null);

        MaterialAbrechnungDto d = service.speichern(7L, 50L, List.of(zeile(1L, "300.00"), new ZeileEingabe(null, new BigDecimal("120.50"), " Kleinteile im Baumarkt ")));

        verify(zuordnungen).deleteAll(any());
        assertThat(gespeichert).hasSize(2);
        assertThat(gespeichert.get(0).getArtikelInProjekt()).isSameAs(rohr);
        assertThat(gespeichert.get(1).getArtikelInProjekt()).isNull();
        assertThat(gespeichert.get(1).getBeschreibung()).isEqualTo("Kleinteile im Baumarkt");
        assertThat(d.rechnungen().get(0).rest()).isEqualByComparingTo("79.50");
        assertThat(d.offenAnzahl()).isEqualTo(1);
    }

    @Test
    @DisplayName("Speichern mit leerer Liste hebt die Zuordnung auf")
    void aufheben() {
        link(rohr, "300.00", null);

        MaterialAbrechnungDto d = service.speichern(7L, 50L, List.of());

        assertThat(gespeichert).isEmpty();
        assertThat(d.offenAnzahl()).isEqualTo(2);
    }

    @Test
    @DisplayName("Mehr als der Anteil der Rechnung wird abgelehnt (mit Cent-Toleranz), nichts wird gespeichert")
    void zuViel() {
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(1L, "300.00"), zeile(2L, "200.02"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("500,02 €").hasMessageContaining("500,00 €");
        verify(zuordnungen, never()).saveAll(any());

        service.speichern(7L, 50L, List.of(zeile(1L, "300.00"), zeile(2L, "200.01")));
        assertThat(gespeichert).hasSize(2);
    }

    @Test
    @DisplayName("Ablehnungen: Betrag 0 oder fehlend, Lagerware, fremdes Projekt, doppelte Position, ohne Plan ohne Beschreibung, Rechnung nicht im Projekt")
    void ablehnungen() {
        ArtikelInProjekt lager = position(3L, 12L, "Winkel", Verrechnungseinheit.STUECK, true, true);
        when(positionen.findById(3L)).thenReturn(Optional.of(lager));
        ArtikelInProjekt fremd = position(4L, 13L, "Blech", Verrechnungseinheit.QUADRATMETER, false, false);
        Projekt anderes = new Projekt();
        anderes.setId(8L);
        fremd.setProjekt(anderes);
        when(positionen.findById(4L)).thenReturn(Optional.of(fremd));
        when(positionen.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(1L, "0")))).hasMessageContaining("Betrag größer als 0");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(new ZeileEingabe(1L, null, null)))).hasMessageContaining("Betrag größer als 0");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(3L, "10")))).hasMessageContaining("aus dem Lager");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(4L, "10")))).hasMessageContaining("gehört nicht zu diesem Projekt");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(99L, "10")))).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(zeile(1L, "10"), zeile(1L, "20")))).hasMessageContaining("doppelt");
        assertThatThrownBy(() -> service.speichern(7L, 50L, List.of(new ZeileEingabe(null, new BigDecimal("10"), "ab")))).hasMessageContaining("kurz beschreiben");
        assertThatThrownBy(() -> service.speichern(7L, 51L, List.of(zeile(1L, "10")))).hasMessageContaining("nicht zugeordnet");
        verify(zuordnungen, never()).saveAll(any());
    }

    @Test
    @DisplayName("Gespeichert wird mit Betrag auf zwei Stellen, Zeitpunkt und Benutzer; dieselbe Position darf von mehreren Rechnungen bezahlt werden")
    void betragUndPosition() {
        service.speichern(7L, 50L, List.of(zeile(1L, "100.005")));

        ArgumentCaptor<Iterable<MaterialRechnungZuordnung>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(zuordnungen).saveAll(c.capture());
        MaterialRechnungZuordnung z = c.getValue().iterator().next();
        assertThat(z.getBetrag()).isEqualByComparingTo("100.01");
        assertThat(z.getZugeordnetAm()).isNotNull();
        assertThat(z.getDokument().getId()).isEqualTo(50L);
    }

    // ---------------------------------------------------------------- Einstellung

    @Test
    @DisplayName("Schwelle: Vorgabe 5; ungültige gespeicherte Werte fallen auf 5 zurück; Setzen nur zwischen 0 und 100")
    void schwelle() {
        assertThat(service.schwelleProzent()).isEqualTo(5);
        when(einstellungen.get(anyString(), anyString())).thenReturn("kaputt");
        assertThat(service.schwelleProzent()).isEqualTo(5);
        when(einstellungen.get(anyString(), anyString())).thenReturn("250");
        assertThat(service.schwelleProzent()).isEqualTo(5);
        when(einstellungen.get(anyString(), anyString())).thenReturn("0");
        assertThat(service.schwelleProzent()).isZero();

        assertThat(service.setzeSchwelle(10)).isEqualTo(10);
        verify(einstellungen).save(org.mockito.ArgumentMatchers.eq("material.abweichung.schwelle-prozent"), org.mockito.ArgumentMatchers.eq("10"), any());
        assertThatThrownBy(() -> service.setzeSchwelle(-1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.setzeSchwelle(101)).isInstanceOf(ResponseStatusException.class);
    }
}
