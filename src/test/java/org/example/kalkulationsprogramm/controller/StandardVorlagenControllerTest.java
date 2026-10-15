package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Formular.VorlagenEinstellungen;
import org.example.kalkulationsprogramm.service.StandardVorlagenService;
import org.example.kalkulationsprogramm.service.StandardVorlagenService.Ergebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StandardVorlagenControllerTest {

    @Mock StandardVorlagenService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StandardVorlagenController(service)).build();
    }

    @Test
    void anlegenLiefertDasErgebnis() throws Exception {
        when(service.anlegen()).thenReturn(new Ergebnis(List.of("Standard Rechnung"), List.of("Standard Mahnung"), 12));

        mvc.perform(post("/api/formulare/standardvorlagen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angelegteVorlagen[0]").value("Standard Rechnung"))
                .andExpect(jsonPath("$.vorhandeneVorlagen[0]").value("Standard Mahnung"))
                .andExpect(jsonPath("$.neueTextbausteine").value(12));
    }

    @Test
    void firmenPlatzhalterLiefertDieWerte() throws Exception {
        when(service.firmenPlatzhalter()).thenReturn(Map.of("FIRMENNAME", "Muster Metallbau GmbH"));

        mvc.perform(get("/api/formulare/firmen-platzhalter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FIRMENNAME").value("Muster Metallbau GmbH"));
    }

    @Test
    void firmenPlatzhalterNimmtKeineEingabeEntgegenUndFalscheMethodeIstAbgelehnt() throws Exception {
        // Es gibt keine Parameter, die man mit Schadtext füllen könnte; falsche Methoden werden abgewiesen
        mvc.perform(post("/api/formulare/firmen-platzhalter")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/formulare/standardvorlagen")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void standardEinstellungenLiefertNormalUndLieferschein() throws Exception {
        when(service.standardEinstellungen(false)).thenReturn(VorlagenEinstellungen.standard());
        when(service.standardEinstellungen(true)).thenReturn(VorlagenEinstellungen.lieferschein());

        mvc.perform(get("/api/formulare/layout-einstellungen/standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.einzelpreiseAusblenden").value(false))
                .andExpect(jsonPath("$.schriftart").value("HELVETICA"))
                .andExpect(jsonPath("$.betreff1Praefix").value("BV:"));
        mvc.perform(get("/api/formulare/layout-einstellungen/standard").param("art", "lieferschein"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.einzelpreiseAusblenden").value(true))
                .andExpect(jsonPath("$.artikelnummerAnzeigen").value(true));
    }

    @Test
    void layoutAusEinstellungenBereinigtDieWerteUndLiefertHtml() throws Exception {
        when(service.layoutAusEinstellungen(org.mockito.ArgumentMatchers.any())).thenReturn("<div class=\"page\"></div>");

        mvc.perform(post("/api/formulare/layout-aus-einstellungen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schriftart\":\"COMIC\",\"schriftgroesse\":400,\"unbekannt\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<div class=\"page\"></div>"))
                .andExpect(jsonPath("$.einstellungen.schriftart").value("HELVETICA"))
                .andExpect(jsonPath("$.einstellungen.schriftgroesse").value(14));
    }
}
