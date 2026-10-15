package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangZeitenDto;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.service.ArbeitsgangManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArbeitsgangZeitenControllerTest {

    @Mock ArbeitsgangManagementService service;
    @Mock ArbeitsgangMapper mapper;
    @Mock AbteilungRepository abteilungen;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArbeitsgangController(service, mapper, abteilungen)).build();
    }

    @Test
    void zeitenSetzenLiefertDenArbeitsgangMitDenZeiten() throws Exception {
        Arbeitsgang gespeichert = new Arbeitsgang();
        ArbeitsgangResponseDto dto = new ArbeitsgangResponseDto();
        dto.setId(5L);
        dto.setBeschreibung("Schweißen");
        dto.setRuestzeitMinuten(new BigDecimal("30.00"));
        dto.setBearbeitungszeitMinuten(new BigDecimal("6.50"));
        when(service.aktualisiereZeiten(eq(5L), any(ArbeitsgangZeitenDto.class))).thenReturn(gespeichert);
        when(mapper.toArbeitsgangResponseDto(gespeichert)).thenReturn(dto);

        mvc.perform(put("/api/arbeitsgaenge/5/zeiten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruestzeitMinuten\":30,\"bearbeitungszeitMinuten\":6.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruestzeitMinuten").value(30.00))
                .andExpect(jsonPath("$.bearbeitungszeitMinuten").value(6.50));
    }

    @Test
    void falscheZeitenAntwortenMit400UndMeldung() throws Exception {
        when(service.aktualisiereZeiten(eq(5L), any(ArbeitsgangZeitenDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Rüstzeit muss zwischen 0 und 100.000 Minuten liegen."));

        mvc.perform(put("/api/arbeitsgaenge/5/zeiten").contentType(MediaType.APPLICATION_JSON).content("{\"ruestzeitMinuten\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void maschineSetzenLiefertDenArbeitsgangMitDerMaschine() throws Exception {
        Arbeitsgang gespeichert = new Arbeitsgang();
        ArbeitsgangResponseDto dto = new ArbeitsgangResponseDto();
        dto.setId(5L);
        dto.setMaschinenStundensatz(new BigDecimal("40.00"));
        dto.setMaschinenBezeichnung("Kappsäge");
        when(service.aktualisiereMaschine(eq(5L), any(org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangMaschineDto.class))).thenReturn(gespeichert);
        when(mapper.toArbeitsgangResponseDto(gespeichert)).thenReturn(dto);

        mvc.perform(put("/api/arbeitsgaenge/5/maschine").contentType(MediaType.APPLICATION_JSON).content("{\"bezeichnung\":\"Kappsäge\",\"stundensatz\":40}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maschinenStundensatz").value(40.00)).andExpect(jsonPath("$.maschinenBezeichnung").value("Kappsäge"));
    }
}
