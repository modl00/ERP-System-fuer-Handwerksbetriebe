package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.planung.PlanungsIst;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Die Zuordnung „Arbeitsgang → Vorgangsart“ für die Stempelung. Nur Dummy-Daten. */
class PlantafelZuordnungControllerTest {

    private final PlanungsIst ist = mock(PlanungsIst.class);
    private final PlanungsService planung = mock(PlanungsService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlantafelZuordnungController(ist, planung)).build();

    @Test
    void listetDieArbeitsgaengeMitIhrerZuordnung() throws Exception {
        when(ist.zuordnungen()).thenReturn(List.of(new PlanungsIst.Zuordnung(5L, "Schweißen", 2L, "Werkstatt", 30L, true)));

        mvc.perform(get("/api/plantafel/arbeitsgang-zuordnung")).andExpect(status().isOk()).andExpect(jsonPath("$[0].arbeitsgang").value("Schweißen"))
                .andExpect(jsonPath("$[0].typId").value(30)).andExpect(jsonPath("$[0].mehrdeutig").value(true));
    }

    @Test
    void zuordnenSpeichertUndRechnetDiePlantafelNeu() throws Exception {
        when(ist.zuordnungen()).thenReturn(List.of());

        mvc.perform(put("/api/plantafel/arbeitsgang-zuordnung").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitsgangId\":5,\"typId\":30}")).andExpect(status().isOk());

        InOrder reihenfolge = inOrder(ist, planung);
        reihenfolge.verify(ist).zuordnen(5L, 30L);
        reihenfolge.verify(planung).neuBerechnenAlle();
    }

    @Test
    void ohneArbeitsgangOderBeiFehlerKommt400MitKlartext() throws Exception {
        mvc.perform(put("/api/plantafel/arbeitsgang-zuordnung").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen Arbeitsgang angeben."));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Auf einen Wartevorgang wird nicht gestempelt.")).when(ist).zuordnen(5L, 31L);
        mvc.perform(put("/api/plantafel/arbeitsgang-zuordnung").contentType(MediaType.APPLICATION_JSON).content("{\"arbeitsgangId\":5,\"typId\":31}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Auf einen Wartevorgang wird nicht gestempelt."));
    }
}
