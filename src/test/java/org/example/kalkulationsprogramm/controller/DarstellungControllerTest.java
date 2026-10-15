package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.example.kalkulationsprogramm.service.DarstellungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DarstellungControllerTest {

    private final DarstellungService dienst = mock(DarstellungService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(dienst.farbe()).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new DarstellungController(dienst)).build();
    }

    @Test
    void ohneEintragIstDieFarbeLeer() throws Exception {
        mvc.perform(get("/api/darstellung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farbe").doesNotExist());
    }

    @Test
    void gewaehlteFarbeWirdGeliefert() throws Exception {
        when(dienst.farbe()).thenReturn(Optional.of("#0f766e"));
        mvc.perform(get("/api/darstellung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farbe").value("#0f766e"));
    }

    @Test
    void farbeSpeichern() throws Exception {
        when(dienst.farbe()).thenReturn(Optional.of("#0f766e"));
        mvc.perform(put("/api/settings/darstellung").contentType(MediaType.APPLICATION_JSON).content("{\"farbe\":\"#0f766e\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farbe").value("#0f766e"));
        verify(dienst).setzeFarbe("#0f766e");
    }

    @Test
    void ungueltigeFarbeGibt400() throws Exception {
        doThrow(new IllegalArgumentException("Bitte eine Farbe wie #e11d48 angeben."))
                .when(dienst).setzeFarbe("rot");
        mvc.perform(put("/api/settings/darstellung").contentType(MediaType.APPLICATION_JSON).content("{\"farbe\":\"rot\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ueberlangeFarbeGibt400() throws Exception {
        String lang = "#" + "a".repeat(10_001);
        mvc.perform(put("/api/settings/darstellung").contentType(MediaType.APPLICATION_JSON).content("{\"farbe\":\"" + lang + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
