package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.service.ArtikelArchivService;
import org.example.kalkulationsprogramm.service.ArtikelArchivService.ProjektNutzung;
import org.example.kalkulationsprogramm.service.ArtikelArchivService.Verwendungen;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ArtikelArchivController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelArchivControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ArtikelArchivService service;

    @Test
    void verwendungenMitProjekten() throws Exception {
        when(service.verwendungen(5L)).thenReturn(new Verwendungen(5L, "Rundrohr 42,4 x 2", true,
                List.of(new ProjektNutzung(9L, "Geländer Musterstraße", "3 Stück, 12,5 m")), 4, 2, 0, null));

        mvc.perform(get("/api/artikel/5/verwendungen")).andExpect(status().isOk())
                .andExpect(jsonPath("$.projekte[0].bauvorhaben").value("Geländer Musterstraße"))
                .andExpect(jsonPath("$.projekte[0].menge").value("3 Stück, 12,5 m"))
                .andExpect(jsonPath("$.lieferantenpreise").value(4));
    }

    @Test
    void fehlerKommenAlsKlartext() throws Exception {
        when(service.loesche(5L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Bitte den Artikel zuerst archivieren. Endgültig gelöscht wird nur aus dem Archiv."));
        when(service.verwendungen(6L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Artikel gibt es nicht."));

        mvc.perform(delete("/api/artikel/5")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Bitte den Artikel zuerst archivieren. Endgültig gelöscht wird nur aus dem Archiv."));
        mvc.perform(get("/api/artikel/6/verwendungen")).andExpect(status().isNotFound());
    }

    @Test
    void archivierenOhneAnmeldungNutztKeinenBenutzernamen() throws Exception {
        when(service.archiviere(5L, false, null)).thenReturn(1);

        mvc.perform(post("/api/artikel/5/archivieren")).andExpect(status().isOk()).andExpect(jsonPath("$.anzahl").value(1));
    }
}
