package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektDto;
import org.example.kalkulationsprogramm.service.KundeObjektService;
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

@WebMvcTest(KundeObjektController.class)
@AutoConfigureMockMvc(addFilters = false)
class KundeObjektControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private KundeObjektService service;

    private static final String GUELTIG = "{\"bezeichnung\":\"Werk Nord\",\"strasse\":\"Industriestraße 12\",\"plz\":\"97080\",\"ort\":\"Würzburg\"}";

    private static KundeObjektDto dto() {
        return new KundeObjektDto(5L, "Werk Nord", "Industriestraße 12", "97080", "Würzburg", null, null, null, true, 2);
    }

    @Test
    @DisplayName("Liste: 200; Inaktive nur mit ?inaktive=true")
    void liste() throws Exception {
        given(service.liste(3L, false)).willReturn(List.of(dto()));
        given(service.liste(3L, true)).willReturn(List.of(dto(), dto()));

        mockMvc.perform(get("/api/kunden/3/objekte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].anzahlWartungen").value(2));
        mockMvc.perform(get("/api/kunden/3/objekte?inaktive=true"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Anlegen: 201; ohne Bezeichnung oder mit zu langen Feldern 400, nichts wird gespeichert")
    void anlegen() throws Exception {
        given(service.anlegen(eq(3L), any())).willReturn(dto());

        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON).content("{\"bezeichnung\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"" + "A".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Werk\",\"notizen\":\"" + "A".repeat(10001) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(service).anlegen(eq(3L), any());
        verify(service, never()).aendern(any(), any(), any());
    }

    @Test
    @DisplayName("Ändern: 200; fremdes oder unbekanntes Objekt 404")
    void aendern() throws Exception {
        given(service.aendern(eq(3L), eq(5L), any())).willReturn(dto());
        given(service.aendern(eq(3L), eq(99L), any())).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Objekt wurde nicht gefunden."));

        mockMvc.perform(put("/api/kunden/3/objekte/5").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/kunden/3/objekte/99").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Aktiv setzen: Pflichtfeld 'aktiv'; ein leerer Body deaktiviert nichts")
    void aktivSetzen() throws Exception {
        given(service.aktivSetzen(3L, 5L, false)).willReturn(new KundeObjektDto(5L, "Werk Nord", null, null, null, null, null, null, false, 0));

        mockMvc.perform(put("/api/kunden/3/objekte/5/aktiv").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false));
        mockMvc.perform(put("/api/kunden/3/objekte/5/aktiv").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).aktivSetzen(3L, 5L, true);
    }

    @Test
    @DisplayName("Skript und SQL in der Bezeichnung werden angenommen (nur als Text gespeichert); ungültige Nummern 400")
    void sonderfaelle() throws Exception {
        given(service.anlegen(eq(3L), any())).willReturn(dto());

        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/kunden/3/objekte").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"'; DROP TABLE wartung_objekt; --\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/kunden/abc/objekte")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/kunden/99999999999999999999/objekte")).andExpect(status().isBadRequest());
    }
}
