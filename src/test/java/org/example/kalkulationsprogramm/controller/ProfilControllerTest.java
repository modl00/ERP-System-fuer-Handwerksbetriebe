package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ProfilTabelle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfilController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProfilTabelle.class)
class ProfilControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private WerkstoffRepository werkstoffe;

    @Test
    void formenUndTabelleKommenAusDerMitgeliefertenTabelle() throws Exception {
        mvc.perform(get("/api/artikel/profile/formen")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/artikel/profile/tabelle").param("profilform", "IPE_PROFIL")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bezeichnung=='IPE 120')].kgJeMeter").value(10.4))
                .andExpect(jsonPath("$[?(@.bezeichnung=='IPE 120')].hoeheMm").value(120));
        mvc.perform(get("/api/artikel/profile/tabelle").param("profilform", "BLECH")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rechnenNimmtDieDichteDesWerkstoffsUndMeldetFehlerMitKlartext() throws Exception {
        Werkstoff alu = new Werkstoff();
        alu.setDichte(new BigDecimal("2.7"));
        given(werkstoffe.findById(9L)).willReturn(Optional.of(alu));

        mvc.perform(post("/api/artikel/profile/rechnen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"profilform\":\"FLACHSTAB\",\"hoehe\":8,\"breite\":40,\"werkstoffId\":9}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kgJeMeter").value(0.864)).andExpect(jsonPath("$.m2JeMeter").value(0.096));
        mvc.perform(post("/api/artikel/profile/rechnen").contentType(MediaType.APPLICATION_JSON).content("{\"profilform\":\"FLACHSTAB\",\"hoehe\":8,\"breite\":40}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kgJeMeter").value(2.512));
        mvc.perform(post("/api/artikel/profile/rechnen").contentType(MediaType.APPLICATION_JSON).content("{\"profilform\":\"RUNDROHR\",\"durchmesser\":42.4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formelTestrechnungRechnetMitDerDichteDesWerkstoffsUndMeldetFehlerInKlartext() throws Exception {
        Werkstoff alu = new Werkstoff();
        alu.setDichte(new BigDecimal("2.7"));
        given(werkstoffe.findById(9L)).willReturn(Optional.of(alu));
        String felder = "\"felder\":[{\"name\":\"L\",\"bezeichnung\":\"Länge\",\"einheit\":\"mm\",\"standard\":1000},{\"name\":\"B\",\"bezeichnung\":\"Breite\",\"einheit\":\"mm\",\"standard\":500},{\"name\":\"T\",\"bezeichnung\":\"Dicke\",\"einheit\":\"mm\",\"standard\":10}]";

        mvc.perform(post("/api/artikel/profile/formel").contentType(MediaType.APPLICATION_JSON)
                .content("{" + felder + ",\"gewicht\":\"L*B*T/1000000*dichte\",\"flaeche\":\"L*B/1000000*2\",\"werkstoffId\":9}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kg").value(13.5)).andExpect(jsonPath("$.m2").value(1.0));
        mvc.perform(post("/api/artikel/profile/formel").contentType(MediaType.APPLICATION_JSON)
                .content("{" + felder + ",\"gewicht\":\"L*B*T/1000000*dichte\",\"werte\":{\"l\":2000}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kg").value(78.5)).andExpect(jsonPath("$.m2").doesNotExist());
        mvc.perform(post("/api/artikel/profile/formel").contentType(MediaType.APPLICATION_JSON).content("{" + felder + ",\"gewicht\":\"L*Q\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/artikel/profile/formel").contentType(MediaType.APPLICATION_JSON).content("{" + felder + "}"))
                .andExpect(status().isBadRequest());
    }
}
