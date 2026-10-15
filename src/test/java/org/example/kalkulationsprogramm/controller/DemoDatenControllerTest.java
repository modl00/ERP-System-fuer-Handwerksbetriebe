package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.demo.DemoDatenService;
import org.example.kalkulationsprogramm.service.demo.DemoDatenService.Ergebnis;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Demo-Daten entfernen: Status, Ergebnis, und ein Fehler beim Entfernen kommt als 409 mit Klartext. Nur Dummy-Daten. */
class DemoDatenControllerTest {

    private final DemoDatenService service = mock(DemoDatenService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoDatenController(service)).build();

    @Test
    void derStatusSagtObNochDemoDatenDaSind() throws Exception {
        when(service.vorhanden()).thenReturn(true);
        mvc.perform(get("/api/demo/status")).andExpect(status().isOk()).andExpect(jsonPath("$.vorhanden").value(true));
    }

    @Test
    void entfernenLiefertDieZahlen() throws Exception {
        when(service.entfernen()).thenReturn(new Ergebnis(8, 6, 3, 7, 8, 13));
        mvc.perform(delete("/api/demo/daten")).andExpect(status().isOk()).andExpect(jsonPath("$.mitarbeiter").value(8)).andExpect(jsonPath("$.auftraege").value(8)).andExpect(jsonPath("$.termine").value(13));
    }

    @Test
    void hangenEchteDatenDranKommtEs409MitKlartext() throws Exception {
        doThrow(new DataIntegrityViolationException("fk")).when(service).entfernen();
        mvc.perform(delete("/api/demo/daten")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Es wurde nichts entfernt")));
    }
}
