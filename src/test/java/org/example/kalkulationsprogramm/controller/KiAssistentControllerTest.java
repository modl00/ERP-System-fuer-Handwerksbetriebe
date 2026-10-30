package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEinstellung;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiFunktion.Datenschutz;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Antwort;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Ergebnis;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Quelle;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Vorschlag;
import org.example.kalkulationsprogramm.service.ki.assistent.KiKontext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

class KiAssistentControllerTest {

    private final KiAssistentService assistent = mock(KiAssistentService.class);
    private final KiAnbieterService anbieterService = mock(KiAnbieterService.class);
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    private static Authentication benutzer(Set<FrontendUserRole> rollen) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(7L, "anna", "Anna Büro", "x", true, rollen);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new KiAssistentController(assistent, anbieterService)).build();
    }

    @Test
    void chatGibtAntwortVorschlaegeUndQuelleZurueckUndNutztDenAngemeldetenBenutzer() throws Exception {
        when(assistent.chat(any(), any(), any(), any())).thenReturn(new Antwort("Es gibt 12 m.", List.of(new Vorschlag("v1", "artikel_anlegen", "Artikel \"X\" anlegen", json.createObjectNode().put("name", "X"))),
                new Quelle("Lokale KI (Ollama u. a.)", "qwen2.5:7b", true), List.of("lagerbestand")));

        mvc.perform(post("/api/ki-assistent/chat").principal(benutzer(Set.of(FrontendUserRole.USER))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"text\":\"Wie viel Rundrohr?\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Es gibt 12 m."))
                .andExpect(jsonPath("$.vorschlaege[0].id").value("v1"))
                .andExpect(jsonPath("$.vorschlaege[0].beschreibung").value("Artikel \"X\" anlegen"))
                .andExpect(jsonPath("$.quelle.lokal").value(true))
                .andExpect(jsonPath("$.genutzteWerkzeuge[0]").value("lagerbestand"));
        ArgumentCaptor<KiKontext> k = ArgumentCaptor.forClass(KiKontext.class);
        verify(assistent).chat(k.capture(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(k.getValue()).isEqualTo(new KiKontext(7L, "anna", false));
    }

    @Test
    void administratorenWerdenErkannt() throws Exception {
        when(assistent.chat(any(), any(), any(), any())).thenReturn(new Antwort("ok", List.of(), new Quelle("x", "m", true), List.of()));

        mvc.perform(post("/api/ki-assistent/chat").principal(benutzer(Set.of(FrontendUserRole.ADMIN))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"text\":\"Hallo\"}]}")).andExpect(status().isOk());

        ArgumentCaptor<KiKontext> k = ArgumentCaptor.forClass(KiKontext.class);
        verify(assistent).chat(k.capture(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(k.getValue().admin()).isTrue();
    }

    @Test
    void ohneBenutzerAnmeldungArbeitetDerAssistentNicht() throws Exception {
        try {
            mvc.perform(post("/api/ki-assistent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"));
            org.junit.jupiter.api.Assertions.fail("erwartet: Zugriff verweigert");
        } catch (jakarta.servlet.ServletException e) {
            org.assertj.core.api.Assertions.assertThat(e.getCause()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }

    @Test
    void anbieterFehlerKommenAls503MitKlartext() throws Exception {
        when(assistent.chat(any(), any(), any(), any())).thenThrow(new KiAnbieterFehler("Für „KI-Assistent“ sind nur lokale KI-Anbieter erlaubt (Datenschutz)."));

        mvc.perform(post("/api/ki-assistent/chat").principal(benutzer(Set.of(FrontendUserRole.USER))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"text\":\"Hallo\"}]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Für „KI-Assistent“ sind nur lokale KI-Anbieter erlaubt (Datenschutz)."));
    }

    @Test
    void ungueltigeAnfragenKommenAls400() throws Exception {
        when(assistent.chat(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("Die letzte Nachricht muss vom Benutzer sein"));

        mvc.perform(post("/api/ki-assistent/chat").principal(benutzer(Set.of(FrontendUserRole.USER))).contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Die letzte Nachricht muss vom Benutzer sein"));
    }

    @Test
    void bestaetigenUndVerwerfen() throws Exception {
        when(assistent.bestaetige(any(), eq("v1"))).thenReturn(new Ergebnis(true, "Artikel angelegt."));
        when(assistent.bestaetige(any(), eq("alt"))).thenReturn(new Ergebnis(false, "Diesen Vorschlag gibt es nicht mehr."));
        when(assistent.verwerfe(any(), eq("v2"))).thenReturn(new Ergebnis(true, "verworfen"));
        when(assistent.verwerfe(any(), eq("alt"))).thenReturn(new Ergebnis(false, "weg"));

        mvc.perform(post("/api/ki-assistent/vorschlaege/v1/bestaetigen").principal(benutzer(Set.of(FrontendUserRole.USER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meldung").value("Artikel angelegt."));
        mvc.perform(post("/api/ki-assistent/vorschlaege/alt/bestaetigen").principal(benutzer(Set.of(FrontendUserRole.USER)))).andExpect(status().isConflict());
        mvc.perform(post("/api/ki-assistent/vorschlaege/v2/verwerfen").principal(benutzer(Set.of(FrontendUserRole.USER)))).andExpect(status().isOk());
        mvc.perform(post("/api/ki-assistent/vorschlaege/alt/verwerfen").principal(benutzer(Set.of(FrontendUserRole.USER)))).andExpect(status().isNotFound());
    }

    @Test
    void statusZeigtBereitschaftUndDatenschutz() throws Exception {
        when(anbieterService.einstellung(KiFunktion.ASSISTENT)).thenReturn(new FunktionsEinstellung("assistent", "KI-Assistent", AnbieterTyp.LOKAL, Datenschutz.NUR_LOKAL));

        mvc.perform(get("/api/ki-assistent/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bereit").value(true))
                .andExpect(jsonPath("$.lokal").value(true))
                .andExpect(jsonPath("$.datenschutz").value("NUR_LOKAL"));
    }

    @Test
    void statusErklaertWarumDerAssistentNichtBereitIst() throws Exception {
        when(anbieterService.einstellung(KiFunktion.ASSISTENT)).thenReturn(new FunktionsEinstellung("assistent", "KI-Assistent", AnbieterTyp.LOKAL, Datenschutz.NUR_LOKAL));
        when(anbieterService.waehle(KiFunktion.ASSISTENT)).thenThrow(new KiAnbieterFehler("Die lokale KI ist noch nicht eingerichtet."));

        mvc.perform(get("/api/ki-assistent/status"))
                .andExpect(jsonPath("$.bereit").value(false))
                .andExpect(jsonPath("$.meldung").value("Die lokale KI ist noch nicht eingerichtet."));
    }
}
