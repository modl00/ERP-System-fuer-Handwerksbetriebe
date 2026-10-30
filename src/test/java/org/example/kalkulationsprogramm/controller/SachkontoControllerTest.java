package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;
import org.example.kalkulationsprogramm.dto.SachkontoDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.BelegeKasseExportPdfService;
import org.example.kalkulationsprogramm.service.SachkontoPflegeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SachkontoController.class)
@AutoConfigureMockMvc(addFilters = false)
class SachkontoControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private SachkontoRepository sachkontoRepository;
    @MockitoBean private BelegRepository belegRepository;
    @MockitoBean private BelegService belegService;
    @MockitoBean private BelegeKasseExportPdfService exportPdfService;
    @MockitoBean private SachkontoPflegeService pflegeService;

    private void darfScannen(boolean ja) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(42L);
        given(belegService.findCaller(any(), any())).willReturn(m);
        given(belegService.darfScannen(m)).willReturn(ja);
    }

    private static Sachkonto konto(String nummer, String name) {
        Sachkonto k = new Sachkonto();
        k.setId(7L);
        k.setNummer(nummer);
        k.setBezeichnung(name);
        k.setKontoTyp(SachkontoTyp.AUFWAND);
        k.setAktiv(true);
        return k;
    }

    @Test
    @DisplayName("Anlegen: gibt das neue Konto zurück, 4120 und Co. sind als Systemkonto gekennzeichnet")
    void anlegen() throws Exception {
        darfScannen(true);
        given(pflegeService.anlegen(any())).willReturn(konto("4120", "Loehne"));

        mvc.perform(post("/api/buchhaltung/sachkonten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nummer\":\"4120\",\"bezeichnung\":\"Loehne\",\"kontoTyp\":\"AUFWAND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nummer").value("4120"))
                .andExpect(jsonPath("$.systemkonto").value(true));
    }

    @Test
    @DisplayName("Anlegen: ein Fehler der Prüfung kommt als 400 mit Klartext-Meldung zurück")
    void anlegenFehler() throws Exception {
        darfScannen(true);
        given(pflegeService.anlegen(any())).willThrow(new IllegalArgumentException("Die Nummer 4920 ist schon vergeben (Telefon)."));

        mvc.perform(post("/api/buchhaltung/sachkonten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nummer\":\"4920\",\"bezeichnung\":\"Handy\",\"kontoTyp\":\"AUFWAND\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die Nummer 4920 ist schon vergeben (Telefon)."));
    }

    @Test
    @DisplayName("Ändern: normaler Erfolg, unbekanntes Konto ergibt 404, Prüfungsfehler 400")
    void aendern() throws Exception {
        darfScannen(true);
        given(pflegeService.aendern(eq(7L), any())).willReturn(konto("4920", "Telefon"));
        given(pflegeService.aendern(eq(8L), any())).willThrow(new NoSuchElementException());
        given(pflegeService.aendern(eq(9L), any())).willThrow(new IllegalArgumentException("Das Konto 4120 braucht das Programm für die Kasse."));

        mvc.perform(put("/api/buchhaltung/sachkonten/7").contentType(MediaType.APPLICATION_JSON).content("{\"beschreibung\":\"x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.systemkonto").value(false));
        mvc.perform(put("/api/buchhaltung/sachkonten/8").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/buchhaltung/sachkonten/9").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Das Konto 4120 braucht das Programm für die Kasse."));
    }

    @Test
    @DisplayName("Ohne Recht zum Scannen und Buchen: 403, der Pflegedienst wird nicht einmal gefragt")
    void ohneRecht() throws Exception {
        darfScannen(false);

        mvc.perform(post("/api/buchhaltung/sachkonten").contentType(MediaType.APPLICATION_JSON).content("{\"bezeichnung\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/buchhaltung/sachkonten/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pflegeService);
    }
}
