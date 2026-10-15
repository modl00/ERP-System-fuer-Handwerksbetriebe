package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiModellService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class KiModellControllerTest {

    @Mock KiModellService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new KiModellController(service)).build();
    }

    private static KiModellService.Uebersicht uebersicht() {
        return new KiModellService.Uebersicht(List.of(new KiModellService.FunktionsModell("ki-hilfe", "KI-Hilfe (Chat)", "gemini-a", "gemini-a", false)), true, true);
    }

    @Test
    void uebersichtLiefertFunktionenUndWebsuche() throws Exception {
        when(service.uebersicht()).thenReturn(uebersicht());

        mvc.perform(get("/api/settings/ki-modelle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funktionen[0].schluessel").value("ki-hilfe"))
                .andExpect(jsonPath("$.funktionen[0].modell").value("gemini-a"))
                .andExpect(jsonPath("$.websuche").value(true));
    }

    @Test
    void speichernReichtAuswahlUndWebsucheWeiter() throws Exception {
        when(service.speichern(any(), any())).thenReturn(uebersicht());

        mvc.perform(put("/api/settings/ki-modelle").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelle\":{\"ki-hilfe\":\"gemini-2.5-flash\"},\"websuche\":false}"))
                .andExpect(status().isOk());

        verify(service).speichern(eq(Map.of("ki-hilfe", "gemini-2.5-flash")), eq(false));
    }

    @Test
    void verfuegbareLiefertDieModelle() throws Exception {
        when(service.verfuegbare()).thenReturn(List.of(new KiModellService.VerfuegbaresModell("gemini-2.5-flash", "Gemini 2.5 Flash", "schnell")));

        mvc.perform(get("/api/settings/ki-modelle/verfuegbar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$[0].anzeigename").value("Gemini 2.5 Flash"));
    }

    @Test
    void testLiefertDasErgebnisAuchBeiKontingentFehler() throws Exception {
        when(service.teste("gemini-x")).thenReturn(new KiModellService.Testergebnis(false, "KONTINGENT", "Das Google-Kontingent ist erschöpft.", 429));

        mvc.perform(post("/api/settings/ki-modelle/test").contentType(MediaType.APPLICATION_JSON).content("{\"modell\":\"gemini-x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.art").value("KONTINGENT"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void fehlerDesDienstesKommenMitStatusAn() throws Exception {
        when(service.verfuegbare()).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Es ist noch kein Gemini API Key hinterlegt."));

        mvc.perform(get("/api/settings/ki-modelle/verfuegbar")).andExpect(status().isBadRequest());
    }
}
