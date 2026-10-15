package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.ZeileEingabe;
import org.example.kalkulationsprogramm.service.MaterialRechnungService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(MaterialRechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
class MaterialRechnungControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private MaterialRechnungService service;

    private static MaterialAbrechnungDto leer() {
        return new MaterialAbrechnungDto(5, List.of(), List.of(), 0, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Übersicht lesen und Zuordnung speichern: Zeilen mit Position, Betrag und Beschreibung kommen beim Dienst an")
    void lesenUndSpeichern() throws Exception {
        given(service.uebersicht(7L)).willReturn(leer());
        given(service.speichern(any(), any(), any())).willReturn(leer());

        mvc.perform(get("/api/projekte/7/material-abrechnung")).andExpect(status().isOk()).andExpect(jsonPath("$.schwelleProzent").value(5));
        mvc.perform(post("/api/projekte/7/material-abrechnung/50").contentType(MediaType.APPLICATION_JSON)
                .content("{\"zeilen\":[{\"artikelInProjektId\":1,\"betrag\":300.5},{\"betrag\":20,\"beschreibung\":\"Kleinteile\"}]}")).andExpect(status().isOk());

        verify(service).speichern(7L, 50L, List.of(new ZeileEingabe(1L, new BigDecimal("300.5"), null), new ZeileEingabe(null, new BigDecimal("20"), "Kleinteile")));
    }

    @Test
    @DisplayName("Ablehnung des Dienstes kommt als 400 mit Text an; Schwelle setzen")
    void fehlerUndSchwelle() throws Exception {
        given(service.speichern(any(), any(), any())).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Zeilen ergeben 600,00 €."));
        given(service.setzeSchwelle(10)).willReturn(10);

        mvc.perform(post("/api/projekte/7/material-abrechnung/50").contentType(MediaType.APPLICATION_JSON).content("{\"zeilen\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/material-abrechnung/schwelle").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prozent").value(10));
    }
}
