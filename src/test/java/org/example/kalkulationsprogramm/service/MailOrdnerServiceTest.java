package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.example.kalkulationsprogramm.service.MailOrdnerService.Eintrag;
import org.example.kalkulationsprogramm.service.MailOrdnerService.Wahl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.mail.Message;

/** Ordnerwahl des E-Mail-Centers: Speichern, Datum „seit“ und Lesen der Auswahl. Nur Dummy-Daten. */
class MailOrdnerServiceTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 10, 10);
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final MailOrdnerService service = new MailOrdnerService(einstellungen);

    @BeforeEach
    void setUp() {
        when(einstellungen.get(MailOrdnerService.SCHLUESSEL, "")).thenReturn("");
    }

    @Test
    void einNeuerOrdnerBekommtHeuteAlsDatumUndLiestAlsoNurNeueMails() {
        List<Eintrag> e = service.speichere(List.of(new Wahl("INBOX", "EINGANG", false)), HEUTE);
        assertThat(e).containsExactly(new Eintrag("INBOX", "EINGANG", "2026-10-10"));
    }

    @Test
    void nachladenLaesstDasDatumWegUndHoltAuchAeltereMails() {
        List<Eintrag> e = service.speichere(List.of(new Wahl("Archiv", "EINGANG", true)), HEUTE);
        assertThat(e.get(0).seit()).isNull();
    }

    @Test
    void einBereitsGewaehlterOrdnerBehaeltSeinDatumAuchWennNachladenAngehaktWird() {
        when(einstellungen.get(MailOrdnerService.SCHLUESSEL, "")).thenReturn("[{\"name\":\"INBOX\",\"richtung\":\"EINGANG\",\"seit\":\"2026-09-01\"}]");
        List<Eintrag> e = service.speichere(List.of(new Wahl("INBOX", "GESENDET", true)), HEUTE);
        assertThat(e).containsExactly(new Eintrag("INBOX", "GESENDET", "2026-09-01"));
    }

    @Test
    void gespeichertWirdAlsTextInDenEinstellungenUndDoppelteNamenEntfallen() {
        service.speichere(List.of(new Wahl("INBOX", "EINGANG", false), new Wahl("INBOX", "EINGANG", false), new Wahl("Sent", "GESENDET", false)), HEUTE);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(einstellungen).save(org.mockito.ArgumentMatchers.eq(MailOrdnerService.SCHLUESSEL), text.capture(), anyString());
        assertThat(MailOrdnerService.lies(text.getValue())).extracting(Eintrag::name).containsExactly("INBOX", "Sent");
    }

    @Test
    void ungueltigeEingabenWerdenAbgewiesenOhneZuSpeichern() {
        assertThatThrownBy(() -> service.speichere(null, HEUTE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.speichere(List.of(new Wahl(" ", "EINGANG", false)), HEUTE)).hasMessageContaining("Ordnername");
        assertThatThrownBy(() -> service.speichere(List.of(new Wahl("INBOX", "ALLES", false)), HEUTE)).hasMessageContaining("Eingang");
        assertThatThrownBy(() -> service.speichere(List.of(new Wahl("x".repeat(301), "EINGANG", false)), HEUTE)).isInstanceOf(IllegalArgumentException.class);
        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void eineLeereAuswahlWirdAlsNichtsGespeichertSoDassDasBisherigeVerhaltenGilt() {
        assertThat(service.speichere(List.of(), HEUTE)).isEmpty();
        verify(einstellungen).save(MailOrdnerService.SCHLUESSEL, "", "Ordner, die das E-Mail-Center liest");
    }

    @Test
    void kaputterTextOderUngueltigeEintraegeGebenKeineAuswahl() {
        assertThat(MailOrdnerService.lies(null)).isEmpty();
        assertThat(MailOrdnerService.lies("kein json")).isEmpty();
        assertThat(MailOrdnerService.lies("[{\"name\":\"\",\"richtung\":\"EINGANG\"},{\"name\":\"A\",\"richtung\":\"XYZ\"},null]")).isEmpty();
    }

    @Test
    void aeltereMailsAlsDasDatumWerdenUebersprungenOhneErkennbaresDatumGilltSieAlsNeu() throws Exception {
        Message alt = mock(Message.class);
        when(alt.getReceivedDate()).thenReturn(Date.from(LocalDate.of(2026, 10, 9).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()));
        Message neu = mock(Message.class);
        when(neu.getReceivedDate()).thenReturn(Date.from(LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()));
        Message ohne = mock(Message.class);
        when(ohne.getReceivedDate()).thenReturn(null);
        when(ohne.getSentDate()).thenReturn((Date) null);

        assertThat(EmailImportService.vorSeit(alt, HEUTE)).isTrue();
        assertThat(EmailImportService.vorSeit(neu, HEUTE)).isFalse();
        assertThat(EmailImportService.vorSeit(ohne, HEUTE)).isFalse();
        assertThat(EmailImportService.vorSeit(alt, null)).as("ohne Datum werden alle gelesen").isFalse();
    }
}
