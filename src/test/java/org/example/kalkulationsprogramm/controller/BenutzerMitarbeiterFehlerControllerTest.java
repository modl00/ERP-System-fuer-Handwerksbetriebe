package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.MitarbeiterService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Benutzer anlegen und löschen, Mitarbeiter löschen: Der Grund kommt als Meldung bei der Oberfläche an (#241). Nur Dummy-Daten. */
class BenutzerMitarbeiterFehlerControllerTest {

    private final FrontendUserProfileService profile = mock(FrontendUserProfileService.class);
    private final MitarbeiterService mitarbeiter = mock(MitarbeiterService.class);
    private final MockMvc benutzerMvc = MockMvcBuilders.standaloneSetup(new FrontendUserController(profile)).build();
    private final MockMvc mitarbeiterMvc = MockMvcBuilders.standaloneSetup(new MitarbeiterController(mitarbeiter, mock(org.example.kalkulationsprogramm.service.BelegService.class))).build();

    private static final String BENUTZER = "{\"displayName\":\"Max Mustermann\",\"username\":\"max\",\"password\":\"passwort123\",\"roles\":[\"USER\"],\"mitarbeiterId\":3}";

    @Test
    void einMitarbeiterDerSchonZuEinemBenutzerGehoertGibtEineVerstaendlicheMeldung() throws Exception {
        when(profile.saveOrUpdate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Der Mitarbeiter „Max Mustermann“ gehört schon zum Benutzer „Max“."));

        benutzerMvc.perform(post("/api/frontend-users").contentType(MediaType.APPLICATION_JSON).content(BENUTZER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Mitarbeiter „Max Mustermann“ gehört schon zum Benutzer „Max“."));
    }

    @Test
    void eineDatenbankKollisionBeimSpeichernWirdZumKonflikt() throws Exception {
        when(profile.saveOrUpdate(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("unique"));

        benutzerMvc.perform(post("/api/frontend-users").contentType(MediaType.APPLICATION_JSON).content(BENUTZER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("kollidieren")));
    }

    @Test
    void einBenutzerDerNichtGeloeschtWerdenKannNenntDenGrund() throws Exception {
        doThrow(new IllegalStateException("Dieser Benutzer hat schon Arbeit im Programm hinterlassen.")).when(profile).delete(5L);

        benutzerMvc.perform(delete("/api/frontend-users/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dieser Benutzer hat schon Arbeit im Programm hinterlassen."));
    }

    @Test
    void einMitarbeiterDerNichtGeloeschtWerdenKannNenntDenGrund() throws Exception {
        doThrow(new IllegalStateException("Dieser Mitarbeiter gehört zum Benutzer „Max“.")).when(mitarbeiter).delete(3L);

        mitarbeiterMvc.perform(delete("/api/mitarbeiter/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dieser Mitarbeiter gehört zum Benutzer „Max“."));
    }

    @Test
    void einMitarbeiterOhneHindernisWirdGeloescht() throws Exception {
        mitarbeiterMvc.perform(delete("/api/mitarbeiter/3")).andExpect(status().isNoContent());
    }
}
