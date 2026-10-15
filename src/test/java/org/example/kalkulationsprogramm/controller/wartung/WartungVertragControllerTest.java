package org.example.kalkulationsprogramm.controller.wartung;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.example.kalkulationsprogramm.dto.wartung.WartungVertragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragSpeichernDto;
import org.example.kalkulationsprogramm.service.wartung.WartungVertragService;
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
class WartungVertragControllerTest {

    @Mock WartungVertragService service;
    MockMvc mvc;

    private static WartungVertragDto dto() {
        return new WartungVertragDto(1L, 3L, "WV-1", LocalDate.parse("2026-01-01"), null, 12, new BigDecimal("450.50"), "Notiz", "LAEUFT", false);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WartungVertragController(service)).build();
    }

    @Test
    void liefertDenVertragOder204() throws Exception {
        when(service.finde(3L)).thenReturn(Optional.of(dto()));
        when(service.finde(4L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/wartung/projekte/3/vertrag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vertragsnummer").value("WV-1"))
                .andExpect(jsonPath("$.status").value("LAEUFT"))
                .andExpect(jsonPath("$.preisNetto").value(450.50));
        mvc.perform(get("/api/wartung/projekte/4/vertrag")).andExpect(status().isNoContent());
    }

    @Test
    void speichernReichtDieAngabenAnDenDienstWeiter() throws Exception {
        when(service.speichern(eq(3L), any(WartungVertragSpeichernDto.class))).thenReturn(dto());

        mvc.perform(put("/api/wartung/projekte/3/vertrag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vertragsnummer\":\"WV-1\",\"beginn\":\"2026-01-01\",\"ende\":null,\"intervallMonate\":12,\"preisNetto\":450.5,\"notiz\":\"Notiz\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WartungVertragSpeichernDto> fang = ArgumentCaptor.forClass(WartungVertragSpeichernDto.class);
        verify(service).speichern(eq(3L), fang.capture());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.parse("2026-01-01"), fang.getValue().beginn());
        org.junit.jupiter.api.Assertions.assertEquals(12, fang.getValue().intervallMonate());
    }

    @Test
    void fehlerDesDienstesKommenMitStatusAn() throws Exception {
        when(service.speichern(eq(3L), any(WartungVertragSpeichernDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte den Beginn des Vertrags angeben."));

        mvc.perform(put("/api/wartung/projekte/3/vertrag").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loeschenAntwortetMit204() throws Exception {
        mvc.perform(delete("/api/wartung/projekte/3/vertrag")).andExpect(status().isNoContent());
        verify(service).loeschen(3L);
    }
}
