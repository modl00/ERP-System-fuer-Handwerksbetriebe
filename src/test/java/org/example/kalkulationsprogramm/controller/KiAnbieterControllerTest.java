package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.AnbieterStand;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEinstellung;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.Testergebnis;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.Uebersicht;
import org.example.kalkulationsprogramm.service.ki.KiFunktion.Datenschutz;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KiAnbieterControllerTest {

    private final KiAnbieterService service = mock(KiAnbieterService.class);
    private final KiAktionProtokollRepository protokoll = mock(KiAktionProtokollRepository.class);
    private MockMvc mvc;

    private static Uebersicht uebersicht() {
        return new Uebersicht(List.of(new FunktionsEinstellung("assistent", "KI-Assistent", AnbieterTyp.LOKAL, Datenschutz.NUR_LOKAL)),
                List.of(new AnbieterStand(AnbieterTyp.CLAUDE, "Claude (Anthropic)", false, true, "claude-sonnet-5-5", true, true, true)), List.of("Scanner"));
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new KiAnbieterController(service, protokoll)).build();
    }

    @Test
    void uebersichtZeigtNurObEinSchluesselGesetztIst() throws Exception {
        when(service.uebersicht()).thenReturn(uebersicht());

        mvc.perform(get("/api/settings/ki-anbieter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funktionen[0].datenschutz").value("NUR_LOKAL"))
                .andExpect(jsonPath("$.anbieter[0].schluesselGesetzt").value(true))
                .andExpect(jsonPath("$.anbieter[0].schluessel").doesNotExist())
                .andExpect(jsonPath("$.nochGemini[0]").value("Scanner"));
    }

    @Test
    void speichernGibtAllesAnDenDienst() throws Exception {
        when(service.speichern(any(), any(), any())).thenReturn(uebersicht());

        mvc.perform(put("/api/settings/ki-anbieter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"funktionen\":{\"assistent\":{\"anbieter\":\"LOKAL\",\"datenschutz\":\"NUR_LOKAL\"}},\"claude\":{\"modell\":\"claude-sonnet-5-5\",\"schluessel\":null},\"mistral\":null}"))
                .andExpect(status().isOk());

        verify(service).speichern(eq(java.util.Map.of("assistent", new KiAnbieterService.FunktionsEingabe("LOKAL", "NUR_LOKAL"))),
                eq(new KiAnbieterService.AnbieterEingabe("claude-sonnet-5-5", null)), eq(null));
    }

    @Test
    void testMitModellReichtDasModellAnDenDienstWeiter() throws Exception {
        when(service.teste(AnbieterTyp.MISTRAL, "mistral-large-latest")).thenReturn(new Testergebnis(true, "OK", "Mistral antwortet"));

        mvc.perform(post("/api/settings/ki-anbieter/test").contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":\"MISTRAL\",\"modell\":\"mistral-large-latest\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(service).teste(AnbieterTyp.MISTRAL, "mistral-large-latest");
    }

    @Test
    void speichernReichtDasModellJeFunktionWeiter() throws Exception {
        when(service.speichern(any(), any(), any())).thenReturn(uebersicht());

        mvc.perform(put("/api/settings/ki-anbieter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"funktionen\":{\"email-text\":{\"anbieter\":\"MISTRAL\",\"datenschutz\":\"CLOUD_ERLAUBT\",\"modell\":\"mistral-large-latest\"}}}"))
                .andExpect(status().isOk());

        verify(service).speichern(eq(java.util.Map.of("email-text", new KiAnbieterService.FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "mistral-large-latest"))),
                eq(null), eq(null));
    }

    @Test
    void testRuftDenGewaehltenAnbieterAufUndLehntUnbekanntesAb() throws Exception {
        when(service.teste(AnbieterTyp.CLAUDE, null)).thenReturn(new Testergebnis(true, "OK", "Claude (Anthropic) antwortet"));

        mvc.perform(post("/api/settings/ki-anbieter/test").contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":\"CLAUDE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/api/settings/ki-anbieter/test").contentType(MediaType.APPLICATION_JSON).content("{\"anbieter\":\"QUATSCH\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/settings/ki-anbieter/test").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void protokollIstBegrenzt() throws Exception {
        KiAktionProtokoll p = new KiAktionProtokoll();
        p.setZeit(LocalDateTime.of(2026, 10, 4, 10, 0));
        p.setBenutzer("anna");
        p.setWerkzeug("artikel_suchen");
        p.setStatus("AUSGEFUEHRT");
        when(protokoll.findAllByOrderByZeitDescIdDesc(any())).thenReturn(List.of(p));

        mvc.perform(get("/api/settings/ki-protokoll?limit=100000")).andExpect(status().isOk()).andExpect(jsonPath("$[0].benutzer").value("anna"));
        mvc.perform(get("/api/settings/ki-protokoll?limit=-5")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        verify(protokoll, org.mockito.Mockito.times(2)).findAllByOrderByZeitDescIdDesc(seite.capture());
        org.assertj.core.api.Assertions.assertThat(seite.getAllValues().get(0).getPageSize()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(seite.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void modelleEinesAnbietersKommenAlsListe_UnbekanntIst404_FehlerDesAnbietersIst502MitKlartext() throws Exception {
        when(service.verfuegbareModelle(AnbieterTyp.CLAUDE)).thenReturn(List.of(new KiAnbieterService.Modell("claude-a", "Claude A")));
        when(service.verfuegbareModelle(AnbieterTyp.MISTRAL)).thenThrow(new org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler("Mistral lehnt den Zugang ab (Fehler 401)."));

        mvc.perform(get("/api/settings/ki-anbieter/claude/modelle")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("claude-a")).andExpect(jsonPath("$[0].anzeigename").value("Claude A"));
        mvc.perform(get("/api/settings/ki-anbieter/quatsch/modelle")).andExpect(status().isNotFound());
        mvc.perform(get("/api/settings/ki-anbieter/MISTRAL/modelle")).andExpect(status().isBadGateway())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().reason("Mistral lehnt den Zugang ab (Fehler 401)."));
    }
}
