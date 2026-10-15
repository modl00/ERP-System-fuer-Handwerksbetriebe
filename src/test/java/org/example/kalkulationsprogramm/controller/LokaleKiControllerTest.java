package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.LokalerKiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LokaleKiControllerTest {

    @Mock LokalerKiService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LokaleKiController(service)).build();
    }

    @Test
    void konfigurationZeigtNurObEinSchluesselGesetztIst() throws Exception {
        when(service.konfiguration()).thenReturn(new LokalerKiService.Konfiguration("http://pc:11434", "qwen2.5:7b", true, false));

        mvc.perform(get("/api/settings/ki-lokal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresse").value("http://pc:11434"))
                .andExpect(jsonPath("$.schluesselGesetzt").value(true))
                .andExpect(jsonPath("$.schluessel").doesNotExist());
    }

    @Test
    void speichernGibtDieWerteAnDenDienstWeiter() throws Exception {
        when(service.speichern("http://pc:11434", "qwen2.5:7b", null, true))
                .thenReturn(new LokalerKiService.Konfiguration("http://pc:11434", "qwen2.5:7b", false, true));

        mvc.perform(put("/api/settings/ki-lokal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"http://pc:11434\",\"modell\":\"qwen2.5:7b\",\"schluessel\":null,\"hilfeLokal\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hilfeLokal").value(true));
        verify(service).speichern(eq("http://pc:11434"), eq("qwen2.5:7b"), eq(null), eq(true));
    }

    @Test
    void ungueltigeEingabeKommtAlsFehlerMitKlartext() throws Exception {
        when(service.speichern("pc", "m", null, false)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Adresse muss mit http:// beginnen."));

        mvc.perform(put("/api/settings/ki-lokal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"pc\",\"modell\":\"m\",\"hilfeLokal\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modelleUndTestWerdenDurchgereicht() throws Exception {
        when(service.modelle()).thenReturn(List.of("a", "b"));
        when(service.teste()).thenReturn(new LokalerKiService.Testergebnis(true, "OK", "Die lokale KI antwortet"));

        mvc.perform(get("/api/settings/ki-lokal/modelle")).andExpect(status().isOk()).andExpect(jsonPath("$[1]").value("b"));
        mvc.perform(post("/api/settings/ki-lokal/test")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }
}
