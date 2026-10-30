package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerDto;
import org.example.kalkulationsprogramm.service.KundeAnsprechpartnerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(KundeAnsprechpartnerController.class)
@AutoConfigureMockMvc(addFilters = false)
class KundeAnsprechpartnerControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private KundeAnsprechpartnerService service;

    private static final String GUELTIG = "{\"name\":\"Hans Beispiel\",\"funktion\":\"Hausmeister\",\"telefon\":\"0931 1\",\"email\":\"hans@example.org\"}";

    private static KundeAnsprechpartnerDto dto() {
        return new KundeAnsprechpartnerDto(7L, "Hans Beispiel", "Hausmeister", "0931 1", null, "hans@example.org", null, true);
    }

    @Test
    @DisplayName("Liste: 200 mit den Ansprechpartnern")
    void liste() throws Exception {
        given(service.liste(3L)).willReturn(List.of(dto()));

        mockMvc.perform(get("/api/kunden/3/ansprechpartner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hans Beispiel"))
                .andExpect(jsonPath("$[0].haupt").value(true));
    }

    @Test
    @DisplayName("Anlegen: 201 mit dem neuen Ansprechpartner")
    void anlegen() throws Exception {
        given(service.anlegen(eq(3L), any())).willReturn(dto());

        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("Anlegen ohne Namen, mit leerem Namen oder zu langen Feldern: 400 mit deutscher Meldung, nichts wird gespeichert")
    void pflichtfelderUndGrenzen() throws Exception {
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "A".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hans\",\"notizen\":\"" + "A".repeat(10001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hans\",\"email\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).anlegen(any(), any());
    }

    @Test
    @DisplayName("SQL-Injection und Skript im Namen gehen unverändert an den Service (sie werden nur als Text gespeichert)")
    void sonderzeichen() throws Exception {
        given(service.anlegen(eq(3L), any())).willReturn(dto());

        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"'; DROP TABLE kunde_ansprechpartner; --\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/kunden/3/ansprechpartner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ändern, Haupt setzen und Löschen: 200, 200 und 204")
    void aendernHauptLoeschen() throws Exception {
        given(service.aendern(eq(3L), eq(7L), any())).willReturn(dto());
        given(service.hauptSetzen(3L, 7L)).willReturn(dto());

        mockMvc.perform(put("/api/kunden/3/ansprechpartner/7").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/kunden/3/ansprechpartner/7/haupt")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/kunden/3/ansprechpartner/7")).andExpect(status().isNoContent());
        verify(service).loeschen(3L, 7L);
    }

    @Test
    @DisplayName("Unbekannter Kunde oder Ansprechpartner: 404 mit der Meldung des Dienstes")
    void nichtGefunden() throws Exception {
        given(service.liste(99L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Kunde wurde nicht gefunden."));
        given(service.aendern(eq(3L), eq(99L), any())).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Ansprechpartner wurde nicht gefunden."));

        mockMvc.perform(get("/api/kunden/99/ansprechpartner")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/kunden/3/ansprechpartner/99").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ungültige Nummern (Buchstaben, zu groß für eine Zahl): 400 statt Absturz")
    void ungueltigeNummern() throws Exception {
        mockMvc.perform(get("/api/kunden/abc/ansprechpartner")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/kunden/3/ansprechpartner/99999999999999999999")).andExpect(status().isBadRequest());
    }
}
