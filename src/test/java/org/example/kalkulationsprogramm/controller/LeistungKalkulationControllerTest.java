package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto;
import org.example.kalkulationsprogramm.service.LeistungKalkulationService;
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
class LeistungKalkulationControllerTest {

    @Mock LeistungKalkulationService service;
    MockMvc mvc;

    private static LeistungKalkulationDto dto() {
        return new LeistungKalkulationDto(7L, true, new BigDecimal("10"), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("132.00"), new BigDecimal("132.00"), false, true);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LeistungKalkulationController(service)).build();
    }

    @Test
    void ladenLiefertDieAufstellung() throws Exception {
        when(service.laden(7L)).thenReturn(dto());

        mvc.perform(get("/api/leistungen/7/kalkulation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leistungId").value(7))
                .andExpect(jsonPath("$.preisBerechnet").value(132.00))
                .andExpect(jsonPath("$.kalkuliert").value(true));
    }

    @Test
    void speichernNimmtDieZeilenAn() throws Exception {
        when(service.speichern(eq(7L), any(LeistungKalkulationSpeichernDto.class))).thenReturn(dto());

        mvc.perform(put("/api/leistungen/7/kalkulation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kalkuliert\":true,\"zuschlagProzent\":10,\"positionen\":[{\"typ\":\"ARTIKEL\",\"artikelId\":1,\"menge\":2.5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preisGespeichert").value(132.00));
    }

    @Test
    void vorschauLiefertDieAufstellung() throws Exception {
        when(service.vorschau(eq(7L), any(LeistungKalkulationSpeichernDto.class))).thenReturn(dto());

        mvc.perform(post("/api/leistungen/7/kalkulation/vorschau").contentType(MediaType.APPLICATION_JSON).content("{\"positionen\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preisBerechnet").value(132.00));
    }

    @Test
    void preisUebernehmen() throws Exception {
        when(service.preisUebernehmen(7L)).thenReturn(dto());

        mvc.perform(post("/api/leistungen/7/kalkulation/preis-uebernehmen")).andExpect(status().isOk());
    }

    @Test
    void fehlerAusDemDienstWerdenWeitergegeben() throws Exception {
        when(service.laden(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Leistung nicht gefunden."));
        when(service.speichern(eq(7L), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Menge muss größer als 0 sein."));

        mvc.perform(get("/api/leistungen/99/kalkulation")).andExpect(status().isNotFound());
        mvc.perform(put("/api/leistungen/7/kalkulation").contentType(MediaType.APPLICATION_JSON).content("{\"positionen\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ungueltigeIdOderKaputtesJsonIstEinClientFehler() throws Exception {
        mvc.perform(get("/api/leistungen/abc/kalkulation")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/leistungen/7/kalkulation").contentType(MediaType.APPLICATION_JSON).content("{kaputt"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/leistungen/7/kalkulation")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void speichernNimmtLosgroesseUndZeitenAn() throws Exception {
        when(service.speichern(eq(7L), any(LeistungKalkulationSpeichernDto.class))).thenReturn(dto());

        mvc.perform(put("/api/leistungen/7/kalkulation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kalkuliert\":true,\"losgroesse\":50,\"positionen\":[{\"typ\":\"ARBEIT\",\"arbeitsgangId\":5,\"ruestzeitMinuten\":30,\"bearbeitungszeitMinuten\":6.5}]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<LeistungKalkulationSpeichernDto> fang = ArgumentCaptor.forClass(LeistungKalkulationSpeichernDto.class);
        org.mockito.Mockito.verify(service).speichern(eq(7L), fang.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("50").compareTo(fang.getValue().losgroesse()));
        LeistungKalkulationSpeichernDto.Position z = fang.getValue().positionen().get(0);
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("30").compareTo(z.ruestzeitMinuten()));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("6.5").compareTo(z.bearbeitungszeitMinuten()));
    }
}
