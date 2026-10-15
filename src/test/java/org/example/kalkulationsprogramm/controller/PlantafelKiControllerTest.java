package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsKi;
import org.example.kalkulationsprogramm.service.planung.PlanungsKi.KettenVorschlag;
import org.example.kalkulationsprogramm.service.planung.PlanungsKi.VorschlagSchritt;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.Schritt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** KI-Schnittstelle der Plantafel: Vorschlag lesen, Auswahl übernehmen, Fehler als 400 mit Klartext. Nur Dummy-Daten. */
class PlantafelKiControllerTest {

    private final PlanungsKi ki = mock(PlanungsKi.class);
    private final PlanungsService planung = mock(PlanungsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelKiController(ki, planung)).build();

    @Test
    void derVorschlagKommtMitBegruendung() throws Exception {
        when(ki.kettenVorschlag(7L)).thenReturn(new KettenVorschlag("ANGEBOT", List.of(new VorschlagSchritt(2L, "Fertigung", "#be123c", 5, "Schweißen")), List.of()));

        mvc.perform(get("/api/plantafel/ki/auftraege/7/vorgaenge")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quelle").value("ANGEBOT")).andExpect(jsonPath("$.schritte[0].typName").value("Fertigung")).andExpect(jsonPath("$.schritte[0].dauerTage").value(5));
    }

    @Test
    void nurDieAusgewaehltenSchritteWerdenUebernommen() throws Exception {
        when(planung.kettePerAuswahl(7L, List.of(new Schritt(2L, 4)))).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, null, null, "OHNE_ZIEL", List.of()));

        mvc.perform(post("/api/plantafel/ki/auftraege/7/vorgaenge/uebernehmen").contentType(MediaType.APPLICATION_JSON).content("{\"schritte\":[{\"typId\":2,\"dauerTage\":4}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projektId").value(7));
        verify(planung).kettePerAuswahl(7L, List.of(new Schritt(2L, 4)));
    }

    @Test
    void einFehlerDerKiKommtAls400MitKlartext() throws Exception {
        when(ki.erklaeren(7L)).thenThrow(new IllegalArgumentException("Für diese Funktion ist kein Schlüssel hinterlegt."));

        mvc.perform(get("/api/plantafel/ki/auftraege/7/erklaerung")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Für diese Funktion ist kein Schlüssel hinterlegt."));
    }
}
