package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Das Fenster bei der Auftragsbestätigung über die Schnittstelle. Nur Dummy-Daten. */
class PlantafelAufnahmeControllerTest {

    private final PlanungsAufnahme aufnahme = mock(PlanungsAufnahme.class);
    private final PlanungsService planung = mock(PlanungsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelAufnahmeController(aufnahme, planung))
            .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(
                    tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
                            .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .build()))
            .build();

    @Test
    void dasFensterBringtDieVorausgefuelltenAngaben() throws Exception {
        when(aufnahme.aufnahme(7L)).thenReturn(new PlanungsAufnahme.Aufnahme(7L, "Geländer", "A-1", false, false, "AUFTRAGSBESTAETIGUNG",
                List.of(new PlanungsAufnahme.FremdleistungDto(3L, "Feuerverzinken", 21L, "Verzinkerei Muster", "kg", new BigDecimal("250.50"), 6)), List.of(), List.of(), new BigDecimal("8"), true));

        mvc.perform(get("/api/plantafel/auftraege/7/aufnahme")).andExpect(status().isOk()).andExpect(jsonPath("$.bezeichnung").value("Geländer"))
                .andExpect(jsonPath("$.fremdleistungen[0].lieferantName").value("Verzinkerei Muster")).andExpect(jsonPath("$.fremdleistungen[0].lieferzeitTage").value(6));
    }

    @Test
    void planenNimmtDieAngabenAlsKoerperAn() throws Exception {
        when(aufnahme.planen(eq(7L), any())).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, LocalDate.of(2026, 12, 1), null, "OHNE_ZIEL", List.of()));

        mvc.perform(post("/api/plantafel/auftraege/7/planen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaeter\":false,\"zielDatum\":\"2026-12-01\",\"prioritaet\":\"DRINGEND\",\"fremdleistungen\":[{\"fremdleistungId\":3,\"bezeichnung\":\"Feuerverzinken\",\"lieferantId\":21,\"menge\":250.5,\"einheit\":\"kg\"}],\"montageVorOrt\":true,\"montageTage\":3,\"mitarbeiterIds\":[4]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zielDatum").value("2026-12-01"));
        verify(aufnahme).planen(7L, new PlanungsAufnahme.Eingabe(false, LocalDate.of(2026, 12, 1), "DRINGEND",
                List.of(new PlanungsAufnahme.FremdleistungEingabe(3L, "Feuerverzinken", 21L, new BigDecimal("250.5"), "kg")), true, 3, List.of(4L)));
    }

    @Test
    void einEingabefehlerKommtAls400MitKlartext() throws Exception {
        when(aufnahme.planen(eq(7L), any())).thenThrow(new IllegalArgumentException("Dieser Auftrag hat schon Vorgänge in der Plantafel."));

        mvc.perform(post("/api/plantafel/auftraege/7/planen").contentType(MediaType.APPLICATION_JSON).content("{\"spaeter\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dieser Auftrag hat schon Vorgänge in der Plantafel."));
    }

    @Test
    void offeneAuftraegeUndDasAbschaltenDesFensters() throws Exception {
        when(planung.offeneAuftraege()).thenReturn(List.of(new PlanungsService.OffenerAuftrag(7L, "Geländer", "A-1")));
        when(aufnahme.fensterAktiv()).thenReturn(true);
        when(aufnahme.fensterSetzen(false)).thenReturn(false);

        mvc.perform(get("/api/plantafel/offene-planung")).andExpect(status().isOk()).andExpect(jsonPath("$[0].projektId").value(7)).andExpect(jsonPath("$[0].auftragsnummer").value("A-1"));
        mvc.perform(get("/api/plantafel/ab-fenster")).andExpect(status().isOk()).andExpect(jsonPath("$.aktiv").value(true));
        mvc.perform(put("/api/plantafel/ab-fenster").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.aktiv").value(false));
        verify(aufnahme).fensterSetzen(false);
    }
}
