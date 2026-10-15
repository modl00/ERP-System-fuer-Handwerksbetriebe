package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.AngebotsLinkService;
import org.example.kalkulationsprogramm.service.AngebotsLinkService.Konfiguration;
import org.example.kalkulationsprogramm.service.AngebotsLinkService.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AngebotsLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
class AngebotsLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AngebotsLinkService service;

    @Test
    void einstellungLesen() throws Exception {
        given(service.konfiguration()).willReturn(new Konfiguration(false, "", false, false, 14));

        mockMvc.perform(get("/api/settings/angebots-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false))
                .andExpect(jsonPath("$.moeglich").value(false))
                .andExpect(jsonPath("$.gueltigkeitTage").value(14));
    }

    @Test
    void einstellungSpeichern() throws Exception {
        given(service.speichern(true, "https://erp.example.org", 30))
                .willReturn(new Konfiguration(true, "https://erp.example.org", false, true, 30));

        mockMvc.perform(put("/api/settings/angebots-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktiv\":true,\"adresse\":\"https://erp.example.org\",\"gueltigkeitTage\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moeglich").value(true))
                .andExpect(jsonPath("$.adresse").value("https://erp.example.org"));
    }

    @Test
    void ungueltigeAdresseKommtAlsFehlerZurueck() throws Exception {
        given(service.speichern(true, "http://x", null)).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Adresse muss mit https:// beginnen."));

        mockMvc.perform(put("/api/settings/angebots-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktiv\":true,\"adresse\":\"http://x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editorBekommtNurDenStandOhneAdresse() throws Exception {
        given(service.status()).willReturn(new Status(true, 21));

        mockMvc.perform(get("/api/ausgangs-dokumente/angebots-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moeglich").value(true))
                .andExpect(jsonPath("$.gueltigkeitTage").value(21))
                .andExpect(jsonPath("$.adresse").doesNotExist());
    }
}
