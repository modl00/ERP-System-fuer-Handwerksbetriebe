package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Urlaub;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Werte;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Einstellungen der Plantafel: Lesen, Speichern mit ISO-Wochentagen und Fehler als 400 mit Klartext. */
class PlantafelEinstellungenControllerTest {

    private final PlanungsEinstellungen service = mock(PlanungsEinstellungen.class);
    private final PlanungsService planung = mock(PlanungsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelEinstellungenController(service, planung))
            // wie im laufenden Programm: Daten als Text (2026-08-03), nicht als Zahlenliste
            .setMessageConverters(new JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
                    .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .build();

    @Test
    void lesenLiefertWochentageAlsZahlenUndDenBetriebsurlaub() throws Exception {
        when(service.lesen()).thenReturn(new Werte(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 2,
                List.of(new Urlaub(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 14), "Sommer"))));

        mvc.perform(get("/api/plantafel/einstellungen")).andExpect(status().isOk())
                .andExpect(jsonPath("$.arbeitstage[0]").value(1)).andExpect(jsonPath("$.arbeitstage[4]").value(5))
                .andExpect(jsonPath("$.ampelSchwelleTage").value(2))
                .andExpect(jsonPath("$.betriebsurlaub[0].von").value("2026-08-03")).andExpect(jsonPath("$.betriebsurlaub[0].bezeichnung").value("Sommer"));
    }

    @Test
    void speichernGibtDieZahlenAnDenServiceWeiter() throws Exception {
        when(service.speichern(any(), eq(3), any())).thenReturn(new Werte(EnumSet.of(DayOfWeek.SATURDAY), 3, List.of()));

        mvc.perform(put("/api/settings/planung").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arbeitstage\":[6],\"ampelSchwelleTage\":3,\"betriebsurlaub\":[{\"von\":\"2026-08-03\",\"bis\":\"2026-08-04\",\"bezeichnung\":\"x\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arbeitstage[0]").value(6));
        verify(service).speichern(eq(EnumSet.of(DayOfWeek.SATURDAY)), eq(3), eq(List.of(new Urlaub(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), "x"))));
        verify(planung).neuBerechnenAlle();
    }

    @Test
    void derRhythmusLiefertStundenUndBuerotageAlsZahlen() throws Exception {
        when(service.rhythmusLesen()).thenReturn(new PlanungsEinstellungen.Rhythmus(new java.math.BigDecimal("7.5"), java.util.Map.of(7L, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))));

        mvc.perform(get("/api/plantafel/rhythmus")).andExpect(status().isOk()).andExpect(jsonPath("$.arbeitstagStunden").value(7.5))
                .andExpect(jsonPath("$.buerotage.7[0]").value(1)).andExpect(jsonPath("$.buerotage.7[1]").value(5));
    }

    @Test
    void denRhythmusSpeichernRechnetDiePlantafelNeuUndMeldetFehlerAls400() throws Exception {
        when(service.rhythmusSpeichern(any(), any())).thenReturn(new PlanungsEinstellungen.Rhythmus(new java.math.BigDecimal("8"), java.util.Map.of(3L, EnumSet.of(DayOfWeek.MONDAY))));

        mvc.perform(put("/api/settings/planung/rhythmus").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitstagStunden\":8,\"buerotage\":{\"3\":[1]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.buerotage.3[0]").value(1));
        verify(service).rhythmusSpeichern(eq(new java.math.BigDecimal("8")), eq(java.util.Map.of(3L, EnumSet.of(DayOfWeek.MONDAY))));
        verify(planung).neuBerechnenAlle();

        mvc.perform(put("/api/settings/planung/rhythmus").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitstagStunden\":8,\"buerotage\":{\"3\":[9]}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Unbekannter Wochentag: 9"));
    }

    @Test
    void einFehlerKommtAls400MitKlartext() throws Exception {
        when(service.speichern(any(), eq(2), any())).thenThrow(new IllegalArgumentException("Mindestens ein Arbeitstag in der Woche ist nötig."));

        mvc.perform(put("/api/settings/planung").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitstage\":[],\"ampelSchwelleTage\":2,\"betriebsurlaub\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mindestens ein Arbeitstag in der Woche ist nötig."));
        mvc.perform(put("/api/settings/planung").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitstage\":[9],\"ampelSchwelleTage\":2,\"betriebsurlaub\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Unbekannter Wochentag: 9"));
    }
}
