package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanBelegung;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Antwort;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Zeile;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Belegung der Plantafel (#323): Zeitraum kommt als Datum, Fehler als 400 mit Klartext. Nur Dummy-Daten. */
class PlantafelBelegungControllerTest {

    private final PlanBelegung service = mock(PlanBelegung.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelBelegungController(service)).build();

    @Test
    void derZeitraumWirdAlsDatumGelesen() throws Exception {
        when(service.belegung(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18))).thenReturn(new Antwort(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18),
                List.of(new Zeile("M3", "MITARBEITER", "Max Mustermann", List.of()))));

        mvc.perform(get("/api/plantafel/belegung").param("von", "2026-10-12").param("bis", "2026-10-18")).andExpect(status().isOk())
                .andExpect(jsonPath("$.zeilen[0].name").value("Max Mustermann"));
    }

    @Test
    void einFalscherZeitraumKommtAls400MitKlartext() throws Exception {
        when(service.belegung(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 12))).thenThrow(new IllegalArgumentException("Bitte einen Zeitraum von bis angeben."));

        mvc.perform(get("/api/plantafel/belegung").param("von", "2026-10-18").param("bis", "2026-10-12")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen Zeitraum von bis angeben."));
    }
}
