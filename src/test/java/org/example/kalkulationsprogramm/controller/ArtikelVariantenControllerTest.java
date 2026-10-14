package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Kombination;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Reihen;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Status;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.VarianteZeile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ArtikelVariantenController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelVariantenControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ArtikelVariantenService service;

    @Test
    void grundartikelAnlegen() throws Exception {
        ArtikelWerkstoffe g = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(g, "id", 5L);
        g.setProduktname("Quadratrohr");
        g.setKurzbezeichnung("QH");
        when(service.legeGrundartikelAn(any())).thenReturn(g);

        mvc.perform(post("/api/artikel/grundartikel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kurzbezeichnung\":\"QH\",\"produktname\":\"Quadratrohr\",\"profilform\":\"QUADRATROHR\",\"verkaufsaufschlagProzent\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.kurzbezeichnung").value("QH"));
    }

    @Test
    void rasterVorschauUndAnlegenReichenDieReihenDurch() throws Exception {
        Kombination k = new Kombination("40|2", "QH 40x40x2", "QH-40x40x2", Status.NEU, null, new BigDecimal("2.3"), null);
        when(service.vorschau(eq(1L), any())).thenReturn(List.of(k));
        when(service.legeVariantenAn(eq(1L), any())).thenReturn(List.of(k));
        String body = "{\"a\":[\"40\"],\"b\":[\"2\"],\"ausgeschlossen\":[\"20|2\"]}";

        mvc.perform(post("/api/artikel/1/varianten/vorschau").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NEU")).andExpect(jsonPath("$[0].name").value("QH 40x40x2"));
        mvc.perform(post("/api/artikel/1/varianten").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(service).legeVariantenAn(1L, new Reihen(List.of("40"), List.of("2"), List.of("20|2")));
    }

    @Test
    void variantenListe() throws Exception {
        when(service.varianten(1L)).thenReturn(List.of(new VarianteZeile(9L, "QH 40x40x2", "QH-40x40x2", "40 x 40 x 2", new BigDecimal("2.3"), null, null)));

        mvc.perform(get("/api/artikel/1/varianten")).andExpect(status().isOk()).andExpect(jsonPath("$[0].artikelnummer").value("QH-40x40x2"));
    }

    @Test
    void fehlerKommenAlsKlartext() throws Exception {
        when(service.vorschau(eq(2L), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dieser Artikel ist kein Grundartikel."));
        when(service.legeGrundartikelAn(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Es gibt schon einen Grundartikel mit der Kurzbezeichnung QH."));

        mvc.perform(post("/api/artikel/2/varianten/vorschau").contentType(MediaType.APPLICATION_JSON).content("{\"a\":[],\"b\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dieser Artikel ist kein Grundartikel."));
        mvc.perform(post("/api/artikel/grundartikel").contentType(MediaType.APPLICATION_JSON).content("{\"kurzbezeichnung\":\"QH\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Es gibt schon einen Grundartikel mit der Kurzbezeichnung QH."));
    }

    @Test
    void rasterVorgabeDesGrundartikels() throws Exception {
        when(service.rasterVorgabe(1L)).thenReturn(new ArtikelVariantenService.Vorgabe("RUNDSTAB", "Rundstab", "Durchmesser (mm)", "zum Beispiel 8 10", false, null, null));

        mvc.perform(get("/api/artikel/1/varianten/raster")).andExpect(status().isOk())
                .andExpect(jsonPath("$.profilform").value("RUNDSTAB")).andExpect(jsonPath("$.achseB").doesNotExist());
    }

    @Test
    void formenListeNennenDieBenoetigtenMasse() throws Exception {
        when(service.vorgabe(any())).thenReturn(null);
        when(service.vorgabe(org.example.kalkulationsprogramm.domain.Profilform.QUADRATROHR))
                .thenReturn(new ArtikelVariantenService.Vorgabe("QUADRATROHR", "Quadratrohr", "Kantenlänge (mm)", "zum Beispiel 20 25", false, "Wandstärke (mm)", "zum Beispiel 2 3"));

        mvc.perform(get("/api/artikel/grundartikel/formen")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].achseA").value("Kantenlänge (mm)"));
    }
}
