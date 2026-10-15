package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.RessourceDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.Zuweisung;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Schnittstelle für Ressourcen und Zuweisung (#323): Eingabefehler kommen als 400 mit Klartext. Nur Dummy-Daten. */
class PlantafelZuweisungControllerTest {

    private final PlanungsZuweisung service = mock(PlanungsZuweisung.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelZuweisungController(service)).build();

    @Test
    void dieRessourcenKommenAlsListe() throws Exception {
        when(service.ressourcen()).thenReturn(List.of(new RessourceDto(1L, "Werkstatt", "ORT", 20, true)));

        mvc.perform(get("/api/plantafel/ressourcen")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Werkstatt"));
    }

    @Test
    void diePersonenKommenAlsListeMitNummerUndName() throws Exception {
        when(service.personen()).thenReturn(List.of(new PlanungsZuweisung.PersonDto(4L, "Max Mustermann")));

        mvc.perform(get("/api/plantafel/personen")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Max Mustermann")).andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void eineRessourceWirdAngelegt() throws Exception {
        when(service.ressourceSpeichern(eq(null), eq("Kran"), eq("MASCHINE"), eq(null), eq(null))).thenReturn(new RessourceDto(5L, "Kran", "MASCHINE", 0, true));

        mvc.perform(post("/api/plantafel/ressourcen").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kran\",\"art\":\"MASCHINE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void dieZuweisungKommtAusDemKoerper() throws Exception {
        when(service.zuweisen(eq(3L), any())).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, null, null, "OHNE_ZIEL", List.of()));

        mvc.perform(put("/api/plantafel/vorgaenge/3/zuweisung").contentType(MediaType.APPLICATION_JSON).content("{\"mitarbeiterIds\":[1,2],\"ressourceId\":4,\"prioritaet\":\"DRINGEND\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projektId").value(7));
        verify(service).zuweisen(3L, new Zuweisung(List.of(1L, 2L), 4L, "DRINGEND"));
    }

    @Test
    void einEingabefehlerKommtAls400MitKlartext() throws Exception {
        when(service.zuweisen(eq(3L), any())).thenThrow(new IllegalArgumentException("Einen der Mitarbeiter gibt es nicht."));

        mvc.perform(put("/api/plantafel/vorgaenge/3/zuweisung").contentType(MediaType.APPLICATION_JSON).content("{\"mitarbeiterIds\":[99]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Einen der Mitarbeiter gibt es nicht."));
    }
}
