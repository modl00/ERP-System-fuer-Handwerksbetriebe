package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.KiHilfeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class KiHilfeControllerFehlerTest {

    @Mock KiHilfeService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new KiHilfeController(service)).build();
    }

    private static final String ANFRAGE = "{\"messages\":[{\"role\":\"user\",\"text\":\"Hallo\"}]}";

    @Test
    void einErschoepftesKontingentWirdImChatBeimNamenGenannt() throws Exception {
        when(service.chat(any(), any())).thenThrow(new KiHilfeService.KiHilfeNichtVerfuegbar("Das Google-Kontingent für dieses Modell ist erschöpft (Fehler 429)."));

        mvc.perform(post("/api/ki-hilfe/chat").contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Das Google-Kontingent für dieses Modell ist erschöpft (Fehler 429)."));
    }

    @Test
    void einUnbekannterFehlerBleibtBeiDerAllgemeinenMeldung() throws Exception {
        when(service.chat(any(), any())).thenThrow(new IllegalStateException("kaputt"));

        mvc.perform(post("/api/ki-hilfe/chat").contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut."));
    }
}
