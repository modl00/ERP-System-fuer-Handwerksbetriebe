package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Ergebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LesbareAblageControllerTest {

    @Mock LesbareAblageService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LesbareAblageController(service, true)).build();
    }

    private static Ergebnis ergebnis(boolean probe) {
        return new Ergebnis(probe, "/app/uploads/_lesbar", 12, 10, 2, 0, 1, 0, 3, List.of("Projekte/2026/x/A.pdf"), List.of());
    }

    @Test
    void statusZeigtZielordnerUndAutomatik() throws Exception {
        when(service.zielordner()).thenReturn(Path.of("/app/uploads/_lesbar"));

        mvc.perform(get("/api/admin/ablage/lesbar")).andExpect(status().isOk())
                .andExpect(jsonPath("$.zielordner").value("/app/uploads/_lesbar"))
                .andExpect(jsonPath("$.automatisch").value(true));
    }

    @Test
    void aufbauenLiefertDasErgebnis() throws Exception {
        when(service.aufbauen(false)).thenReturn(ergebnis(false));

        mvc.perform(post("/api/admin/ablage/lesbar/aufbauen")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dateien").value(12)).andExpect(jsonPath("$.neu").value(10))
                .andExpect(jsonPath("$.fehlend").value(3)).andExpect(jsonPath("$.beispiele[0]").value("Projekte/2026/x/A.pdf"));
    }

    @Test
    void probelaufWirdDurchgereicht() throws Exception {
        when(service.aufbauen(true)).thenReturn(ergebnis(true));

        mvc.perform(post("/api/admin/ablage/lesbar/aufbauen").param("probelauf", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.probelauf").value(true));
        verify(service).aufbauen(true);
    }

    @Test
    void laeuftSchonEinAufbauIstDasEinKonflikt() throws Exception {
        when(service.aufbauen(false)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Die lesbare Ablage wird gerade aufgebaut."));

        mvc.perform(post("/api/admin/ablage/lesbar/aufbauen")).andExpect(status().isConflict());
    }

    @Test
    void ungueltigeParameterUndMethodenSindClientFehler() throws Exception {
        mvc.perform(post("/api/admin/ablage/lesbar/aufbauen").param("probelauf", "vielleicht")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/ablage/lesbar/aufbauen")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/admin/ablage/lesbar")).andExpect(status().isMethodNotAllowed());
    }
}
