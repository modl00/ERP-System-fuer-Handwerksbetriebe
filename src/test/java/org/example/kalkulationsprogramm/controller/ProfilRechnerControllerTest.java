package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.service.ProfilBerechnungService;
import org.example.kalkulationsprogramm.service.ProfilRechner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ProfilRechnerController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfilRechnerControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ProfilBerechnungService berechnung;

    @Test
    void liefertGewichtQuerschnittUndOberflaeche() throws Exception {
        when(berechnung.vorschau(eq(Profilform.RUNDROHR), any(), eq(3L))).thenReturn(new ProfilRechner.Ergebnis(new BigDecimal("2.538"),
                new BigDecimal("1.9926"), null, new BigDecimal("0.1332"), ProfilRechner.Herkunft.BERECHNET, null));

        mvc.perform(post("/api/artikel/profilrechner").contentType(MediaType.APPLICATION_JSON)
                .content("{\"profilform\":\"RUNDROHR\",\"durchmesser\":42.4,\"wandstaerke\":2,\"werkstoffId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kgProMeter").value(1.9926))
                .andExpect(jsonPath("$.herkunft").value("BERECHNET"));
    }

    @Test
    void fehlendeMasseKommenAlsKlartextMitStatus400() throws Exception {
        when(berechnung.vorschau(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für Rundrohr fehlt die Wandstärke."));

        mvc.perform(post("/api/artikel/profilrechner").contentType(MediaType.APPLICATION_JSON).content("{\"profilform\":\"RUNDROHR\",\"durchmesser\":42.4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Für Rundrohr fehlt die Wandstärke."));
    }
}
