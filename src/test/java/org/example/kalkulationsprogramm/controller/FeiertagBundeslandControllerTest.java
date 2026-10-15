package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.FeiertagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FeiertagBundeslandControllerTest {

    private FeiertagService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(FeiertagService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FeiertagBundeslandController(service)).build();
    }

    @Test
    void liefertDasEingestellteLandUndAlleSechzehn() throws Exception {
        when(service.bundesland()).thenReturn("BY");
        mvc.perform(get("/api/settings/feiertage-bundesland"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundesland").value("BY"))
                .andExpect(jsonPath("$.auswahl.length()").value(16))
                .andExpect(jsonPath("$.auswahl[?(@.kuerzel=='NW')].name").value("Nordrhein-Westfalen"));
    }

    @Test
    void speichertEinLand() throws Exception {
        when(service.setzeBundesland("NW")).thenReturn("NW");
        mvc.perform(put("/api/settings/feiertage-bundesland").contentType(MediaType.APPLICATION_JSON).content("{\"bundesland\":\"NW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundesland").value("NW"));
    }

    @Test
    void meldetEinUnbekanntesLandMitKlartext() throws Exception {
        when(service.setzeBundesland("XX")).thenThrow(new IllegalArgumentException("Unbekanntes Bundesland: XX"));
        mvc.perform(put("/api/settings/feiertage-bundesland").contentType(MediaType.APPLICATION_JSON).content("{\"bundesland\":\"XX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unbekanntes Bundesland: XX"));
    }
}
