package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeDuplikatService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class KundeAnlegenWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final KundeRepository kundeRepository = mock(KundeRepository.class);
    private final KundennummerService kundennummerService = mock(KundennummerService.class);
    private final KundeDuplikatService duplikatService = mock(KundeDuplikatService.class);
    private final KundeAnlegenWerkzeug werkzeug = new KundeAnlegenWerkzeug(kundeRepository, kundennummerService, duplikatService);

    @BeforeEach
    void setUp() {
        when(kundennummerService.reserviereNaechsteKundennummer()).thenReturn("K-0042");
        KundeDuplikatResponseDto leer = new KundeDuplikatResponseDto();
        leer.setDuplikate(java.util.List.of());
        when(duplikatService.findeDuplikate(any(), any(), any(), any(), any(), any())).thenReturn(leer);
    }

    private JsonNode muellerGmbh() {
        return json.createObjectNode()
                .put("name", "Mueller GmbH")
                .put("anrede", "Firma")
                .put("strasse", "Hauptstrasse 1")
                .put("plz", "12345")
                .put("ort", "Berlin")
                .put("telefon", "030 123456")
                .put("email", "info@mueller.example");
    }

    @Test
    void esIstEineAenderung() {
        assertThat(werkzeug.aendert()).isTrue();
        assertThat(werkzeug.loescht()).isFalse();
        verify(kundeRepository, never()).save(any());
    }

    @Test
    void vorschauEnthaltAlleAngaben() {
        String v = werkzeug.vorschau(muellerGmbh());
        assertThat(v).contains("Mueller GmbH", "Hauptstrasse 1", "12345", "Berlin", "030 123456", "info@mueller.example");
    }

    @Test
    void ausfuehrenLegtKundenAn() {
        Kunde gespeichert = new Kunde();
        gespeichert.setId(99L);
        gespeichert.setName("Mueller GmbH");
        gespeichert.setKundennummer("K-0042");
        when(kundeRepository.save(any())).thenReturn(gespeichert);

        String ergebnis = werkzeug.ausfuehren(muellerGmbh(), anna);

        ArgumentCaptor<Kunde> cap = ArgumentCaptor.forClass(Kunde.class);
        verify(kundeRepository).save(cap.capture());
        Kunde k = cap.getValue();
        assertThat(k.getName()).isEqualTo("Mueller GmbH");
        assertThat(k.getAnrede()).isEqualTo(Anrede.FIRMA);
        assertThat(k.getStrasse()).isEqualTo("Hauptstrasse 1");
        assertThat(k.getTelefon()).isNotNull();
        assertThat(k.getKundennummer()).isEqualTo("K-0042");
        assertThat(ergebnis).contains("Mueller GmbH", "K-0042", "99");
    }

    @Test
    void fehlenderNameWirdAbgelehnt() {
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode()))
                .isInstanceOf(KiWerkzeugFehler.class)
                .hasMessageContaining("Name");
    }

    @Test
    void ungueltigeEmailWirdAbgelehnt() {
        ObjectNode a = json.createObjectNode().put("name", "Mustermann").put("email", "keine-email");
        assertThatThrownBy(() -> werkzeug.vorschau(a))
                .isInstanceOf(KiWerkzeugFehler.class)
                .hasMessageContaining("E-Mail");
    }

    @Test
    void duplikatWarnungWirdInVorschauAngegeben() {
        var duplikat = new KundeDuplikatResponseDto();
        var treffer = new org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto();
        treffer.setName("Mueller GmbH");
        duplikat.setDuplikate(java.util.List.of(treffer));
        when(duplikatService.findeDuplikate(any(), any(), any(), any(), any(), any())).thenReturn(duplikat);

        String v = werkzeug.vorschau(muellerGmbh());
        assertThat(v).contains("ACHTUNG");
    }

    @Test
    void anredeAusGibtKorrekteWerte() {
        assertThat(KundeAnlegenWerkzeug.anredeAus("Herr")).isEqualTo(Anrede.HERR);
        assertThat(KundeAnlegenWerkzeug.anredeAus("Frau")).isEqualTo(Anrede.FRAU);
        assertThat(KundeAnlegenWerkzeug.anredeAus("Firma")).isEqualTo(Anrede.FIRMA);
        assertThat(KundeAnlegenWerkzeug.anredeAus("Damen und Herren")).isEqualTo(Anrede.DAMEN_HERREN);
        assertThat(KundeAnlegenWerkzeug.anredeAus("unbekannt")).isEqualTo(Anrede.FIRMA);
    }
}
