package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.KiWissenService;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProgrammHilfeWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final KiWissenService wissen = mock(KiWissenService.class);
    private final ProgrammHilfeWerkzeug werkzeug = new ProgrammHilfeWerkzeug(wissen);

    @Test
    void liefertDieAuszuegeAusDemProgrammwissenUndWaehltKeineBetriebsdaten() {
        when(wissen.relevantes(eq("Lieferschein anlegen"), anyInt())).thenReturn("## Lieferschein\nUnter Einkauf anlegen.");
        when(wissen.grenzen()).thenReturn("Kann keine Briefe drucken.");

        String antwort = werkzeug.ausfuehren(json.createObjectNode().put("suche", "Lieferschein anlegen"), anna);

        assertThat(antwort).contains("Unter Einkauf anlegen").contains("Kann keine Briefe drucken");
        assertThat(werkzeug.aendert()).isFalse();
        assertThat(werkzeug.beruehrtProgrammdaten()).as("steht auch bei strengem Datenschutz zur Verfügung").isFalse();
        assertThat(werkzeug.verfuegbar(anna)).isTrue();
    }

    @Test
    void ohneTrefferSagtDasWerkzeugDassNichtsDazuSteht() {
        when(wissen.relevantes(eq("Raumschiff"), anyInt())).thenReturn("");

        String antwort = werkzeug.ausfuehren(json.createObjectNode().put("suche", "Raumschiff"), anna);

        assertThat(antwort).contains("nichts im Programmwissen");
    }
}
