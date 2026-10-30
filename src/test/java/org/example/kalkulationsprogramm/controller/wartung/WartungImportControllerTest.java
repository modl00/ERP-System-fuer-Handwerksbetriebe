package org.example.kalkulationsprogramm.controller.wartung;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Zusammenfassung;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAusfuehrer;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAusfuehrer.Ergebnisbericht;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(WartungImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class WartungImportControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private WartungImportService service;
    @MockitoBean private WartungImportAusfuehrer ausfuehrer;

    @Test
    @DisplayName("Die Vorschau nimmt die Exportdatei als JSON und liefert die Zusammenfassung")
    void vorschau() throws Exception {
        given(service.vorschau(any())).willReturn(new Vorschau(new Zusammenfassung(2, 1, 1, 0, 0, 1, 1, 1, 10, 8, 2, 3, 0), List.of(), List.of(), List.of(), List.of(), List.of()));

        mvc.perform(post("/api/admin/wartung-import/vorschau").contentType(MediaType.APPLICATION_JSON).content("{\"quelle\":\"HandwerkPro\",\"wartungen\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zusammenfassung.wartungen").value(2)).andExpect(jsonPath("$.zusammenfassung.werteNeuInListe").value(3));
    }

    @Test
    @DisplayName("Eine fremde Datei wird mit Klartext abgelehnt")
    void fremdeDatei() throws Exception {
        given(service.vorschau(any())).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das ist keine Exportdatei aus HandwerkPro."));

        mvc.perform(post("/api/admin/wartung-import/vorschau").contentType(MediaType.APPLICATION_JSON).content("{\"x\":1}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Die Übernahme liefert den Bericht; neue Werte kommen standardmäßig in den Katalog, mit neueWerteInKatalog=false nicht")
    void ausfuehren() throws Exception {
        given(ausfuehrer.ausfuehren(any(), eq(true))).willReturn(new Ergebnisbericht(105, 0, 6, 8, 43, 227, 0, 71, List.of("A-3: Modul fehlt."), List.of()));
        given(ausfuehrer.ausfuehren(any(), eq(false))).willReturn(new Ergebnisbericht(1, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of()));

        mvc.perform(post("/api/admin/wartung-import/ausfuehren").contentType(MediaType.APPLICATION_JSON).content("{\"quelle\":\"HandwerkPro\",\"wartungen\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.protokolleNeu").value(105)).andExpect(jsonPath("$.uebersprungen[0]").value("A-3: Modul fehlt."));
        mvc.perform(post("/api/admin/wartung-import/ausfuehren").param("neueWerteInKatalog", "false").contentType(MediaType.APPLICATION_JSON).content("{\"quelle\":\"HandwerkPro\",\"wartungen\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.protokolleNeu").value(1));
    }
}
