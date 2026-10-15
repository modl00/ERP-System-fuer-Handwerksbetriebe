package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SatzWahlTest {

    ArbeitsgangStundensatzRepository repo = mock(ArbeitsgangStundensatzRepository.class);

    private static ArbeitsgangStundensatz satz(long agId, int jahr, String betrag) {
        Arbeitsgang ag = new Arbeitsgang();
        ag.setId(agId);
        ArbeitsgangStundensatz s = new ArbeitsgangStundensatz();
        s.setArbeitsgang(ag);
        s.setJahr(jahr);
        s.setSatz(new BigDecimal(betrag));
        return s;
    }

    @BeforeEach
    void setUp() {
        when(repo.findTopByArbeitsgangIdAndJahrOrderByIdDesc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(repo.findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(repo.findTopByArbeitsgangIdOrderByJahrDesc(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void dieKetteNimmtErstDasBuchungsjahrDannEinSpaeteresDannDasNeuesteUndSonstNichts() {
        ArbeitsgangStundensatz s2026 = satz(5, 2026, "60.00");
        ArbeitsgangStundensatz s2027 = satz(5, 2027, "65.00");
        ArbeitsgangStundensatz s2024 = satz(5, 2024, "55.00");
        when(repo.findTopByArbeitsgangIdAndJahrOrderByIdDesc(5L, 2026)).thenReturn(Optional.of(s2026));
        assertThat(SatzWahl.finde(repo, 5L, 2026)).contains(s2026);

        // Kein Satz für 2025: der nächste spätere (2026) vor dem neuesten
        when(repo.findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(5L, 2025)).thenReturn(Optional.of(s2026));
        when(repo.findTopByArbeitsgangIdOrderByJahrDesc(5L)).thenReturn(Optional.of(s2027));
        assertThat(SatzWahl.finde(repo, 5L, 2025)).contains(s2026);

        // Nichts Späteres: der neueste überhaupt
        when(repo.findTopByArbeitsgangIdOrderByJahrDesc(5L)).thenReturn(Optional.of(s2024));
        assertThat(SatzWahl.finde(repo, 5L, 2030)).contains(s2024);

        assertThat(SatzWahl.finde(repo, 99L, 2026)).isEmpty();
        assertThat(SatzWahl.finde(repo, null, 2026)).isEmpty();
    }

    @Test
    void anwendenHaeltDenBetragFestUndEineSpaetereAenderungDerSatzzeileLaesstDieBuchungUnberuehrt() {
        ArbeitsgangStundensatz zeile = satz(5, 2026, "60.00");
        Zeitbuchung b = new Zeitbuchung();

        SatzWahl.anwenden(b, zeile);
        zeile.setSatz(new BigDecimal("75.00")); // Rechner-Übernahme oder Pflege ändert die Zeile des Jahres

        assertThat(b.getVerrechnungssatzWert()).isEqualByComparingTo("60.00");
        assertThat(SatzWahl.wertVon(b)).isEqualByComparingTo("60.00");
        assertThat(b.getArbeitsgangStundensatz()).isSameAs(zeile);
    }

    @Test
    void aelterenBuchungenOhneFestenWertGiltDerBetragDerSatzzeileOhneBeidesNull() {
        Zeitbuchung alt = new Zeitbuchung();
        alt.setArbeitsgangStundensatz(satz(5, 2026, "60.00"));
        assertThat(SatzWahl.wertVon(alt)).isEqualByComparingTo("60.00");
        assertThat(SatzWahl.wertVon(new Zeitbuchung())).isNull();
    }

    @Test
    void beimAendernBleibtDerFesteSatzSolangeSichWederArbeitsgangNochJahrAendern() {
        ArbeitsgangStundensatz s2026 = satz(5, 2026, "60.00");
        Zeitbuchung b = new Zeitbuchung();
        SatzWahl.anwenden(b, s2026);

        assertThat(SatzWahl.neuFuerAenderung(repo, b, 5L, 2026)).as("nichts zu tun").isEmpty();

        // Anderer Arbeitsgang: neu wählen
        ArbeitsgangStundensatz anderer = satz(6, 2026, "50.00");
        when(repo.findTopByArbeitsgangIdAndJahrOrderByIdDesc(6L, 2026)).thenReturn(Optional.of(anderer));
        assertThat(SatzWahl.neuFuerAenderung(repo, b, 6L, 2026)).contains(anderer);

        // Datum in ein Jahr verschoben, für das es einen Satz gibt: der wird genommen
        ArbeitsgangStundensatz s2027 = satz(5, 2027, "65.00");
        when(repo.findTopByArbeitsgangIdAndJahrOrderByIdDesc(5L, 2027)).thenReturn(Optional.of(s2027));
        assertThat(SatzWahl.neuFuerAenderung(repo, b, 5L, 2027)).contains(s2027);

        // Datum in ein Jahr ohne eigenen Satz verschoben: der bisherige Satz bleibt (kein Ersatz-Wechsel)
        assertThat(SatzWahl.neuFuerAenderung(repo, b, 5L, 2031)).isEmpty();
    }

    @Test
    void eineBuchungOhneSatzOderOhneFestenWertWirdBeimAendernNeuBewertet() {
        ArbeitsgangStundensatz s2026 = satz(5, 2026, "60.00");
        when(repo.findTopByArbeitsgangIdAndJahrOrderByIdDesc(5L, 2026)).thenReturn(Optional.of(s2026));

        Zeitbuchung ohneSatz = new Zeitbuchung();
        assertThat(SatzWahl.neuFuerAenderung(repo, ohneSatz, 5L, 2026)).contains(s2026);

        Zeitbuchung nurVerweis = new Zeitbuchung();
        nurVerweis.setArbeitsgangStundensatz(s2026);
        assertThat(SatzWahl.neuFuerAenderung(repo, nurVerweis, 5L, 2026)).as("ältere Buchung bekommt jetzt ihren festen Wert").contains(s2026);
    }
}
