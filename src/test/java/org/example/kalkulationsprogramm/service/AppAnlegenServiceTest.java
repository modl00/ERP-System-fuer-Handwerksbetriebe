package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.controller.KundeDuplikatException;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.AppAnlegenService.AnfrageEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Max Mustermann). */
class AppAnlegenServiceTest {

    private FunktionsRechteService rechte;
    private KundeRepository kunden;
    private KundennummerService nummern;
    private KundeDuplikatService duplikate;
    private KundeAnsprechpartnerService ansprechpartner;
    private AnfrageService anfragen;
    private ApplicationEventPublisher events;
    private AppAnlegenService service;

    @BeforeEach
    void setUp() {
        rechte = mock(FunktionsRechteService.class);
        kunden = mock(KundeRepository.class);
        nummern = mock(KundennummerService.class);
        duplikate = mock(KundeDuplikatService.class);
        ansprechpartner = mock(KundeAnsprechpartnerService.class);
        anfragen = mock(AnfrageService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AppAnlegenService(rechte, kunden, nummern, duplikate, ansprechpartner, anfragen, events);
        when(nummern.reserviereNaechsteKundennummer()).thenReturn("K-2001");
        when(duplikate.findeDuplikate(any(), any(), any(), any(), any(), any())).thenReturn(new KundeDuplikatResponseDto(List.of(), false));
        when(kunden.save(any(Kunde.class))).thenAnswer(a -> {
            Kunde k = a.getArgument(0);
            k.setId(42L);
            return k;
        });
    }

    private KundeCreateRequestDto kunde() {
        KundeCreateRequestDto d = new KundeCreateRequestDto();
        d.setName("  Max Mustermann  ");
        d.setAnsprechspartner("Erika Mustermann");
        d.setStrasse("Musterweg 1");
        d.setPlz("12345");
        d.setOrt("Musterstadt");
        d.setTelefon("0123 456");
        d.setKundenEmails(List.of(" Max@Example.ORG ", "", "max@example.org"));
        d.setKundennummer("K-9999");
        return d;
    }

    @Test
    @DisplayName("Kunde anlegen: Kundennummer vergibt der Server (nicht die App), E-Mails werden bereinigt, Ansprechpartner und E-Mail-Abgleich laufen wie am PC")
    void kundeAnlegen() {
        var ergebnis = service.legeKundeAn("tok", kunde(), false);

        verify(rechte).verlangeFuerToken("tok", Funktion.KUNDEN, FunktionsAktion.ANLEGEN);
        ArgumentCaptor<Kunde> k = ArgumentCaptor.forClass(Kunde.class);
        verify(kunden).save(k.capture());
        assertThat(k.getValue().getKundennummer()).isEqualTo("K-2001");
        assertThat(k.getValue().getName()).isEqualTo("Max Mustermann");
        assertThat(k.getValue().getKundenEmails()).containsExactly("max@example.org");
        verify(ansprechpartner).ausKundenformular(any(Kunde.class), org.mockito.ArgumentMatchers.eq("Erika Mustermann"));
        verify(events).publishEvent(any(EmailAddressChangedEvent.class));
        assertThat(ergebnis.id()).isEqualTo(42L);
        assertThat(ergebnis.kundennummer()).isEqualTo("K-2001");
        assertThat(ergebnis.name()).isEqualTo("Max Mustermann");
    }

    @Test
    @DisplayName("Ohne das Recht „Kunden anlegen“ wird nichts geprüft und nichts gespeichert")
    void kundeOhneRecht() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung.")).when(rechte).verlangeFuerToken("tok", Funktion.KUNDEN, FunktionsAktion.ANLEGEN);

        assertThatThrownBy(() -> service.legeKundeAn("tok", kunde(), false)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Berechtigung");
        verify(kunden, never()).save(any());
        verify(duplikate, never()).findeDuplikate(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mögliche Duplikate: ohne Bestätigung Ausnahme mit den Treffern, mit Bestätigung wird angelegt und gar nicht erst gesucht")
    void duplikate() {
        KundeDuplikatTrefferDto treffer = new KundeDuplikatTrefferDto();
        treffer.setId(7L);
        treffer.setName("Max Mustermann GmbH");
        when(duplikate.findeDuplikate(any(), any(), any(), any(), any(), any())).thenReturn(new KundeDuplikatResponseDto(List.of(treffer), true));

        assertThatThrownBy(() -> service.legeKundeAn("tok", kunde(), false)).isInstanceOf(KundeDuplikatException.class)
                .satisfies(e -> assertThat(((KundeDuplikatException) e).getAntwort().getDuplikate()).hasSize(1));
        verify(kunden, never()).save(any());

        service.legeKundeAn("tok", kunde(), true);
        verify(kunden).save(any(Kunde.class));
    }

    @Test
    @DisplayName("Gehört die E-Mail-Adresse schon einem anderen Kunden (Duplikat bestätigt), wird sie nicht übernommen und der Hinweis sagt es; sonst käme ein Datenbankfehler")
    void emailSchonVergeben() {
        when(kunden.existsByKundenEmail("max@example.org")).thenReturn(true);

        var ergebnis = service.legeKundeAn("tok", kunde(), true);

        ArgumentCaptor<Kunde> k = ArgumentCaptor.forClass(Kunde.class);
        verify(kunden).save(k.capture());
        assertThat(k.getValue().getKundenEmails()).isEmpty();
        assertThat(ergebnis.hinweis()).contains("max@example.org").contains("gehört schon zu einem anderen Kunden");
        verify(events, never()).publishEvent(any(EmailAddressChangedEvent.class));
    }

    @Test
    @DisplayName("Ohne Namen wird nichts angelegt")
    void ohneName() {
        KundeCreateRequestDto d = kunde();
        d.setName("   ");

        assertThatThrownBy(() -> service.legeKundeAn("tok", d, true)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Namen");
        verify(kunden, never()).save(any());
    }

    @Test
    @DisplayName("Anfrage anlegen: nur die erlaubten Felder gehen weiter, der Kunde muss es geben; Datum und Status bestimmt der Server")
    void anfrageAnlegen() {
        Kunde k = new Kunde();
        k.setId(42L);
        when(kunden.findById(42L)).thenReturn(Optional.of(k));
        AnfrageResponseDto antwort = new AnfrageResponseDto();
        when(anfragen.erstelleAnfrage(any(AnfrageErstellenDto.class))).thenReturn(antwort);

        var r = service.legeAnfrageAn("tok", new AnfrageEingabe("  Geländer Musterweg  ", 42L, "Musterweg 1", "12345", "Musterstadt", " Treppe außen ", new BigDecimal("1500.00")));

        verify(rechte).verlangeFuerToken("tok", Funktion.ANFRAGEN, FunktionsAktion.ANLEGEN);
        ArgumentCaptor<AnfrageErstellenDto> d = ArgumentCaptor.forClass(AnfrageErstellenDto.class);
        verify(anfragen).erstelleAnfrage(d.capture());
        assertThat(d.getValue().getBauvorhaben()).isEqualTo("Geländer Musterweg");
        assertThat(d.getValue().getKundenId()).isEqualTo(42L);
        assertThat(d.getValue().getKurzbeschreibung()).isEqualTo("Treppe außen");
        assertThat(d.getValue().getBetrag()).isEqualByComparingTo("1500.00");
        assertThat(d.getValue().getAbgeschlossen()).as("Status bestimmt der Server").isNull();
        assertThat(d.getValue().getAnlegedatum()).as("Datum bestimmt der Server").isNull();
        assertThat(r).isSameAs(antwort);
    }

    @Test
    @DisplayName("Anfrage: ohne Recht, ohne Bauvorhaben, ohne oder mit unbekanntem Kunden und mit negativem Betrag wird nichts angelegt")
    void anfrageAblehnungen() {
        when(kunden.findById(42L)).thenReturn(Optional.of(new Kunde()));
        when(kunden.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.legeAnfrageAn("tok", new AnfrageEingabe(" ", 42L, null, null, null, null, null))).hasMessageContaining("Bauvorhaben");
        assertThatThrownBy(() -> service.legeAnfrageAn("tok", new AnfrageEingabe("X", null, null, null, null, null, null))).hasMessageContaining("Kunden");
        assertThatThrownBy(() -> service.legeAnfrageAn("tok", new AnfrageEingabe("X", 99L, null, null, null, null, null))).hasMessageContaining("Kunden");
        assertThatThrownBy(() -> service.legeAnfrageAn("tok", new AnfrageEingabe("X", 42L, null, null, null, null, new BigDecimal("-1")))).hasMessageContaining("negativ");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung.")).when(rechte).verlangeFuerToken("tok", Funktion.ANFRAGEN, FunktionsAktion.ANLEGEN);
        assertThatThrownBy(() -> service.legeAnfrageAn("tok", new AnfrageEingabe("X", 42L, null, null, null, null, null))).hasMessageContaining("Berechtigung");
        verify(anfragen, never()).erstelleAnfrage(any(AnfrageErstellenDto.class));
    }
}
