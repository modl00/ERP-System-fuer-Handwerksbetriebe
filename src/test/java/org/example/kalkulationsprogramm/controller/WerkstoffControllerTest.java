package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.service.WerkstoffService;
import org.example.kalkulationsprogramm.service.WerkstoffService.Eingabe;
import org.example.kalkulationsprogramm.service.WerkstoffService.Zeile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class WerkstoffControllerTest {

    private final WerkstoffService service = mock(WerkstoffService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WerkstoffController(service)).build();

    private static Zeile zeile() {
        return new Zeile(1L, "S235JR", "Baustahl", "Schwarzstahl", new BigDecimal("7.850"), "EN 10025", true, true, null, 2);
    }

    @Test
    void listeAnlegenAendernLoeschen() throws Exception {
        when(service.alle()).thenReturn(List.of(zeile()));
        when(service.anlegen(any())).thenReturn(zeile());
        when(service.aendern(eq(1L), any())).thenReturn(zeile());
        String body = "{\"name\":\"S235JR\",\"anzeigename\":\"Baustahl\",\"dichte\":7.85,\"verzinkungsgeeignet\":true}";

        mvc.perform(get("/api/werkstoffe")).andExpect(status().isOk()).andExpect(jsonPath("$[0].artikelAnzahl").value(2)).andExpect(jsonPath("$[0].dichte").value(7.85));
        mvc.perform(post("/api/werkstoffe").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("S235JR"));
        mvc.perform(put("/api/werkstoffe/1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(delete("/api/werkstoffe/1")).andExpect(status().isNoContent());

        verify(service).anlegen(new Eingabe("S235JR", "Baustahl", null, new BigDecimal("7.85"), null, true, null, null));
        verify(service).loeschen(1L);
    }

    @Test
    void ablehnungenKommenMitStatusAnDerOberflaecheAn() throws Exception {
        when(service.anlegen(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Einen Werkstoff mit dem Namen „S235JR“ gibt es schon."));

        mvc.perform(post("/api/werkstoffe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"S235JR\"}")).andExpect(status().isConflict());
    }
}
