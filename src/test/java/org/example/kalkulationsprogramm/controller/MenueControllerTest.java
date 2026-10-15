package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.example.kalkulationsprogramm.service.MenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MenueControllerTest {

    private final MenueService dienst = mock(MenueService.class);
    private MockMvc mvc;
    private UsernamePasswordAuthenticationToken max;

    @BeforeEach
    void setUp() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(7L, "max", "Max Mustermann", "x", true, Set.of(FrontendUserRole.USER));
        max = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(dienst.vorgabe()).thenReturn(Optional.empty());
        when(dienst.eigene(7L)).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new MenueController(dienst)).build();
    }

    @Test
    void ohneEintragSindBeideLeer() throws Exception {
        mvc.perform(get("/api/menue").principal(max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vorgabe").doesNotExist())
                .andExpect(jsonPath("$.eigene").doesNotExist());
    }

    @Test
    void zeigtVorgabeUndEigeneAuswahlDesAngemeldetenBenutzers() throws Exception {
        when(dienst.vorgabe()).thenReturn(Optional.of(List.of("/werkstoffe")));
        when(dienst.eigene(7L)).thenReturn(Optional.of(List.of("/lager")));
        mvc.perform(get("/api/menue").principal(max))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vorgabe[0]").value("/werkstoffe"))
                .andExpect(jsonPath("$.eigene[0]").value("/lager"));
    }

    @Test
    void ohneAnmeldungGibt401() throws Exception {
        mvc.perform(get("/api/menue")).andExpect(status().isUnauthorized());
    }

    @Test
    void eigeneAuswahlSpeichertNurFuerDenAngemeldetenBenutzer() throws Exception {
        mvc.perform(put("/api/menue/eigene").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"ausgeblendet\":[\"/lager\"]}"))
                .andExpect(status().isOk());
        verify(dienst).setzeEigene(eq(7L), eq(List.of("/lager")));
    }

    @Test
    void ungueltigeAuswahlGibt400() throws Exception {
        doThrow(new IllegalArgumentException("Ungültiger Menüpunkt.")).when(dienst).setzeEigene(eq(7L), anyList());
        mvc.perform(put("/api/menue/eigene").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"ausgeblendet\":[\"<script>\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eigeneAuswahlZuruecksetzen() throws Exception {
        mvc.perform(delete("/api/menue/eigene").principal(max)).andExpect(status().isOk());
        verify(dienst).loescheEigene(7L);
    }

    @Test
    void vorgabeSpeichernUndZuruecksetzen() throws Exception {
        mvc.perform(put("/api/settings/menue/vorgabe").principal(max).contentType(MediaType.APPLICATION_JSON).content("{\"ausgeblendet\":[\"/werkstoffe\"]}"))
                .andExpect(status().isOk());
        verify(dienst).setzeVorgabe(List.of("/werkstoffe"));

        mvc.perform(delete("/api/settings/menue/vorgabe").principal(max)).andExpect(status().isOk());
        verify(dienst).loescheVorgabe();
    }

    @Test
    void ohneAnmeldungWirdNichtsGespeichert() throws Exception {
        mvc.perform(put("/api/settings/menue/vorgabe").contentType(MediaType.APPLICATION_JSON).content("{\"ausgeblendet\":[]}"))
                .andExpect(status().isUnauthorized());
        verify(dienst, never()).setzeVorgabe(anyList());
    }
}
