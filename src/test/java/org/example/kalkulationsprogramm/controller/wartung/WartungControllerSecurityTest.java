package org.example.kalkulationsprogramm.controller.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.wartung.WartungKatalogService;
import org.example.kalkulationsprogramm.service.wartung.WartungObjektService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hält fest, dass die Wartungs-Endpunkte hinter dem normalen Login (Session und CSRF) liegen, dass
 * ungültige Eingaben abgewiesen werden, bevor ein Service etwas tut, und dass die Listen begrenzt sind.
 *
 * <p>Wichtig: Die Wartung gehört zur geschützten API-Kette ({@code /api/**}) und nicht zu den
 * Pfaden, die der mobilen Zeiterfassung ohne Anmeldung offenstehen.</p>
 */
@WebMvcTest(controllers = {WartungKatalogController.class, WartungObjektController.class, WartungProtokollController.class,
        WartungSammelberichtController.class, WartungModulAdminController.class, WartungAnlageController.class,
        org.example.kalkulationsprogramm.controller.ModulSchalterController.class})
@Import({SecurityConfig.class, WartungControllerSecurityTest.EchteFilterBeans.class})
class WartungControllerSecurityTest {

    /** Der Cloudflare-Filter muss eine echte Instanz sein, siehe FirmaControllerSecurityTest. */
    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WartungKatalogService katalogService;
    @MockBean
    private WartungObjektService objektService;
    @MockBean
    private WartungProtokollService protokollService;
    @MockBean
    private WartungProtokollPdfService pdfService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungSammelberichtService sammelberichtService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungModulAdminService modulAdminService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungAnlageService anlageService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungBerichtService berichtService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungMitarbeiterUnterschriftService unterschriftService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungBenutzerService benutzerService;
    @MockBean
    private org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService modulSchalter;
    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void modulEingeschaltet() {
        // Der Mock meldet sonst "ausgeschaltet" und die Sperre würde jede Anfrage abweisen
        when(modulSchalter.istAktiv()).thenReturn(true);
    }

    // ── Anmeldung und CSRF ──────────────────────────────────────────────────

    @Test
    @DisplayName("Ohne Anmeldung ist kein Wartungs-Endpunkt erreichbar")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(get("/api/wartung/module")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/protokolle")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/protokolle/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/protokolle/1/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/kunden/1/objekte")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wartung/protokolle").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/wartung/protokolle/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ABGESCHLOSSEN\"}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(katalogService, objektService, protokollService, pdfService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Schreibende Aufrufe ohne CSRF-Token werden abgelehnt")
    void ohneCsrfAbgelehnt() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wartung/protokolle/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ABGESCHLOSSEN\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wartung/tueren/1/kopieren")).andExpect(status().isForbidden());

        verifyNoInteractions(protokollService, objektService);
    }

    @Test
    @DisplayName("Ohne Anmeldung sind Sammelbericht und Modul-Editor nicht erreichbar")
    void sammelberichtUndEditorAnonym() throws Exception {
        mockMvc.perform(post("/api/wartung/sammelbericht/pdf").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"protokollIds\":[1]}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/admin/module")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/wartung/admin/module/1/aktiv").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aktiv\":false}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(sammelberichtService, modulAdminService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Der Modul-Editor ist für normale Benutzer gesperrt, lesend wie schreibend")
    void editorNurFuerAdmin() throws Exception {
        mockMvc.perform(get("/api/wartung/admin/module")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/wartung/admin/module/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/wartung/admin/globale-felder")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wartung/admin/module/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/wartung/admin/module/1").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wartung/admin/module/reihenfolge").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1]}")).andExpect(status().isForbidden());

        verifyNoInteractions(modulAdminService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ein Admin darf den Modul-Editor benutzen, ungültige Eingaben werden trotzdem abgewiesen")
    void editorFuerAdmin() throws Exception {
        when(modulAdminService.alleModule()).thenReturn(List.of());

        mockMvc.perform(get("/api/wartung/admin/module")).andExpect(status().isOk());
        mockMvc.perform(put("/api/wartung/admin/module/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/admin/module/reihenfolge").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}")).andExpect(status().isBadRequest());
        // Eine fehlende Liste hieße "alles löschen": Das Modul wird nur mit allen Listen gespeichert
        mockMvc.perform(put("/api/wartung/admin/module/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"kategorie\":\"BASIS\",\"aktiv\":true,\"standardIntervallMonate\":12}"))
                .andExpect(status().isBadRequest());
        verify(modulAdminService, never()).speichern(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Der Sammelbericht verlangt mindestens ein und höchstens 200 Protokolle und liefert ein PDF")
    void sammelberichtPruefungUndAusgabe() throws Exception {
        mockMvc.perform(post("/api/wartung/sammelbericht/pdf").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"protokollIds\":[]}")).andExpect(status().isBadRequest());
        String zuViele = java.util.stream.IntStream.rangeClosed(1, 201).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/wartung/sammelbericht/pdf").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"protokollIds\":[" + zuViele + "]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(sammelberichtService);

        when(sammelberichtService.erstelle(any())).thenReturn(new WartungProtokollPdfService.PdfDatei("Sammelbericht_Größe.pdf", "%PDF-1.4 test".getBytes()));
        mockMvc.perform(post("/api/wartung/sammelbericht/pdf").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"protokollIds\":[1,2],\"titel\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ein leerer Body deaktiviert kein Objekt und keine Tür")
    void leererAktivBodyWirdAbgelehnt() throws Exception {
        mockMvc.perform(put("/api/wartung/objekte/1/aktiv").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/tueren/1/aktiv").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"aktive\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(objektService);
    }

    // ── Feldeinsatz: Wartung, Projekt, Bericht, Unterschrift, Modulschalter ─

    @Test
    @DisplayName("Ohne Anmeldung sind die neuen Endpunkte der Wartung nicht erreichbar")
    void feldeinsatzEndpunkteAnonym() throws Exception {
        mockMvc.perform(get("/api/wartung/tueren/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/wartung/tueren/1/projekt").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/projekte/1/wartungen")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wartung/protokolle/1/bericht").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wartung/protokolle/1/pruefpunkte").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/wartung/protokolle/1/pruefpunkte/2").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wartung/unterschrift/meine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/features")).andExpect(status().isUnauthorized());

        verifyNoInteractions(anlageService, berichtService, unterschriftService, protokollService, modulSchalter);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Schreibende Aufrufe der neuen Endpunkte ohne CSRF-Token werden abgelehnt")
    void feldeinsatzEndpunkteOhneCsrf() throws Exception {
        mockMvc.perform(put("/api/wartung/tueren/1/projekt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wartung/protokolle/1/bericht")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/wartung/protokolle/1/pruefpunkte/2")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wartung/unterschrift/meine").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        verifyNoInteractions(anlageService, berichtService, unterschriftService, protokollService);
    }

    @Test
    @DisplayName("Ohne Anmeldung sind die Datenfeld-Endpunkte im Protokoll nicht erreichbar")
    void datenfeldEndpunkteAnonym() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/2/definition").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/wartung/protokolle/1/datenfelder/2").with(csrf())).andExpect(status().isUnauthorized());

        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Datenfelder ändern ohne CSRF-Token wird abgelehnt")
    void datenfeldEndpunkteOhneCsrf() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Baujahr\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/2/definition").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Neu\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/wartung/protokolle/1/datenfelder/2")).andExpect(status().isForbidden());

        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültige Eingaben bei Datenfeldern: 400 mit Meldung, kein Service-Aufruf (leer, zu lang, zu viele Möglichkeiten, unbekannte Art)")
    void datenfeldEingabepruefung() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='label')].message").value("Bitte das Datenfeld benennen."));
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"" + "x".repeat(201) + "\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Zustand\",\"feldtyp\":\"AUSWAHL\",\"optionen\":[" + "\"a\",".repeat(50) + "\"b\"]}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Zustand\",\"feldtyp\":\"AUSWAHL\",\"optionen\":[\"" + "o".repeat(201) + "\",\"b\"]}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Baujahr\",\"feldtyp\":\"ZAHL\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/2/definition").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/abc/definition").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Neu\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Gültige Datenfeld-Aufrufe erreichen den Dienst: anlegen 201, ändern 200, löschen 200")
    void datenfeldGueltigeAufrufe() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle/1/datenfelder").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Baujahr\",\"feldtyp\":\"JA_NEIN\",\"modulKey\":\"m_bst\"}")).andExpect(status().isCreated());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/2/definition").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Neu\",\"optionen\":[\"a\",\"b\"]}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/wartung/protokolle/1/datenfelder/2").with(csrf())).andExpect(status().isOk());

        verify(protokollService).datenfeldHinzufuegen(eq(1L), any());
        verify(protokollService).datenfeldDefinition(eq(1L), eq(2L), any());
        verify(protokollService).datenfeldLoeschen(1L, 2L);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültige Eingaben bei Prüfpunkt und Unterschrift: 400 mit Meldung, kein Service-Aufruf")
    void feldeinsatzEingabepruefung() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle/1/pruefpunkte").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='label')].message").value("Bitte den Prüfpunkt beschreiben."));
        mockMvc.perform(post("/api/wartung/protokolle/1/pruefpunkte").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/pruefpunkte/2/text").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/unterschrift/meine").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='bild')].message").value("Bitte zuerst unterschreiben."));
        mockMvc.perform(put("/api/wartung/unterschrift/meine").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bild\":\"" + "A".repeat(700_001) + "\"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(protokollService, unterschriftService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Der Bericht im Projekt wird mit 201 und Adresse des Dokuments zurückgegeben, ohne gespeicherte Unterschrift gibt es 204")
    void berichtUndMeineUnterschrift() throws Exception {
        when(berichtService.erstelleImProjekt(7L)).thenReturn(new org.example.kalkulationsprogramm.dto.wartung.WartungBerichtDto(900L, "Bericht.pdf", "/api/dokumente/x.pdf", null));
        when(benutzerService.mitarbeiterId(any())).thenReturn(3L);
        when(unterschriftService.finde(3L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/wartung/protokolle/7/bericht").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("/api/dokumente/x.pdf"));
        mockMvc.perform(get("/api/wartung/unterschrift/meine")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Alle Angemeldeten lesen den Modulschalter, ihn setzen darf nur ein Admin")
    void modulSchalterRechte() throws Exception {
        when(modulSchalter.istAktiv()).thenReturn(true);

        mockMvc.perform(get("/api/features")).andExpect(status().isOk()).andExpect(jsonPath("$.wartung").value(true));
        mockMvc.perform(put("/api/settings/features/wartung").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isForbidden());
        verify(modulSchalter, never()).setzeAktiv(anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ein Admin schaltet das Wartungsmodul aus und ein, ein leerer Body schaltet nichts")
    void modulSchalterAdmin() throws Exception {
        when(modulSchalter.istAktiv()).thenReturn(false);

        mockMvc.perform(put("/api/settings/features/wartung").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wartung").value(false));
        verify(modulSchalter).setzeAktiv(false);
        mockMvc.perform(put("/api/settings/features/wartung").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(modulSchalter, never()).setzeAktiv(true);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ist das Wartungsmodul ausgeschaltet, sind alle Wartungs-Endpunkte gesperrt (403), die Schalter-Abfrage bleibt offen")
    void ausgeschaltetesModulIstGesperrt() throws Exception {
        when(modulSchalter.istAktiv()).thenReturn(false);

        mockMvc.perform(get("/api/wartung/module")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Das Wartungsmodul ist ausgeschaltet."));
        mockMvc.perform(get("/api/wartung/protokolle")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/wartung/projekte/1/wartungen")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wartung/protokolle/1/bericht").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/features")).andExpect(status().isOk()).andExpect(jsonPath("$.wartung").value(false));

        verifyNoInteractions(katalogService, protokollService, anlageService, berichtService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ausgeschaltet heißt ausgeschaltet: Auch der Modul-Editor für Admins ist gesperrt")
    void ausgeschaltetesModulSperrtAuchAdmins() throws Exception {
        when(modulSchalter.istAktiv()).thenReturn(false);

        mockMvc.perform(get("/api/wartung/admin/module")).andExpect(status().isForbidden());
        verifyNoInteractions(modulAdminService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Eingeschaltet laufen die Wartungs-Endpunkte wie bisher")
    void eingeschaltetesModulLaeuft() throws Exception {
        when(modulSchalter.istAktiv()).thenReturn(true);
        when(katalogService.aktiveModule()).thenReturn(List.of());

        mockMvc.perform(get("/api/wartung/module")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ein angemeldeter Benutzer darf den Katalog lesen")
    void benutzerDarfKatalogLesen() throws Exception {
        when(katalogService.aktiveModule()).thenReturn(List.of());

        mockMvc.perform(get("/api/wartung/module")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Das PDF kommt als application/pdf, im Browser geöffnet, ohne Zwischenspeicher und mit nosniff")
    void pdfDownload() throws Exception {
        when(pdfService.erstelle(7L)).thenReturn(new WartungProtokollPdfService.PdfDatei("Wartungsprotokoll_WA-2026-1001.pdf", "%PDF-1.4 test".getBytes()));

        mockMvc.perform(get("/api/wartung/protokolle/7/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("inline"), org.hamcrest.Matchers.containsString("Wartungsprotokoll_WA-2026-1001.pdf"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().bytes("%PDF-1.4 test".getBytes()));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ein PDF zu einer Nicht-Zahl als Id wird abgewiesen")
    void pdfMitUngueltigerId() throws Exception {
        mockMvc.perform(get("/api/wartung/protokolle/abc/pdf")).andExpect(status().isBadRequest());

        verifyNoInteractions(pdfService);
    }

    // ── Eingabeprüfung ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Protokoll anlegen ohne Kunde: 400 mit Meldung zum Feld, Service wird nicht aufgerufen (die Module prüft der Service, denn mit einer Vorlage sind sie nicht nötig)")
    void anlegenOhnePflichtfelder() throws Exception {
        mockMvc.perform(post("/api/wartung/protokolle").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='kundeId')].message").value("Bitte einen Kunden wählen."));

        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Überlange und ungültige Eingaben beim Anlegen werden abgewiesen")
    void anlegenMitUngueltigenWerten() throws Exception {
        String ueberlang = "A".repeat(10_001);
        String modul = "\"module\":[{\"modulId\":1,\"untertypSchluessel\":\"einflg\"}]";

        for (String body : List.of(
                "{\"kundeId\":1,\"titel\":\"" + ueberlang + "\"," + modul + "}",
                "{\"kundeId\":1,\"bemerkungen\":\"" + ueberlang + "\"," + modul + "}",
                "{\"kundeId\":1,\"intervallMonate\":0," + modul + "}",
                "{\"kundeId\":1,\"intervallMonate\":-5," + modul + "}",
                "{\"kundeId\":1,\"intervallMonate\":121," + modul + "}",
                "{\"kundeId\":1,\"module\":[{\"untertypSchluessel\":\"einflg\"}]}",
                "{\"kundeId\":\"abc\"," + modul + "}",
                "{\"kundeId\":1,\"datum\":\"31.12.2026\"," + modul + "}",
                "kein json")) {
            mockMvc.perform(post("/api/wartung/protokolle").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Objekt und Tür: leere oder überlange Bezeichnung wird abgewiesen")
    void objektUndTuerPflichtfelder() throws Exception {
        for (String bezeichnung : List.of("", "   ", "A".repeat(201))) {
            mockMvc.perform(post("/api/wartung/kunden/1/objekte").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bezeichnung\":\"" + bezeichnung + "\"}")).andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/wartung/objekte/1/tueren").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bezeichnung\":\"" + bezeichnung + "\"}")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/wartung/objekte/1/tueren").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bezeichnung\":\"Tür\",\"intervallMonate\":9999}")).andExpect(status().isBadRequest());
        verifyNoInteractions(objektService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültiger Status, ungültige Rolle und ungültiges Ergebnis: 400")
    void ungueltigeEnumWerte() throws Exception {
        mockMvc.perform(put("/api/wartung/protokolle/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"QUATSCH\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/unterschrift").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rolle\":\"CHEF\",\"bild\":null,\"name\":\"x\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/unterschrift").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bild\":null,\"name\":\"x\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/pruefpunkte/2").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ergebnis\":\"VIELLEICHT\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/pruefpunkte/2").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ergebnis\":\"IO\",\"bemerkung\":\"" + "A".repeat(10_001) + "\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/wartung/protokolle/1/datenfelder/2").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"wert\":\"" + "A".repeat(5_001) + "\"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(protokollService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Keine Zahl als ID in der URL: 400, kein Servicezugriff")
    void idKeineZahl() throws Exception {
        mockMvc.perform(get("/api/wartung/protokolle/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/wartung/kunden/abc/objekte")).andExpect(status().isBadRequest());

        verifyNoInteractions(protokollService, objektService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ungültige Filterwerte in der Liste: 400")
    void ungueltigeFilter() throws Exception {
        mockMvc.perform(get("/api/wartung/protokolle?status=QUATSCH")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/wartung/protokolle?datumVon=gestern")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/wartung/protokolle?kundeId=abc")).andExpect(status().isBadRequest());

        verifyNoInteractions(protokollService);
    }

    // ── Begrenzung der Liste ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Die Seitengröße der Liste ist auf 100 begrenzt, negative Seiten werden zu 0")
    void seitengroesseBegrenzt() throws Exception {
        when(protokollService.suche(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/wartung/protokolle?groesse=100000&seite=-4")).andExpect(status().isOk());
        mockMvc.perform(get("/api/wartung/protokolle?groesse=0")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        verify(protokollService, org.mockito.Mockito.times(2))
                .suche(any(), any(), any(), any(), eq(false), any(), any(), any(), seite.capture());
        assertThat(seite.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(seite.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(seite.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Die Liste reicht Suchtext unverändert weiter, die Maskierung macht der Service")
    void suchtextWirdWeitergereicht() throws Exception {
        when(protokollService.suche(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/wartung/protokolle").param("suche", "'; DROP TABLE x; --")).andExpect(status().isOk());

        verify(protokollService).suche(any(), any(), any(), any(), eq(false), any(), any(), eq("'; DROP TABLE x; --"), any(Pageable.class));
    }
}
