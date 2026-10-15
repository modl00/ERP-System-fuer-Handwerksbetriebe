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

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.service.KostensatzService.Ergebnis;
import org.example.kalkulationsprogramm.service.KostensatzService.Zeile;
import org.example.kalkulationsprogramm.service.SatzUebernahmeService.UebernahmeFehler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Übernahme des Verrechnungssatzes mit Vorschau (#166, Stufe 3b). Nur Dummy-Daten. */
class SatzUebernahmeServiceTest {

    private final KostensatzService kostensaetze = mock(KostensatzService.class);
    private final ArbeitsgangRepository arbeitsgaenge = mock(ArbeitsgangRepository.class);
    private final ArbeitsgangStundensatzRepository stundensaetze = mock(ArbeitsgangStundensatzRepository.class);
    private final SatzUebernahmeService service = new SatzUebernahmeService(kostensaetze, arbeitsgaenge, stundensaetze);

    private Arbeitsgang schweissen;
    private Arbeitsgang montage;
    private Arbeitsgang alt;

    private static Ergebnis ergebnis(String verrechnungssatz) {
        BigDecimal v = verrechnungssatz == null ? null : new BigDecimal(verrechnungssatz);
        Zeile betrieb = new Zeile("BETRIEB", "Ganzer Betrieb", 3, BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, null, v, null);
        return new Ergebnis(2026, "HOCHRECHNUNG", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null, BigDecimal.ZERO, BigDecimal.ZERO, betrieb, List.of(), List.of(), List.of(),
                new KostensatzService.Gemeinkosten(List.of(), null, false, false, false));
    }

    private static Arbeitsgang ag(long id, String name, String abteilung, boolean archiviert) {
        Arbeitsgang a = new Arbeitsgang();
        a.setId(id);
        a.setBeschreibung(name);
        a.setArchiviert(archiviert);
        if (abteilung != null) {
            Abteilung ab = new Abteilung();
            ab.setName(abteilung);
            a.setAbteilung(ab);
        }
        return a;
    }

    private static ArbeitsgangStundensatz satz(Arbeitsgang a, String wert) {
        ArbeitsgangStundensatz s = new ArbeitsgangStundensatz();
        s.setArbeitsgang(a);
        s.setJahr(2026);
        s.setSatz(new BigDecimal(wert));
        return s;
    }

    @BeforeEach
    void setUp() {
        schweissen = ag(1, "Schweißen", "Werkstatt", false);
        montage = ag(2, "Montage", "Montage", false);
        alt = ag(3, "Altes Verfahren", null, true);
        when(arbeitsgaenge.findAll()).thenReturn(List.of(montage, alt, schweissen));
        when(arbeitsgaenge.findAllById(any())).thenAnswer(i -> {
            var ids = (Iterable<Long>) i.getArgument(0);
            var treffer = new java.util.ArrayList<Arbeitsgang>();
            for (Arbeitsgang a : List.of(schweissen, montage, alt)) {
                for (Long id : ids) {
                    if (a.getId().equals(id)) treffer.add(a);
                }
            }
            return treffer;
        });
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnis("80.00"));
        when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(1L, 2026)).thenReturn(Optional.of(satz(schweissen, "70.00")));
        when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(2L, 2026)).thenReturn(Optional.of(satz(montage, "80.00")));
        when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(3L, 2026)).thenReturn(Optional.empty());
    }

    @Test
    void dieVorschauZeigtAltNeuUndDifferenzJeArbeitsgangOhneArchivierteUndSortiertNachNamen() {
        var v = service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10"));

        assertThat(v.neuerSatz()).isEqualByComparingTo("80.00");
        assertThat(v.zeilen()).extracting(SatzUebernahmeService.Zeile::name).containsExactly("Montage", "Schweißen");
        var s = v.zeilen().get(1);
        assertThat(s.alt()).isEqualByComparingTo("70.00");
        assertThat(s.differenz()).isEqualByComparingTo("10.00");
        assertThat(s.art()).isEqualTo("AENDERT");
        assertThat(s.abteilung()).isEqualTo("Werkstatt");
        assertThat(v.zeilen().get(0).art()).as("gleicher Satz").isEqualTo("GLEICH");
    }

    @Test
    void einArbeitsgangOhneSatzFuerDasJahrIstNeu() {
        when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(1L, 2026)).thenReturn(Optional.empty());
        var z = service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10")).zeilen().stream().filter(x -> x.arbeitsgangId() == 1L).findFirst().orElseThrow();
        assertThat(z.art()).isEqualTo("NEU");
        assertThat(z.alt()).isNull();
        assertThat(z.differenz()).isNull();
    }

    @Test
    void dieVorschauAendertNichts() {
        service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10"));
        verify(stundensaetze, never()).save(any());
    }

    @Test
    void ohneBerechenbarenSatzGibtEsKeineVorschau() {
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnis(null));
        assertThatThrownBy(() -> service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10")))
                .isInstanceOf(UebernahmeFehler.class).hasMessageContaining("kein Verrechnungssatz");
    }

    @Test
    void uebernommenWirdNurWasAngekreuztIstUnveraenderteUndArchivierteBleiben() {
        int n = service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80.00"), List.of(1L, 2L, 3L));

        assertThat(n).as("Schweißen geändert; Montage hat schon 80,00; Archiviertes bleibt").isEqualTo(1);
        org.mockito.ArgumentCaptor<ArbeitsgangStundensatz> c = org.mockito.ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensaetze).save(c.capture());
        assertThat(c.getValue().getArbeitsgang()).isSameAs(schweissen);
        assertThat(c.getValue().getJahr()).isEqualTo(2026);
        assertThat(c.getValue().getSatz()).isEqualByComparingTo("80.00");
    }

    @Test
    void einNichtAngekreuzterArbeitsgangBleibtUnangetastet() {
        service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80.00"), List.of(2L));
        verify(stundensaetze, never()).save(any());
    }

    @Test
    void einNeuerSatzFuerDasJahrWirdAngelegt() {
        when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(1L, 2026)).thenReturn(Optional.empty());
        assertThat(service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80"), List.of(1L))).isEqualTo(1);
        verify(stundensaetze).save(any());
    }

    @Test
    void habenSichDieZahlenSeitDerVorschauGeaendertWirdNichtsGeschrieben() {
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnis("82.50"));
        assertThatThrownBy(() -> service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80.00"), List.of(1L)))
                .isInstanceOf(UebernahmeFehler.class).hasMessageContaining("Vorschau neu laden");
        verify(stundensaetze, never()).save(any());
    }

    @Test
    void ohneAuswahlOderOhneVorschauWirdAbgelehnt() {
        assertThatThrownBy(() -> service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80"), List.of())).isInstanceOf(UebernahmeFehler.class);
        assertThatThrownBy(() -> service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), null, List.of(1L))).isInstanceOf(UebernahmeFehler.class);
        verify(stundensaetze, never()).save(any());
    }

    private static Ergebnis ergebnisMitGruppe(String betrieb, String gruppenName, String gruppenSatz) {
        Zeile b = new Zeile("BETRIEB", "Ganzer Betrieb", 3, BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, null, new BigDecimal(betrieb), null);
        Zeile g = new Zeile("GRUPPE", gruppenName, 1, BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, null, gruppenSatz == null ? null : new BigDecimal(gruppenSatz), null);
        return new Ergebnis(2026, "HOCHRECHNUNG", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null, BigDecimal.ZERO, BigDecimal.ZERO, b, List.of(g), List.of(), List.of(), new KostensatzService.Gemeinkosten(List.of(), null, false, false, false));
    }

    @Test
    void einArbeitsgangMitQualifikationBekommtDenSatzDieserGruppeDerAndereDenBetriebssatz() {
        schweissen.setSatzQuelle(Arbeitsgang.SATZ_VERRECHNUNG);
        schweissen.setSatzGruppe("FACHARBEITER");
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnisMitGruppe("80.00", "Facharbeiter", "92.50"));

        var v = service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10"));

        var s = v.zeilen().stream().filter(z -> z.arbeitsgangId() == 1L).findFirst().orElseThrow();
        assertThat(s.neu()).isEqualByComparingTo("92.50");
        assertThat(s.differenz()).isEqualByComparingTo("22.50");
        assertThat(s.quelle()).isEqualTo("VERRECHNUNG");
        assertThat(s.gruppe()).isEqualTo("FACHARBEITER");
        var m = v.zeilen().stream().filter(z -> z.arbeitsgangId() == 2L).findFirst().orElseThrow();
        assertThat(m.neu()).as("von Hand: Betriebssatz wie bisher").isEqualByComparingTo("80.00");
        assertThat(m.quelle()).isEqualTo("EIGEN");
    }

    @Test
    void ohneSatzFuerDieGruppeStehtKeinSatzDaUndNichtsWirdGeschrieben() {
        schweissen.setSatzQuelle(Arbeitsgang.SATZ_VERRECHNUNG);
        schweissen.setSatzGruppe("MEISTER");
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnisMitGruppe("80.00", "Facharbeiter", "92.50"));

        var z = service.vorschau(2026, null, new BigDecimal("5"), new BigDecimal("10")).zeilen().stream().filter(x -> x.arbeitsgangId() == 1L).findFirst().orElseThrow();
        assertThat(z.art()).isEqualTo("KEIN_SATZ");
        assertThat(z.neu()).isNull();

        assertThat(service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80.00"), List.of(1L))).isZero();
        verify(stundensaetze, never()).save(any());
    }

    @Test
    void dasUebernehmenSchreibtDenGruppensatz() {
        schweissen.setSatzQuelle(Arbeitsgang.SATZ_VERRECHNUNG);
        schweissen.setSatzGruppe("FACHARBEITER");
        when(kostensaetze.berechne(2026, null, new BigDecimal("5"), new BigDecimal("10"))).thenReturn(ergebnisMitGruppe("80.00", "Facharbeiter", "92.50"));

        assertThat(service.uebernehme(2026, null, new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("80.00"), List.of(1L))).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(ArbeitsgangStundensatz.class);
        verify(stundensaetze).save(captor.capture());
        assertThat(captor.getValue().getSatz()).isEqualByComparingTo("92.50");
    }
}
