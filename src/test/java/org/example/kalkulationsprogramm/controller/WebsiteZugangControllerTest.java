package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Beitraege.BeitragSummaryDto;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteClient;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteException;
import org.example.kalkulationsprogramm.service.WebsiteZugangService;
import org.example.kalkulationsprogramm.service.WebsiteZugangService.Konfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebsiteZugangControllerTest {

    private final WebsiteZugangService zugang = mock(WebsiteZugangService.class);
    private final BeitraegeWebsiteClient client = mock(BeitraegeWebsiteClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WebsiteZugangController(zugang, client)).build();
        when(zugang.konfiguration()).thenReturn(new Konfiguration("https://w.example.test", true, false, true));
    }

    @Test
    void konfigurationZeigtNurObEinSchluesselGesetztIst() throws Exception {
        mvc.perform(get("/api/settings/website-zugang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresse").value("https://w.example.test"))
                .andExpect(jsonPath("$.tokenGesetzt").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void speichernGibtDieWerteWeiter() throws Exception {
        when(zugang.speichern("https://w.example.test", null)).thenReturn(new Konfiguration("https://w.example.test", true, false, true));

        mvc.perform(put("/api/settings/website-zugang").contentType(MediaType.APPLICATION_JSON).content("{\"adresse\":\"https://w.example.test\"}"))
                .andExpect(status().isOk());
        verify(zugang).speichern(eq("https://w.example.test"), eq(null));
    }

    @Test
    void testErfolgZaehltDieBeitraege() throws Exception {
        when(client.listeAlle()).thenReturn(List.of(mock(BeitragSummaryDto.class), mock(BeitragSummaryDto.class)));

        mvc.perform(post("/api/settings/website-zugang/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.meldung").value("Die Website antwortet. Es gibt 2 Beiträge."));
    }

    @Test
    void abgelehnterSchluesselWirdErklaert() throws Exception {
        when(client.listeAlle()).thenThrow(new BeitraegeWebsiteException("HTTP 401: geheime Details der Gegenstelle", 401));

        mvc.perform(post("/api/settings/website-zugang/test"))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.art").value("KEIN_ZUGRIFF"))
                .andExpect(jsonPath("$.meldung").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("geheime"))));
    }

    @Test
    void falscheAdresseUndNichtErreichbarWerdenUnterschieden() throws Exception {
        when(client.listeAlle()).thenThrow(new BeitraegeWebsiteException("x", 404));
        mvc.perform(post("/api/settings/website-zugang/test")).andExpect(jsonPath("$.meldung").value(org.hamcrest.Matchers.containsString("404")));

        org.mockito.Mockito.doThrow(new BeitraegeWebsiteException("Netzwerkfehler beim Aufruf der Website-API: timeout")).when(client).listeAlle();
        mvc.perform(post("/api/settings/website-zugang/test"))
                .andExpect(jsonPath("$.art").value("NICHT_ERREICHBAR"))
                .andExpect(jsonPath("$.meldung").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("timeout"))));
    }

    @Test
    void ohneEinrichtungWirdNichtAufgerufen() throws Exception {
        when(zugang.konfiguration()).thenReturn(new Konfiguration("", false, false, false));

        mvc.perform(post("/api/settings/website-zugang/test"))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.art").value("NICHT_EINGERICHTET"));
        org.mockito.Mockito.verifyNoInteractions(client);
    }
}
