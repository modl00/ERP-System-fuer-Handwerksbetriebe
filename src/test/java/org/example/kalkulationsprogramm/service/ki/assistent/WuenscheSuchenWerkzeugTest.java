package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Aehnlich;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Die KI sucht vor einem neuen Wunsch nach ähnlichen offenen Issues (#249). */
class WuenscheSuchenWerkzeugTest {

    private final EntwicklungsWunschService wunsch = mock(EntwicklungsWunschService.class);
    private final WuenscheSuchenWerkzeug werkzeug = new WuenscheSuchenWerkzeug(wunsch);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void esIstEineAuskunftNurFuerAdministratorenMitEingerichtetemZugang() {
        when(wunsch.istBereit()).thenReturn(true);
        assertThat(werkzeug.aendert()).isFalse();
        assertThat(werkzeug.verfuegbar(new KiKontext(1L, "admin", true))).isTrue();
        assertThat(werkzeug.verfuegbar(new KiKontext(2L, "anna", false))).isFalse();
        when(wunsch.istBereit()).thenReturn(false);
        assertThat(werkzeug.verfuegbar(new KiKontext(1L, "admin", true))).isFalse();
    }

    @Test
    void trefferStehenMitNummerUndTitelUndDemHinweisAufKommentarZuIssue() throws Exception {
        when(wunsch.sucheAehnliche(anyString())).thenReturn(List.of(new Aehnlich(251, "KI-Chatfenster: Größe anpassen", "http://x/251", 4)));
        String r = werkzeug.ausfuehren(json.readTree("{\"suche\":\"Chatfenster verschieben\"}"), new KiKontext(1L, "admin", true));
        assertThat(r).contains("#251").contains("KI-Chatfenster: Größe anpassen").contains("kommentarZuIssue");
    }

    @Test
    void ohneTreffersagtEsDassEinNeuerWunschMoeglichIst() throws Exception {
        when(wunsch.sucheAehnliche(anyString())).thenReturn(List.of());
        assertThat(werkzeug.ausfuehren(json.readTree("{\"suche\":\"Etwas ganz Neues zum Thema Kalender\"}"), new KiKontext(1L, "admin", true))).contains("Kein ähnliches offenes Issue");
    }
}
