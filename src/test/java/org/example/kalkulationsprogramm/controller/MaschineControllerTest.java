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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Maschine.MaschineDto;
import org.example.kalkulationsprogramm.service.MaschineService;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Aufstellung;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@WebMvcTest(MaschineController.class)
@AutoConfigureMockMvc(addFilters = false)
class MaschineControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private MaschineService service;

    private static MaschineDto maschine() {
        Aufstellung a = new Aufstellung(new BigDecimal("4.00"), new BigDecimal("1.10"), new BigDecimal("1.00"), new BigDecimal("0.50"), new BigDecimal("0.20"), new BigDecimal("3.00"), new BigDecimal("9.80"), null);
        return new MaschineDto(5L, "Schweißanlage", null, null, null, null, null, null, null, null, null, null, null, null, new BigDecimal("9.80"), false, true, null, a,
                List.of(new MaschineDto.ArbeitsgangKurz(3L, "Schweißen")));
    }

    @Test
    void listeUndRechnerLiefernDieDaten() throws Exception {
        given(service.alle()).willReturn(List.of(maschine()));
        given(service.rechne(any())).willReturn(maschine().aufstellung());

        mvc.perform(get("/api/maschinen")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Schweißanlage"))
                .andExpect(jsonPath("$[0].kostensatz").value(9.80)).andExpect(jsonPath("$[0].arbeitsgaenge[0].beschreibung").value("Schweißen"));
        mvc.perform(post("/api/maschinen/rechner").contentType(MediaType.APPLICATION_JSON).content("{\"leistungKw\":20,\"strompreisJeKwh\":0.30,\"auslastungProzent\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summe").value(9.80)).andExpect(jsonPath("$.energie").value(3.00));
    }

    @Test
    void anlegenUndAendernGebenDenBenutzerFuerDenVerlaufWeiter() throws Exception {
        given(service.anlegen(any(), any())).willReturn(maschine());
        given(service.aendern(eq(5L), any(), any())).willReturn(maschine());

        mvc.perform(post("/api/maschinen").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Schweißanlage\",\"satzVonHand\":12.5,\"arbeitsgangIds\":[3]}")
                .principal(new org.springframework.security.authentication.TestingAuthenticationToken("max", "x"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(put("/api/maschinen/5").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Schweißanlage\",\"satzVonHand\":13}")
                .principal(new org.springframework.security.authentication.TestingAuthenticationToken("max", "x"))).andExpect(status().isOk());
        verify(service).anlegen(any(), eq("max"));
    }

    @Test
    void eineEingabeOhneNamenOderMitNegativenZahlenWirdMitKlartextAbgelehnt() throws Exception {
        mvc.perform(post("/api/maschinen").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/maschinen").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Säge\",\"satzVonHand\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/maschinen").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Säge\",\"laufstundenJeJahr\":9000}")).andExpect(status().isBadRequest());
    }

    @Test
    void verlaufUndLoeschenUndFehlerDesDienstes() throws Exception {
        given(service.verlauf(5L)).willReturn(List.of(new MaschineDto.Verlauf(new BigDecimal("9.80"), false, LocalDate.of(2026, 10, 8), "max", LocalDateTime.of(2026, 10, 8, 9, 0))));
        mvc.perform(get("/api/maschinen/5/verlauf")).andExpect(status().isOk()).andExpect(jsonPath("$[0].geaendertVon").value("max"));

        mvc.perform(delete("/api/maschinen/5")).andExpect(status().isNoContent());
        org.mockito.BDDMockito.willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Die Maschine wird von 1 Arbeitsgang genutzt.")).given(service).loeschen(6L);
        mvc.perform(delete("/api/maschinen/6")).andExpect(status().isConflict());
    }
}
