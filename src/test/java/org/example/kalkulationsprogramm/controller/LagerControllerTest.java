package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerZusammenfassungDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.service.LagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LagerControllerTest {

    @Mock LagerService service;
    MockMvc mvc;

    private static LagerBestandDto zeile() {
        return new LagerBestandDto(5L, "Pfosten verzinkt", "A-1", "Stk", new BigDecimal("42"), new BigDecimal("10"), false, false,
                List.of(new LagerBestandDto.OrtBestand(1L, "Hauptlager", new BigDecimal("42"))), null, null, null);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LagerController(service)).build();
    }

    @Test
    void orteLiefernUndAnlegen() throws Exception {
        when(service.orte()).thenReturn(List.of(new LagerortDto(1L, "Hauptlager", null, true, 0, false, null)));
        when(service.ortAnlegen(any())).thenReturn(new LagerortDto(2L, "Fahrzeug", "Sprinter", true, 1, false, null));

        mvc.perform(get("/api/lager/orte")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Hauptlager"));
        mvc.perform(post("/api/lager/orte").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fahrzeug\",\"beschreibung\":\"Sprinter\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void aktivErwartetDenWertImBody() throws Exception {
        when(service.ortAktiv(eq(1L), anyBoolean())).thenReturn(new LagerortDto(1L, "Hauptlager", null, false, 0, false, null));

        mvc.perform(put("/api/lager/orte/1/aktiv").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aktiv").value(false));
        verify(service).ortAktiv(1L, false);
        mvc.perform(put("/api/lager/orte/1/aktiv").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/lager/orte/1/aktiv").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":\"vielleicht\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bestandReichtSucheUndFilterDurch() throws Exception {
        when(service.bestand("pfost", true)).thenReturn(List.of(zeile()));

        mvc.perform(get("/api/lager/bestand").param("q", "pfost").param("nurHandlungsbedarf", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Pfosten verzinkt"))
                .andExpect(jsonPath("$[0].orte[0].lagerortName").value("Hauptlager"));
    }

    @Test
    void buchenNimmtDieEingabeAn() throws Exception {
        when(service.buche(any())).thenReturn(zeile());

        mvc.perform(post("/api/lager/buchungen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"art\":\"ZUGANG\",\"artikelId\":5,\"lagerortId\":1,\"menge\":2.5,\"notiz\":\"Lieferschein 4711\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<LagerBuchungEingabe> e = ArgumentCaptor.forClass(LagerBuchungEingabe.class);
        verify(service).buche(e.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ZUGANG", e.getValue().art());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("2.5").compareTo(e.getValue().menge()));
        org.junit.jupiter.api.Assertions.assertEquals("Lieferschein 4711", e.getValue().notiz());
    }

    @Test
    void mindestbestandUndZusammenfassung() throws Exception {
        when(service.setzeMindestbestand(eq(5L), any())).thenReturn(zeile());
        when(service.zusammenfassung()).thenReturn(new LagerZusammenfassungDto(12, 3, 1));

        mvc.perform(put("/api/lager/artikel/5/mindestbestand").contentType(MediaType.APPLICATION_JSON).content("{\"mindestbestand\":10}"))
                .andExpect(status().isOk());
        verify(service).setzeMindestbestand(5L, new BigDecimal("10"));
        mvc.perform(get("/api/lager/zusammenfassung")).andExpect(status().isOk())
                .andExpect(jsonPath("$.unterMindestbestand").value(3)).andExpect(jsonPath("$.negativ").value(1));
    }

    @Test
    void fehlerDesDienstesUndKaputteEingabenSindClientFehler() throws Exception {
        when(service.buche(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Menge muss größer als 0 sein."));
        when(service.verlauf(99L)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Artikel existiert nicht."));

        mvc.perform(post("/api/lager/buchungen").contentType(MediaType.APPLICATION_JSON).content("{\"art\":\"ZUGANG\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lager/artikel/99/buchungen")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lager/artikel/abc/buchungen")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/lager/buchungen").contentType(MediaType.APPLICATION_JSON).content("{kaputt")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/lager/bestand")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void bestandEinesArtikels() throws Exception {
        when(service.bestandVon(5L)).thenReturn(zeile());

        mvc.perform(get("/api/lager/artikel/5/bestand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artikelId").value(5))
                .andExpect(jsonPath("$.orte[0].lagerortName").value("Hauptlager"));
    }
}
