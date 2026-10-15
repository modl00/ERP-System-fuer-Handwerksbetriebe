package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.example.kalkulationsprogramm.service.planung.PlanTermine;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Termin verschieben (#323): Tag und Uhrzeit kommen als Text, Fehler als 400 mit Klartext. Nur Dummy-Daten. */
class PlantafelTerminControllerTest {

    private final PlanTermine service = mock(PlanTermine.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelTerminController(service))
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()))
            .build();

    @Test
    void tagUndUhrzeitWerdenGelesen() throws Exception {
        mvc.perform(put("/api/plantafel/termine/5/verschieben").contentType(MediaType.APPLICATION_JSON)
                .content("{\"datum\":\"2026-10-14\",\"von\":\"13:30\",\"bis\":\"15:30\",\"vonMitarbeiterId\":1,\"nachMitarbeiterId\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        verify(service).verschiebe(5L, LocalDate.of(2026, 10, 14), LocalTime.of(13, 30), LocalTime.of(15, 30), 1L, 2L);
    }

    @Test
    void einFehlerKommtAls400MitKlartext() throws Exception {
        doThrow(new IllegalArgumentException("Der Termin wurde nicht gefunden.")).when(service).verschiebe(9L, LocalDate.of(2026, 10, 14), null, null, null, null);

        mvc.perform(put("/api/plantafel/termine/9/verschieben").contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"2026-10-14\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Der Termin wurde nicht gefunden."));
    }
}
