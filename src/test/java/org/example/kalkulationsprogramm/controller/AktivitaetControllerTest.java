package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto;
import org.example.kalkulationsprogramm.service.AktivitaetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AktivitaetController.class)
@AutoConfigureMockMvc(addFilters = false)
class AktivitaetControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private AktivitaetService service;

    private static UsernamePasswordAuthenticationToken benutzer(Set<FrontendUserRole> rollen) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(5L, "max", "Max Mustermann", "x", true, rollen);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test
    @DisplayName("Mein Tagesverlauf: der angemeldete Benutzer, sein Datum, die Rolle; ohne Anmeldung 401")
    void mein() throws Exception {
        given(service.mein(5L, false, LocalDate.of(2026, 10, 3))).willReturn(new AktivitaetDto.MeinTag(LocalDate.of(2026, 10, 3), true, 365,
                List.of(new AktivitaetDto.Ereignis(1L, LocalDateTime.of(2026, 10, 3, 8, 15), "GEAENDERT", "Projekt geändert", "Projekt", 7L, "2026/014 Anbau Halle", null))));

        mvc.perform(get("/api/aktivitaet/mein?datum=2026-10-03").principal(benutzer(Set.of(FrontendUserRole.USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aktiv").value(true)).andExpect(jsonPath("$.ereignisse[0].text").value("Projekt geändert"))
                .andExpect(jsonPath("$.ereignisse[0].objektName").value("2026/014 Anbau Halle"));
        mvc.perform(get("/api/aktivitaet/mein")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Administrator-Sicht: Filter, Einstellungen lesen und speichern, Auskunft und Löschung einer Person")
    void admin() throws Exception {
        given(service.imSystem("Projekt", 7L, null, 30, null)).willReturn(List.of());
        given(service.einstellungen()).willReturn(new AktivitaetDto.Einstellungen(false, 365, 12));
        given(service.speichereEinstellungen(true, 0)).willReturn(new AktivitaetDto.Einstellungen(true, 0, 12));
        given(service.loescheVon(2L)).willReturn(5);
        given(service.auskunft(2L)).willReturn(List.of());

        mvc.perform(get("/api/admin/aktivitaet?objektTyp=Projekt&objektId=7&tage=30")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/aktivitaet/einstellungen")).andExpect(status().isOk()).andExpect(jsonPath("$.aufbewahrungTage").value(365));
        mvc.perform(put("/api/admin/aktivitaet/einstellungen").contentType(MediaType.APPLICATION_JSON).content("{\"mitarbeiterAktiv\":true,\"aufbewahrungTage\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mitarbeiterAktiv").value(true));
        mvc.perform(get("/api/admin/aktivitaet/person/2")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/aktivitaet/person/2")).andExpect(status().isOk()).andExpect(jsonPath("$.geloescht").value(5));
        verify(service).loescheVon(2L);
    }

    @Test
    @DisplayName("Ablehnung beim Speichern der Einstellungen kommt als 400 an")
    void ablehnung() throws Exception {
        given(service.speichereEinstellungen(any(), any())).willThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Die Aufbewahrung muss zwischen 0 und 36500 Tagen liegen."));

        mvc.perform(put("/api/admin/aktivitaet/einstellungen").contentType(MediaType.APPLICATION_JSON).content("{\"aufbewahrungTage\":-1}")).andExpect(status().isBadRequest());
    }
}
