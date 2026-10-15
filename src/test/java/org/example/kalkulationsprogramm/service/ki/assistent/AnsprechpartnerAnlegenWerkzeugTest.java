package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerSpeichernDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeAnsprechpartnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AnsprechpartnerAnlegenWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final KundeRepository kunden = mock(KundeRepository.class);
    private final KundeAnsprechpartnerService service = mock(KundeAnsprechpartnerService.class);
    private final AnsprechpartnerAnlegenWerkzeug werkzeug = new AnsprechpartnerAnlegenWerkzeug(kunden, service);

    private JsonNode erika() {
        return json.createObjectNode().put("kundeId", 3).put("name", "Erika Mustermann").put("funktion", "Einkauf").put("telefon", "01234 567890")
                .put("email", "Erika@Mustermann-Bau.example");
    }

    @BeforeEach
    void setUp() {
        Kunde k = new Kunde();
        k.setId(3L);
        k.setName("Mustermann Bau GmbH");
        when(kunden.findById(3L)).thenReturn(Optional.of(k));
        when(service.liste(3L)).thenReturn(List.of(new KundeAnsprechpartnerDto(1L, "Max Mustermann", null, null, null, null, null, true)));
    }

    @Test
    void esIstEineAenderungMitVorschauDerWerte() {
        assertThat(werkzeug.aendert()).isTrue();
        assertThat(werkzeug.loescht()).isFalse();
        assertThat(werkzeug.vorschau(erika())).contains("Mustermann Bau GmbH", "Erika Mustermann", "Einkauf", "Tel. 01234 567890", "erika@mustermann-bau.example");
        verify(service, never()).anlegen(any(), any());
    }

    @Test
    void ausfuehrenLegtDenAnsprechpartnerOhneHauptMarkierungAn() {
        String r = werkzeug.ausfuehren(erika(), anna);

        ArgumentCaptor<KundeAnsprechpartnerSpeichernDto> c = ArgumentCaptor.forClass(KundeAnsprechpartnerSpeichernDto.class);
        verify(service).anlegen(eq(3L), c.capture());
        assertThat(c.getValue().name()).isEqualTo("Erika Mustermann");
        assertThat(c.getValue().email()).isEqualTo("erika@mustermann-bau.example");
        assertThat(c.getValue().haupt()).isFalse();
        assertThat(r).contains("wurde bei Mustermann Bau GmbH angelegt");
    }

    @Test
    void einNameDenEsBeimKundenSchonGibtWirdAbgelehnt() {
        JsonNode doppelt = json.createObjectNode().put("kundeId", 3).put("name", "max mustermann");
        assertThatThrownBy(() -> werkzeug.vorschau(doppelt)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("gibt es schon");
        assertThatThrownBy(() -> werkzeug.ausfuehren(doppelt, anna)).isInstanceOf(KiWerkzeugFehler.class);
        verify(service, never()).anlegen(any(), any());
    }

    @Test
    void ungueltigeAngabenWerdenMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode().put("name", "Erika"))).hasMessageContaining("Kunden-ID");
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode().put("kundeId", 99).put("name", "Erika"))).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode().put("kundeId", 3))).hasMessageContaining("Namen");
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode().put("kundeId", 3).put("name", "Erika").put("email", "keine-mail"))).hasMessageContaining("nicht gültig");
    }

    @Test
    void dieGrenzeJeKundeVomPcWirdAlsKlartextWeitergegeben() {
        when(service.anlegen(eq(3L), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Pro Kunde sind höchstens 50 Ansprechpartner möglich."));
        assertThatThrownBy(() -> werkzeug.ausfuehren(erika(), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("höchstens 50");
    }
}
