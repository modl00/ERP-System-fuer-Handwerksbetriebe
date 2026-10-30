package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Eingabe;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Ergebnis;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.HinweisNoetigException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ZeiterfassungNachtragController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeiterfassungNachtragControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ZeiterfassungNachtragService service;

    private static final String BODY = "{\"token\":\"tok\",\"typ\":\"ARBEIT\",\"datum\":\"2026-10-03\",\"start\":\"08:00\",\"ende\":\"10:30\",\"projektId\":7,\"arbeitsgangId\":3,\"notiz\":\"Geländer\"}";

    @Test
    @DisplayName("Neue Buchung: Datum und Uhrzeiten werden gelesen, 201 mit der Buchung")
    void erfassen() throws Exception {
        given(service.erfasse(any())).willReturn(new Ergebnis(Map.of("id", 100), List.of()));

        mvc.perform(post("/api/zeiterfassung/nachtrag").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.buchung.id").value(100)).andExpect(jsonPath("$.hinweise").isEmpty());

        verify(service).erfasse(new Eingabe("tok", "ARBEIT", LocalDate.of(2026, 10, 3), LocalTime.of(8, 0), LocalTime.of(10, 30), null, 7L, 3L, null, "Geländer", false, null));
    }

    @Test
    @DisplayName("Hinweis nötig: 409 mit Code BESTAETIGUNG_NOETIG und der Liste der Hinweise; mit bestaetigt=true geht es an den Dienst")
    void bestaetigung() throws Exception {
        given(service.erfasse(any())).willThrow(new HinweisNoetigException(List.of("Am 03.10.2026 ist bereits Urlaub eingetragen.")));

        mvc.perform(post("/api/zeiterfassung/nachtrag").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BESTAETIGUNG_NOETIG"))
                .andExpect(jsonPath("$.hinweise[0]").value("Am 03.10.2026 ist bereits Urlaub eingetragen."))
                .andExpect(jsonPath("$.message").value("Am 03.10.2026 ist bereits Urlaub eingetragen."));

        org.mockito.Mockito.doReturn(new Ergebnis(Map.of("id", 101), List.of("Am 03.10.2026 ist bereits Urlaub eingetragen."))).when(service).erfasse(any());
        mvc.perform(post("/api/zeiterfassung/nachtrag").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("}", ",\"bestaetigt\":true}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hinweise[0]").exists());
    }

    @Test
    @DisplayName("Ändern, Löschen und Tagesstatus")
    void aendernLoeschenStatus() throws Exception {
        given(service.aendere(eq(5L), any())).willReturn(new Ergebnis(Map.of("id", 5), List.of()));
        given(service.status("tok", LocalDate.of(2026, 10, 3))).willReturn(Map.of("gesperrt", false, "arbeitMinuten", 120));

        mvc.perform(put("/api/zeiterfassung/nachtrag/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"start\":\"09:00\",\"ende\":\"11:00\",\"grund\":\"Zeit falsch\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.buchung.id").value(5));
        mvc.perform(delete("/api/zeiterfassung/nachtrag/5").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\",\"grund\":\"Doppelt\"}"))
                .andExpect(status().isNoContent());
        verify(service).storniere("tok", 5L, "Doppelt");
        mvc.perform(get("/api/zeiterfassung/nachtrag/tok/tag").param("datum", "2026-10-03"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gesperrt").value(false)).andExpect(jsonPath("$.arbeitMinuten").value(120));
    }

    @Test
    @DisplayName("Ablehnungen (400, 403, 409) kommen mit ihrem Status in der App an")
    void ablehnungen() throws Exception {
        given(service.erfasse(any())).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Der Monat Oktober 2026 ist abgeschlossen."));
        given(service.status(eq("falsch"), any())).willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig."));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung.")).when(service).storniere(any(), any(), any());

        mvc.perform(post("/api/zeiterfassung/nachtrag").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        mvc.perform(get("/api/zeiterfassung/nachtrag/falsch/tag").param("datum", "2026-10-03")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/zeiterfassung/nachtrag/5").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\",\"grund\":\"x\"}")).andExpect(status().isForbidden());
    }
}
