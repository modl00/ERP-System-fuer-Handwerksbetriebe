package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.StartbildschirmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StartbildschirmControllerTest {

    private final StartbildschirmService dienst = mock(StartbildschirmService.class);
    private MockMvc mvc;
    private UsernamePasswordAuthenticationToken max;

    @BeforeEach
    void setUp() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(7L, "max", "Max Mustermann", "x", true, Set.of(FrontendUserRole.USER));
        max = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(dienst.vorgabe()).thenReturn(Optional.empty());
        when(dienst.eigene(7L)).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new StartbildschirmController(dienst)).build();
    }

    @Test
    void ohneEintragSindBeideLeerUndOhneAnmeldungGibtEs401() throws Exception {
        mvc.perform(get("/api/startbildschirm").principal(max)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vorgabe").doesNotExist()).andExpect(jsonPath("$.eigene").doesNotExist());
        mvc.perform(get("/api/startbildschirm")).andExpect(status().isUnauthorized());
    }

    @Test
    void zeigtVorgabeUndEigeneLeisteDesAngemeldetenBenutzers() throws Exception {
        when(dienst.vorgabe()).thenReturn(Optional.of(List.of("plantafel")));
        when(dienst.eigene(7L)).thenReturn(Optional.of(List.of("lager", "aufgaben")));
        mvc.perform(get("/api/startbildschirm").principal(max)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vorgabe[0]").value("plantafel")).andExpect(jsonPath("$.eigene[1]").value("aufgaben"));
    }

    @Test
    void eigeneLeisteWirdNurFuerDenAngemeldetenBenutzerGespeichertUndZurueckgesetzt() throws Exception {
        mvc.perform(put("/api/startbildschirm/eigene").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"eintraege\":[\"lager\"]}")).andExpect(status().isOk());
        verify(dienst).setzeEigene(eq(7L), eq(List.of("lager")));
        mvc.perform(delete("/api/startbildschirm/eigene").principal(max)).andExpect(status().isOk());
        verify(dienst).loescheEigene(7L);
    }

    @Test
    void dieVorgabeFuerAlleUndUngueltigeEingabeAls400() throws Exception {
        mvc.perform(put("/api/settings/startbildschirm/vorgabe").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"eintraege\":[\"plantafel\"]}")).andExpect(status().isOk());
        verify(dienst).setzeVorgabe(eq(List.of("plantafel")));
        doThrow(new IllegalArgumentException("Ungültiger Menüpunkt.")).when(dienst).setzeEigene(eq(7L), anyList());
        mvc.perform(put("/api/startbildschirm/eigene").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"eintraege\":[\"<x>\"]}")).andExpect(status().isBadRequest());
    }
}
