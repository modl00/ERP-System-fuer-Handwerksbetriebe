package org.example.kalkulationsprogramm.controller.wartung;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.service.wartung.AppWartungService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ZeiterfassungWartungControllerTest {

    private final AppWartungService service = mock(AppWartungService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ZeiterfassungWartungController(service)).build();

    @Test
    void derZugangslinkKommtAusDemKopfNieAusDerAdresse() throws Exception {
        when(service.anlagenDesProjekts("tok", 9L)).thenReturn(List.of(new WartungProjektWartungDto(7L, 4L, "Tür 1", "Haus 1", "Max Mustermann GmbH", "Brandschutztür", null, null, 0, null, null, List.of())));

        mvc.perform(get("/api/zeiterfassung/wartung/projekte/9/anlagen").header("X-Zugang", "tok")).andExpect(status().isOk()).andExpect(jsonPath("$[0].bezeichnung").value("Tür 1"));
        mvc.perform(get("/api/zeiterfassung/wartung/projekte/9/anlagen")).andExpect(status().isBadRequest());
    }

    @Test
    void startenBewertenStatusUndPdf() throws Exception {
        when(service.feldeinsatzStarten("tok", 7L)).thenReturn(null);
        when(service.pdf("tok", 3L)).thenReturn(new WartungProtokollPdfService.PdfDatei("W-1.pdf", new byte[] { 37, 80, 68, 70 }));

        mvc.perform(post("/api/zeiterfassung/wartung/anlagen/7/feldeinsatz").header("X-Zugang", "tok")).andExpect(status().isOk());
        mvc.perform(put("/api/zeiterfassung/wartung/protokolle/3/pruefpunkte/8").header("X-Zugang", "tok").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ergebnis\":\"NIO\",\"bemerkung\":\"Dichtung lose\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/zeiterfassung/wartung/protokolle/3/status").header("X-Zugang", "tok").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ABGESCHLOSSEN\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/zeiterfassung/wartung/protokolle/3/pdf").header("X-Zugang", "tok")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(content().bytes(new byte[] { 37, 80, 68, 70 }));

        verify(service).pruefpunktBewerten(eq("tok"), eq(3L), eq(8L), any(WartungPruefpunktErgebnisDto.class));
        verify(service).status("tok", 3L, WartungStatus.ABGESCHLOSSEN);
    }

    @Test
    void ablehnungenKommenMitStatusAnDerApp() throws Exception {
        when(service.protokoll("falsch", 1L)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig. Bitte neu anmelden."));
        when(service.feldeinsatzStarten("tok", 7L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung: Wartung, Anlegen."));

        mvc.perform(get("/api/zeiterfassung/wartung/protokolle/1").header("X-Zugang", "falsch")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/zeiterfassung/wartung/anlagen/7/feldeinsatz").header("X-Zugang", "tok")).andExpect(status().isForbidden());
    }
}
