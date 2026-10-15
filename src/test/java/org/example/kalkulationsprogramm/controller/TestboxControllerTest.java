package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.testbox.TestboxService;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Einstellung;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Punkt;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Vorschlag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Test-Box: Eingaben kommen an, der Schlüssel kommt nie zurück, Fehler als 400 mit Klartext. Nur Dummy-Daten. */
class TestboxControllerTest {

    private final TestboxService service = mock(TestboxService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestboxController(service)).build();

    @Test
    void dieEinstellungHatKeinFeldFuerDenSchluessel() throws Exception {
        when(service.einstellung()).thenReturn(new Einstellung("http://g", "M/Demo", true));

        mvc.perform(get("/api/testbox/einstellung")).andExpect(status().isOk()).andExpect(jsonPath("$.tokenGesetzt").value(true)).andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void speichernUndPunkteUndAufbereitenUndSenden() throws Exception {
        when(service.speichereEinstellung("http://g", "M/Demo", "t")).thenReturn(new Einstellung("http://g", "M/Demo", true));
        when(service.punkte()).thenReturn(List.of(new Punkt(5, "Plantafel", "http://g/5", List.of("Wartet auf Antwort"), "Frage?", true)));
        when(service.aufbereiten(5, "text", "PROBLEM")).thenReturn(new Vorschlag("PROBLEM", "Es klappt nicht.", true, null));

        mvc.perform(put("/api/testbox/einstellung").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"http://g\",\"repo\":\"M/Demo\",\"token\":\"t\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/testbox/punkte")).andExpect(status().isOk()).andExpect(jsonPath("$[0].nummer").value(5)).andExpect(jsonPath("$[0].wartet").value(true));
        mvc.perform(post("/api/testbox/aufbereiten").contentType(MediaType.APPLICATION_JSON).content("{\"nummer\":5,\"antwort\":\"text\",\"einordnung\":\"PROBLEM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kommentar").value("Es klappt nicht.")).andExpect(jsonPath("$.ki").value(true));
        mvc.perform(post("/api/testbox/senden").contentType(MediaType.APPLICATION_JSON).content("{\"nummer\":5,\"einordnung\":\"PROBLEM\",\"kommentar\":\"Es klappt nicht.\",\"original\":\"text\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gesendet").value(true));
        verify(service).senden(5, "PROBLEM", "Es klappt nicht.", "text");
    }

    @Test
    void fehlerKommenAls400MitKlartext() throws Exception {
        when(service.punkte()).thenThrow(new IllegalArgumentException("Gitea ist nicht erreichbar. Bitte die Adresse prüfen."));

        mvc.perform(get("/api/testbox/punkte")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Gitea ist nicht erreichbar. Bitte die Adresse prüfen."));
    }
}
