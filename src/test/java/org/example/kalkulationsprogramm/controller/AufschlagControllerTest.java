package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.AufschlagService.KategorieZeile;
import org.example.kalkulationsprogramm.service.AufschlagService.Quelle;
import org.example.kalkulationsprogramm.service.AufschlagService.Uebersicht;
import org.example.kalkulationsprogramm.service.AufschlagService.Wirksam;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AufschlagControllerTest {

    private final AufschlagService service = mock(AufschlagService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AufschlagController(service)).build();

    @Test
    void uebersichtSetzenUndWirksamerAufschlagAmArtikel() throws Exception {
        var u = new Uebersicht(new BigDecimal("20.00"), List.of(new KategorieZeile(2, 1, "Stahl › Rohre", null, new BigDecimal("25.00"), Quelle.KATEGORIE, "Stahl")));
        when(service.uebersicht()).thenReturn(u);
        when(service.setzeAllgemein(new BigDecimal("12.5"))).thenReturn(u);
        when(service.setzeKategorie(2, null)).thenReturn(u);
        when(service.wirksamerFuerArtikel(7L)).thenReturn(new Wirksam(new BigDecimal("25.00"), Quelle.KATEGORIE, "Stahl"));

        mvc.perform(get("/api/aufschlaege")).andExpect(status().isOk()).andExpect(jsonPath("$.allgemein").value(20.0))
                .andExpect(jsonPath("$.kategorien[0].pfad").value("Stahl › Rohre")).andExpect(jsonPath("$.kategorien[0].wirksamVon").value("Stahl"));
        mvc.perform(put("/api/aufschlaege/allgemein").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":12.5}")).andExpect(status().isOk());
        mvc.perform(put("/api/aufschlaege/kategorien/2").contentType(MediaType.APPLICATION_JSON).content("{\"prozent\":null}")).andExpect(status().isOk());
        mvc.perform(get("/api/aufschlaege/artikel/7")).andExpect(status().isOk()).andExpect(jsonPath("$.quelle").value("KATEGORIE")).andExpect(jsonPath("$.quellenName").value("Stahl"));

        verify(service).setzeAllgemein(new BigDecimal("12.5"));
        verify(service).setzeKategorie(2, null);
    }
}
