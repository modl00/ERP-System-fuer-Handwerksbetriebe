package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.wartung.WartungNummernkreis;
import org.example.kalkulationsprogramm.repository.wartung.WartungNummernkreisRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WartungNummernServiceTest {

    @Mock
    private WartungNummernkreisRepository repository;

    @Mock
    private WartungProtokollRepository protokolle;

    @InjectMocks
    private WartungNummernService service;

    private static WartungNummernkreis kreis(int jahr, int letzterWert) {
        WartungNummernkreis k = new WartungNummernkreis();
        k.setPraefix("WA");
        k.setJahr(jahr);
        k.setLetzterWert(letzterWert);
        return k;
    }

    @Test
    @DisplayName("Die erste Nummer eines frisch angelegten Zählers ist 1001")
    void ersteNummer() {
        WartungNummernkreis k = kreis(2026, 1000);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));

        assertThat(service.naechsteNummer(LocalDate.of(2026, 10, 1))).isEqualTo("WA-2026-1001");
        assertThat(k.getLetzterWert()).isEqualTo(1001);
    }

    @Test
    @DisplayName("Jede weitere Nummer zählt um eins hoch")
    void zaehltHoch() {
        WartungNummernkreis k = kreis(2026, 1041);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));

        assertThat(service.naechsteNummer(LocalDate.of(2026, 10, 1))).isEqualTo("WA-2026-1042");
        assertThat(service.naechsteNummer(LocalDate.of(2026, 10, 2))).isEqualTo("WA-2026-1043");
    }

    @Test
    @DisplayName("Im neuen Jahr beginnt der Zähler wieder bei 1001")
    void jahreswechsel() {
        WartungNummernkreis k = kreis(2026, 1234);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));

        assertThat(service.naechsteNummer(LocalDate.of(2027, 1, 2))).isEqualTo("WA-2027-1001");
        assertThat(k.getJahr()).isEqualTo(2027);
    }

    @Test
    @DisplayName("Geht die Systemuhr zurück, wird nicht zurückgesetzt, damit keine Nummer doppelt vergeben wird")
    void uhrGehtZurueck() {
        WartungNummernkreis k = kreis(2027, 1005);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));

        assertThat(service.naechsteNummer(LocalDate.of(2026, 12, 31))).isEqualTo("WA-2027-1006");
        assertThat(k.getJahr()).isEqualTo(2027);
    }

    @Test
    @DisplayName("Fehlt die Zählerzeile, wird sie angelegt und die Nummer beginnt bei 1001")
    void zeileFehlt() {
        when(repository.findMitSperre("WA")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(WartungNummernkreis.class))).thenAnswer(a -> a.getArgument(0));

        assertThat(service.naechsteNummer(LocalDate.of(2026, 5, 5))).isEqualTo("WA-2026-1001");
        verify(repository).saveAndFlush(any(WartungNummernkreis.class));
    }

    @Test
    @DisplayName("Schon vergebene Nummern (zum Beispiel aus der Übernahme von HandwerkPro) werden übersprungen")
    void ueberspringtVergebeneNummern() {
        WartungNummernkreis k = kreis(2026, 1003);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));
        when(protokolle.existsByNummer("WA-2026-1004")).thenReturn(true);
        when(protokolle.existsByNummer("WA-2026-1005")).thenReturn(true);

        assertThat(service.naechsteNummer(LocalDate.of(2026, 10, 7))).isEqualTo("WA-2026-1006");
        // Der Zähler steht danach bei der vergebenen Nummer: Die nächste Anlage macht bei 1007 weiter, ohne die Lücke noch einmal abzusuchen
        assertThat(k.getLetzterWert()).isEqualTo(1006);
        assertThat(service.naechsteNummer(LocalDate.of(2026, 10, 7))).isEqualTo("WA-2026-1007");
    }

    @Test
    @DisplayName("Sind unglaublich viele Nummern vergeben, gibt es eine klare Fehlermeldung statt einer Endlosschleife")
    void gibtNachVielenVersuchenAuf() {
        WartungNummernkreis k = kreis(2026, 1000);
        when(repository.findMitSperre("WA")).thenReturn(Optional.of(k));
        when(protokolle.existsByNummer(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.naechsteNummer(LocalDate.of(2026, 10, 7)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("keine freie Wartungsnummer");
    }
}
