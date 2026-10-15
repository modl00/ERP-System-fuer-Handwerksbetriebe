package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto;
import org.example.kalkulationsprogramm.service.AppAnlegenService;
import org.example.kalkulationsprogramm.service.AppAnlegenService.KundeKurz;
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

@WebMvcTest(ZeiterfassungAnlegenController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeiterfassungAnlegenControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private AppAnlegenService service;

    private static final String KUNDE = "{\"token\":\"tok\",\"kunde\":{\"name\":\"Max Mustermann\",\"ort\":\"Musterstadt\"}}";

    @Test
    @DisplayName("Kunde anlegen: 201 mit Nummer; das Bestätigen von Duplikaten wird weitergereicht")
    void kunde() throws Exception {
        given(service.legeKundeAn(eq("tok"), any(), eq(false))).willReturn(new KundeKurz(42L, "Max Mustermann", "K-2001", "Musterstadt", null));

        mvc.perform(post("/api/zeiterfassung/anlegen/kunden").contentType(MediaType.APPLICATION_JSON).content(KUNDE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(42)).andExpect(jsonPath("$.kundennummer").value("K-2001"));
        mvc.perform(post("/api/zeiterfassung/anlegen/kunden").contentType(MediaType.APPLICATION_JSON).content(KUNDE.replace("}}", "},\"bestaetigt\":true}")))
                .andExpect(status().isCreated());
        verify(service).legeKundeAn(eq("tok"), any(), eq(true));
    }

    @Test
    @DisplayName("Mögliche Duplikate: 409 mit Code DUPLIKAT und den Treffern")
    void duplikat() throws Exception {
        KundeDuplikatTrefferDto t = new KundeDuplikatTrefferDto();
        t.setId(7L);
        t.setName("Max Mustermann GmbH");
        given(service.legeKundeAn(any(), any(), eq(false))).willThrow(new KundeDuplikatException(new KundeDuplikatResponseDto(List.of(t), true)));

        mvc.perform(post("/api/zeiterfassung/anlegen/kunden").contentType(MediaType.APPLICATION_JSON).content(KUNDE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLIKAT")).andExpect(jsonPath("$.duplikate[0].name").value("Max Mustermann GmbH"))
                .andExpect(jsonPath("$.harterTreffer").value(true));
    }

    @Test
    @DisplayName("Ohne Namen oder ohne Berechtigung: Ablehnung mit dem Status des Servers")
    void ablehnungen() throws Exception {
        mvc.perform(post("/api/zeiterfassung/anlegen/kunden").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\",\"kunde\":{\"name\":\"\"}}"))
                .andExpect(status().isBadRequest());
        given(service.legeKundeAn(any(), any(), eq(false))).willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung."));

        mvc.perform(post("/api/zeiterfassung/anlegen/kunden").contentType(MediaType.APPLICATION_JSON).content(KUNDE)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anfrage anlegen: 201 mit der neuen Anfrage; die Angaben gehen an den Dienst")
    void anfrage() throws Exception {
        AnfrageResponseDto neu = new AnfrageResponseDto();
        neu.setId(55L);
        given(service.legeAnfrageAn(eq("tok"), any())).willReturn(neu);

        mvc.perform(post("/api/zeiterfassung/anlegen/anfragen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"bauvorhaben\":\"Geländer\",\"kundenId\":42,\"betrag\":1500}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(55));
    }
}
