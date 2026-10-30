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

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsHistorie;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Plantafel-Schnittstelle: Eingabefehler kommen als 400 mit Klartext, Änderungen liefern den ganzen Auftrag. Nur Dummy-Daten. */
class PlantafelControllerTest {

    private final PlanungsService service = mock(PlanungsService.class);
    private final org.example.kalkulationsprogramm.service.planung.PlanungsHistorie historie = mock(org.example.kalkulationsprogramm.service.planung.PlanungsHistorie.class);
    // wie im Programm: Datumswerte als 2026-11-30, nicht als Zahlenliste
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelController(service, historie))
            .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(
                    tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
                            .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .build()))
            .build();

    @Test
    void eineAbhaengigkeitImKreisKommtAls400MitKlartext() throws Exception {
        when(service.abhaengigkeitSetzen(2L, 1L, 0)).thenThrow(new IllegalArgumentException("Die Vorgänge hängen im Kreis voneinander ab. Bitte eine Abhängigkeit entfernen."));

        mvc.perform(put("/api/plantafel/vorgaenge/2/vorgaenger/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die Vorgänge hängen im Kreis voneinander ab. Bitte eine Abhängigkeit entfernen."));
    }

    @Test
    void derPufferKommtAusDemKoerperUndFehltEr_istErNull() throws Exception {
        when(service.abhaengigkeitSetzen(eq(2L), eq(1L), eq(3))).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, null, null, "OHNE_ZIEL", List.of()));

        mvc.perform(put("/api/plantafel/vorgaenge/2/vorgaenger/1").contentType(MediaType.APPLICATION_JSON).content("{\"versatzTage\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projektId").value(7)).andExpect(jsonPath("$.ampel").value("OHNE_ZIEL"));
        verify(service).abhaengigkeitSetzen(2L, 1L, 3);
    }

    @Test
    void dieTermineWerdenAlsDatumGelesen() throws Exception {
        when(service.setzeTermine(any(), any(), any())).thenReturn(new AuftragDto(7L, "Geländer", "A-1", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30), null, "OHNE_ZIEL", List.of()));

        mvc.perform(put("/api/plantafel/auftraege/7/termine").contentType(MediaType.APPLICATION_JSON).content("{\"start\":\"2026-11-02\",\"ziel\":\"2026-11-30\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zielDatum").value("2026-11-30"));
        verify(service).setzeTermine(7L, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30));
    }

    @Test
    void dieUebersichtIstEineListe() throws Exception {
        when(service.auftraege()).thenReturn(List.of());
        mvc.perform(get("/api/plantafel/auftraege")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void derVorschlagKommtAlsListeMitAltUndNeu() throws Exception {
        when(service.vorschlag(7L)).thenReturn(new PlanungsService.VorschlagAntwort("ANGEBOT",
                List.of(new PlanungsService.DauerVorschlag(3L, "Fertigung", "Fertigung", 5, "GESCHAETZT", 3, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("24.00"), "Werkstatt", null)), List.of()));

        mvc.perform(get("/api/plantafel/auftraege/7/vorschlag")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quelle").value("ANGEBOT")).andExpect(jsonPath("$.zeilen[0].vorgeschlageneDauer").value(3)).andExpect(jsonPath("$.zeilen[0].aktuelleDauer").value(5));
    }

    @Test
    void dasUebernehmenSchicktNurDieNummernDerVorgaenge() throws Exception {
        when(service.vorschlagUebernehmen(eq(7L), any())).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, null, null, "OHNE_ZIEL", List.of()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/plantafel/auftraege/7/vorschlag/uebernehmen")
                .contentType(MediaType.APPLICATION_JSON).content("{\"vorgangIds\":[3,4]}")).andExpect(status().isOk());
        verify(service).vorschlagUebernehmen(7L, List.of(3L, 4L));
    }

    @Test
    void einKonfliktBeimSpeichernKommtAls409MitKlartext() throws Exception {
        when(service.fruehestensSetzen(2L, LocalDate.of(2026, 11, 2), 3)).thenThrow(new org.example.kalkulationsprogramm.service.planung.PlanKonfliktException("Der Vorgang wurde inzwischen geändert."));

        mvc.perform(put("/api/plantafel/vorgaenge/2/fruehestens").contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"2026-11-02\",\"stand\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Der Vorgang wurde inzwischen geändert."));
    }

    @Test
    void dieVorschauNimmtDieProbeAlsKoerperAn() throws Exception {
        when(service.vorschau(eq(2L), any())).thenReturn(new PlanungsService.Vorschau(List.of(new PlanungsService.AuftragAenderung(7L, "Geländer", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), 7, "OK", "VERSPAETET", 3, true)), List.of(), List.of("Hinweis")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/plantafel/vorgaenge/2/vorschau").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aufwandStunden\":32,\"fruehestensAb\":\"2026-11-02\",\"fruehestensAbSetzen\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auftraege[0].tage").value(7)).andExpect(jsonPath("$.auftraege[0].neuAmpel").value("VERSPAETET")).andExpect(jsonPath("$.warnungen[0]").value("Hinweis"));
        verify(service).vorschau(eq(2L), eq(new PlanungsService.Probe(new java.math.BigDecimal("32"), null, LocalDate.of(2026, 11, 2), true, null, false, null, null)));
    }

    @Test
    void dieAenderungsListeUndDasRueckgaengigGehenDurchDenController() throws Exception {
        when(historie.liste(20)).thenReturn(List.of(new PlanungsHistorie.Eintrag(5L, java.time.LocalDateTime.of(2026, 10, 12, 8, 30), "anna", "„Fertigung“: Aufwand von 24 auf 32 Stunden", 2L, 7L, false, true)));
        when(service.rueckgaengig(5L)).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, null, null, "OHNE_ZIEL", List.of()));

        mvc.perform(get("/api/plantafel/aenderungen")).andExpect(status().isOk()).andExpect(jsonPath("$[0].beschreibung").value("„Fertigung“: Aufwand von 24 auf 32 Stunden")).andExpect(jsonPath("$[0].rueckgaengigMoeglich").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/plantafel/aenderungen/5/rueckgaengig")).andExpect(status().isOk()).andExpect(jsonPath("$.projektId").value(7));
        verify(service).rueckgaengig(5L);
    }
}
