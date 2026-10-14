package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.ArtikelArchivService;
import org.example.kalkulationsprogramm.service.ArtikelArchivService.Geloescht;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Archivieren darf jeder Angemeldete, endgültig löschen nur ein Administrator; alles nur mit CSRF-Token. */
@WebMvcTest(controllers = ArtikelArchivController.class)
@Import({ SecurityConfig.class, ArtikelArchivControllerSecurityTest.EchteFilterBeans.class })
class ArtikelArchivControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ArtikelArchivService service;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private FrontendUserProfileRepository frontendUserProfileRepository;

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(post("/api/artikel/1/archivieren").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/artikel/1/wiederherstellen").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/artikel/1").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("Ohne CSRF-Token: 403, auch beim Archivieren")
    void ohneCsrf() throws Exception {
        mockMvc.perform(post("/api/artikel/1/archivieren")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/artikel/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "anna.buero")
    @DisplayName("Ein normaler Benutzer darf archivieren und wiederherstellen, aber nicht endgültig löschen")
    void normalerBenutzer() throws Exception {
        given(service.archiviere(eq(1L), eq(true), eq("anna.buero"))).willReturn(3);

        mockMvc.perform(post("/api/artikel/1/archivieren").param("mitVarianten", "true").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.anzahl").value(3));
        mockMvc.perform(post("/api/artikel/1/wiederherstellen").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/artikel/1").with(csrf())).andExpect(status().isForbidden());

        verify(service).stelleWiederHer(1L);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).loesche(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ein Administrator darf endgültig löschen")
    void administrator() throws Exception {
        given(service.loesche(1L)).willReturn(new Geloescht(true, 2));

        mockMvc.perform(delete("/api/artikel/1").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.alsPlatzhalter").value(true)).andExpect(jsonPath("$.projekte").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Das Löschen eines Dokuments (zwei Pfadteile) bleibt für alle erlaubt und läuft nicht in die Admin-Regel")
    void dokumentLoeschenBleibtOffen() throws Exception {
        // Die Admin-Regel gilt nur für /api/artikel/{id}; ein Pfad mit zwei Teilen gehört dem Dokument-Controller (hier nicht geladen: 404, nicht 403)
        mockMvc.perform(delete("/api/artikel/dokumente/5").with(csrf())).andExpect(status().isNotFound());
    }
}
