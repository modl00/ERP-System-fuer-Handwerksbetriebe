package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Datenschutzstufe;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Eingriff;
import org.example.kalkulationsprogramm.service.ki.assistent.KiKontext;
import org.example.kalkulationsprogramm.service.ki.assistent.KiWerkzeug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(KiObergrenzeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(KiObergrenzeControllerTest.Werkzeuge.class)
class KiObergrenzeControllerTest {

    @TestConfiguration
    static class Werkzeuge {
        private static KiWerkzeug werkzeug(String name, boolean aendert, boolean loescht) {
            return new KiWerkzeug() {
                public String name() { return name; }
                public String beschreibung() { return name + " (Beschreibung)"; }
                public JsonNode parameter() { return new ObjectMapper().createObjectNode(); }
                public boolean aendert() { return aendert; }
                public boolean loescht() { return loescht; }
                public boolean verfuegbar(KiKontext k) { return true; }
                public String ausfuehren(JsonNode a, KiKontext k) { return ""; }
            };
        }

        @Bean KiWerkzeug suchen() { return werkzeug("artikel_suchen", false, false); }
        @Bean KiWerkzeug buchen() { return werkzeug("zeit_buchen", true, false); }
        @Bean KiWerkzeug loeschen() { return werkzeug("artikel_loeschen", true, true); }
    }

    @Autowired private MockMvc mvc;
    @MockBean private KiObergrenze obergrenze;

    @Test
    @DisplayName("Lesen: Stufen, Anbieter mit Vertrag und Werkzeuge; nur ändernde, nicht löschende Werkzeuge sind freischaltbar")
    void lesen() throws Exception {
        given(obergrenze.datenschutz()).willReturn(Datenschutzstufe.ALLGEMEIN);
        given(obergrenze.eingriff()).willReturn(Eingriff.ERWEITERT);
        given(obergrenze.freigeschalteteWerkzeuge()).willReturn(Set.of("zeit_buchen"));
        given(obergrenze.hatVertrag(org.example.kalkulationsprogramm.service.ki.AnbieterTyp.MISTRAL)).willReturn(true);

        mvc.perform(get("/api/settings/ki-obergrenze")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datenschutz").value("ALLGEMEIN")).andExpect(jsonPath("$.eingriff").value("ERWEITERT"))
                .andExpect(jsonPath("$.anbieter[?(@.typ=='MISTRAL')].vertrag").value(true))
                .andExpect(jsonPath("$.anbieter[?(@.typ=='LOKAL')].lokal").value(true))
                .andExpect(jsonPath("$.werkzeuge[?(@.name=='zeit_buchen')].freischaltbar").value(true))
                .andExpect(jsonPath("$.werkzeuge[?(@.name=='zeit_buchen')].freigegeben").value(true))
                .andExpect(jsonPath("$.werkzeuge[?(@.name=='artikel_suchen')].freischaltbar").value(false))
                .andExpect(jsonPath("$.werkzeuge[?(@.name=='artikel_loeschen')].freischaltbar").value(false));
    }

    @Test
    @DisplayName("Speichern: die Eingabe geht mit den freischaltbaren Werkzeugen an den Dienst")
    void speichern() throws Exception {
        given(obergrenze.datenschutz()).willReturn(Datenschutzstufe.LOKAL);
        given(obergrenze.eingriff()).willReturn(Eingriff.MIT_BESTAETIGUNG);
        given(obergrenze.freigeschalteteWerkzeuge()).willReturn(Set.of());

        mvc.perform(put("/api/settings/ki-obergrenze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datenschutz\":\"LOKAL\",\"eingriff\":\"MIT_BESTAETIGUNG\",\"vertrag\":{\"MISTRAL\":true},\"ohneRueckfrage\":[\"zeit_buchen\"]}"))
                .andExpect(status().isOk());

        verify(obergrenze).speichern(any(), eq(Set.of("zeit_buchen")), any(), any());
    }

    @Test
    @DisplayName("Ungültige Eingabe: 400 mit Klartext")
    void ablehnung() throws Exception {
        doThrow(new IllegalArgumentException("Unbekannte Stufe für Datenschutz: GEHEIM")).when(obergrenze).speichern(any(), any(), any(), any());

        mvc.perform(put("/api/settings/ki-obergrenze").contentType(MediaType.APPLICATION_JSON).content("{\"datenschutz\":\"GEHEIM\",\"eingriff\":\"AUS\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Unbekannte Stufe für Datenschutz: GEHEIM"));
    }
}
