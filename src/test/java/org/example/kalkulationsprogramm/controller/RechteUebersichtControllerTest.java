package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto;
import org.example.kalkulationsprogramm.service.RechteUebersichtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RechteUebersichtController.class)
@AutoConfigureMockMvc(addFilters = false)
class RechteUebersichtControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private RechteUebersichtService service;

    @Test
    @DisplayName("Liefert Bereiche, Benutzer mit Feldern und Warnungen als JSON")
    void uebersicht() throws Exception {
        given(service.uebersicht()).willReturn(new RechteUebersichtDto(
                List.of(new RechteUebersichtDto.Bereich("TELEFON", "Anrufe und Anrufbeantworter", "Dokumente und Telefon", "Anrufliste ansehen.")),
                List.of(new RechteUebersichtDto.Benutzer(1L, "Max Mustermann", "max", false, 5L, "Max Mustermann", List.of("Büro"),
                        Map.of("TELEFON", new RechteUebersichtDto.Feld("ALLES", "Ja", "Über die Abteilung Büro.")))),
                List.of(new RechteUebersichtDto.Warnung("KEIN_ADMIN", "Es gibt keinen aktiven Administrator.", "/benutzer", null))));

        mvc.perform(get("/api/admin/rechte-uebersicht")).andExpect(status().isOk())
                .andExpect(jsonPath("$.bereiche[0].name").value("Anrufe und Anrufbeantworter"))
                .andExpect(jsonPath("$.benutzer[0].felder.TELEFON.stufe").value("ALLES"))
                .andExpect(jsonPath("$.benutzer[0].abteilungen[0]").value("Büro"))
                .andExpect(jsonPath("$.warnungen[0].ziel").value("/benutzer"));
    }
}
