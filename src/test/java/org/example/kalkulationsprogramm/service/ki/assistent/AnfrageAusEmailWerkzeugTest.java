package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.AnfrageService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** KI legt aus einer E-Mail Kunde, Anfrage und Verknüpfung in einem Schritt an. Nur Dummy-Daten. */
class AnfrageAusEmailWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final EmailRepository emails = mock(EmailRepository.class);
    private final KundeRepository kunden = mock(KundeRepository.class);
    private final AnfrageRepository anfragen = mock(AnfrageRepository.class);
    private final AnfrageService anfrageService = mock(AnfrageService.class);
    private final KundennummerService nummern = mock(KundennummerService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AnfrageAusEmailWerkzeug werkzeug = new AnfrageAusEmailWerkzeug(emails, kunden, anfragen, anfrageService, nummern, events);
    private final KiKontext admin = new KiKontext(1L, "admin", true);
    private Email mail;

    private JsonNode a(String s) throws Exception {
        return json.readTree(s);
    }

    @BeforeEach
    void setUp() {
        mail = new Email();
        mail.setId(10L);
        mail.setSubject("Anfrage Geländer");
        mail.setFromAddress("Max@Mustermann.example");
        mail.setDirection(EmailDirection.IN);
        mail.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        when(emails.findById(10L)).thenReturn(Optional.of(mail));
        when(kunden.findByKundenEmailIgnoreCase("max@mustermann.example")).thenReturn(List.of());
    }

    @Test
    void esIstEineAenderungUndFuerAlleBenutzerVerfuegbar() {
        assertThat(werkzeug.aendert()).isTrue();
        assertThat(werkzeug.verfuegbar(admin)).isTrue();
    }

    @Test
    void dieVorschauNenntNeuenKundenAnfrageUndVerknuepfung() throws Exception {
        String v = werkzeug.vorschau(a("{\"emailId\":10,\"kunde_name\":\"Mustermann Bau GmbH\",\"kunde_ort\":\"Musterstadt\",\"bauvorhaben\":\"Geländer Haus 4\",\"kurzbeschreibung\":\"Treppengeländer außen\"}"));
        assertThat(v).contains("NEUEN Kunden anlegen").contains("Mustermann Bau GmbH").contains("max@mustermann.example").contains("Anfrage anlegen: \"Geländer Haus 4\"")
                .contains("Treppengeländer außen").contains("verknüpft");
        verify(kunden, never()).save(any());
    }

    @Test
    void mitVorhandenemKundenWirdKeinNeuerAngelegt() throws Exception {
        Kunde k = new Kunde();
        k.setId(5L);
        k.setName("Mustermann Bau GmbH");
        k.setKundennummer("K-100");
        when(kunden.findById(5L)).thenReturn(Optional.of(k));
        assertThat(werkzeug.vorschau(a("{\"emailId\":10,\"kundeId\":5,\"bauvorhaben\":\"X\"}"))).contains("Vorhandenen Kunden verwenden").contains("K-100").doesNotContain("NEUEN");
    }

    @Test
    void gibtEsDenAbsenderSchonAlsKundenWirdNichtDoppeltAngelegtUndDieKiBekommtDieId() throws Exception {
        Kunde k = new Kunde();
        k.setId(5L);
        k.setName("Mustermann Bau GmbH");
        when(kunden.findByKundenEmailIgnoreCase("max@mustermann.example")).thenReturn(List.of(k));
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":10,\"kunde_name\":\"Neu\",\"bauvorhaben\":\"X\"}"))).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("kundeId 5");
    }

    @Test
    void ausgefuehrtWirdKundeAnfrageUndVerknuepfung() throws Exception {
        when(nummern.reserviereNaechsteKundennummer()).thenReturn("K-200");
        when(kunden.save(any())).thenAnswer(i -> { Kunde k = i.getArgument(0); k.setId(6L); return k; });
        AnfrageResponseDto antwort = mock(AnfrageResponseDto.class);
        when(antwort.getId()).thenReturn(33L);
        when(anfrageService.erstelleAnfrage(any(AnfrageErstellenDto.class))).thenReturn(antwort);
        Anfrage anfrage = new Anfrage();
        anfrage.setId(33L);
        anfrage.setBauvorhaben("Geländer Haus 4");
        when(anfragen.findById(33L)).thenReturn(Optional.of(anfrage));

        String r = werkzeug.ausfuehren(a("{\"emailId\":10,\"kunde_name\":\"Mustermann Bau GmbH\",\"bauvorhaben\":\"Geländer Haus 4\",\"projektOrt\":\"Musterstadt\"}"), admin);

        ArgumentCaptor<Kunde> kunde = ArgumentCaptor.forClass(Kunde.class);
        verify(kunden).save(kunde.capture());
        assertThat(kunde.getValue().getKundennummer()).isEqualTo("K-200");
        assertThat(kunde.getValue().getKundenEmails()).containsExactly("max@mustermann.example");
        ArgumentCaptor<AnfrageErstellenDto> dto = ArgumentCaptor.forClass(AnfrageErstellenDto.class);
        verify(anfrageService).erstelleAnfrage(dto.capture());
        assertThat(dto.getValue().getKundenId()).isEqualTo(6L);
        assertThat(dto.getValue().getProjektOrt()).isEqualTo("Musterstadt");
        assertThat(mail.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(mail.getAnfrage()).isSameAs(anfrage);
        verify(emails).save(mail);
        assertThat(r).contains("Kunde \"Mustermann Bau GmbH\"").contains("angelegt").contains("verknüpft");
    }

    @Test
    void eineSchonZugeordneteOderAusgehendeMailWirdAbgelehnt() throws Exception {
        mail.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":10,\"kunde_name\":\"Neu GmbH\",\"bauvorhaben\":\"X\"}"))).hasMessageContaining("schon zugeordnet");
        mail.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        mail.setDirection(EmailDirection.OUT);
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":10,\"kunde_name\":\"Neu GmbH\",\"bauvorhaben\":\"X\"}"))).hasMessageContaining("eingehende");
    }

    @Test
    void fehlendeAngabenGebenKlartext() throws Exception {
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"bauvorhaben\":\"X\",\"kunde_name\":\"Neu GmbH\"}"))).hasMessageContaining("Nummer der E-Mail");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":10,\"bauvorhaben\":\"X\"}"))).hasMessageContaining("kundeId");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":10,\"kunde_name\":\"Neu GmbH\"}"))).isInstanceOf(KiWerkzeugFehler.class);
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"emailId\":99,\"kunde_name\":\"Neu GmbH\",\"bauvorhaben\":\"X\"}"))).hasMessageContaining("gibt es nicht");
    }
}
