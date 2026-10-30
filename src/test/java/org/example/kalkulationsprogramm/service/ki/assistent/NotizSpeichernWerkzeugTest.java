package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeNotiz;
import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Die KI speichert eine Information als Notiz bei Lieferant oder Kunde. Nur Dummy-Daten. */
class NotizSpeichernWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final LieferantenRepository lieferanten = mock(LieferantenRepository.class);
    private final LieferantNotizRepository lieferantNotizen = mock(LieferantNotizRepository.class);
    private final KundeRepository kunden = mock(KundeRepository.class);
    private final KundeNotizRepository kundeNotizen = mock(KundeNotizRepository.class);
    private final NotizSpeichernWerkzeug werkzeug = new NotizSpeichernWerkzeug(lieferanten, lieferantNotizen, kunden, kundeNotizen);
    private final KiKontext admin = new KiKontext(1L, "admin", true);

    private JsonNode a(String s) throws Exception {
        return json.readTree(s);
    }

    @BeforeEach
    void setUp() {
        Lieferanten l = new Lieferanten();
        l.setId(4L);
        l.setLieferantenname("Muster Stahl GmbH");
        when(lieferanten.findById(4L)).thenReturn(Optional.of(l));
        Kunde k = new Kunde();
        k.setId(6L);
        k.setName("Mustermann Bau GmbH");
        when(kunden.findById(6L)).thenReturn(Optional.of(k));
    }

    @Test
    void esIstEineAenderungDieNurMitBestaetigungLaeuft() {
        assertThat(werkzeug.aendert()).isTrue();
        assertThat(werkzeug.name()).isEqualTo("notiz_speichern");
    }

    @Test
    void dieVorschauNenntZielUndText() throws Exception {
        String v = werkzeug.vorschau(a("{\"art\":\"LIEFERANT\",\"id\":4,\"text\":\"Neue Preisliste ab Januar. Aus E-Mail vom 10.10.2026\"}"));
        assertThat(v).contains("Lieferant \"Muster Stahl GmbH\"").contains("Neue Preisliste ab Januar");
        verify(lieferantNotizen, never()).save(any());
    }

    @Test
    void ausgefuehrtWirdDieNotizBeimLieferantenGespeichert() throws Exception {
        String r = werkzeug.ausfuehren(a("{\"art\":\"lieferant\",\"id\":4,\"text\":\"Liefert auch Sonderlängen\"}"), admin);
        ArgumentCaptor<LieferantNotiz> c = ArgumentCaptor.forClass(LieferantNotiz.class);
        verify(lieferantNotizen).save(c.capture());
        assertThat(c.getValue().getText()).isEqualTo("Liefert auch Sonderlängen");
        assertThat(c.getValue().getLieferant().getId()).isEqualTo(4L);
        assertThat(r).contains("Muster Stahl GmbH");
        verify(kundeNotizen, never()).save(any());
    }

    @Test
    void auchBeimKundenMoeglich() throws Exception {
        werkzeug.ausfuehren(a("{\"art\":\"KUNDE\",\"id\":6,\"text\":\"Wünscht Angebote immer per Mail\"}"), admin);
        ArgumentCaptor<KundeNotiz> c = ArgumentCaptor.forClass(KundeNotiz.class);
        verify(kundeNotizen).save(c.capture());
        assertThat(c.getValue().getKunde().getId()).isEqualTo(6L);
        verify(lieferantNotizen, never()).save(any());
    }

    @Test
    void fehlendeUngueltigeOderUnbekannteAngabenGebenKlartextUndSpeichernNichts() {
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"art\":\"PROJEKT\",\"id\":4,\"text\":\"abc def\"}"))).hasMessageContaining("LIEFERANT oder KUNDE");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"art\":\"LIEFERANT\",\"text\":\"abc def\"}"))).hasMessageContaining("ID");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"art\":\"LIEFERANT\",\"id\":4,\"text\":\"ab\"}"))).hasMessageContaining("zu kurz");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"art\":\"LIEFERANT\",\"id\":99,\"text\":\"abc def\"}"))).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> werkzeug.vorschau(a("{\"art\":\"LIEFERANT\",\"id\":4,\"text\":\"" + "x".repeat(5_001) + "\"}"))).isInstanceOf(KiWerkzeugFehler.class);
        verify(lieferantNotizen, never()).save(any());
    }
}
