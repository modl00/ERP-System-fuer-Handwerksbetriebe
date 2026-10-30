package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungEingabe;
import org.example.kalkulationsprogramm.service.LieferantenBestellungPdfService;
import org.example.kalkulationsprogramm.service.LieferantenBestellungService;
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

@WebMvcTest(LieferantenBestellungController.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantenBestellungControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private LieferantenBestellungService service;
    @MockitoBean private LieferantenBestellungPdfService pdfService;
    @MockitoBean private org.example.kalkulationsprogramm.service.BestellPositionAnhangService bestellPositionAnhangService;
    @MockitoBean private org.example.kalkulationsprogramm.service.ArtikelFertigungsunterlageService artikelFertigungsunterlageService;

    private static BestellungDto dto(Long id, String nummer, BestellStatus status) {
        return new BestellungDto(id, nummer, status, status.name(), 3L, "Stahlhandel Muster", List.of(), null, LocalDateTime.of(2026, 10, 5, 8, 0), "admin", null,
                null, null, null, null, BigDecimal.ZERO, 0, 0, null, false, 7, List.of(), List.of());
    }

    private static final String BODY = "{\"lieferantId\":3,\"notiz\":\"Bitte liefern\",\"positionen\":[{\"artikelId\":10,\"menge\":12.5,\"einheit\":\"m\",\"herkunft\":\"PROJEKT\",\"projektId\":7,\"artikelInProjektId\":55,\"kommentar\":\"Zuschnitt\"}]}";

    @Test
    @DisplayName("Anlegen: 201, Eingabe wird gelesen (Menge, Herkunft, Projekt, Materialposition)")
    void anlegen() throws Exception {
        given(service.anlegen(any())).willReturn(dto(100L, null, BestellStatus.ENTWURF));

        mvc.perform(post("/api/lieferanten-bestellungen").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(100)).andExpect(jsonPath("$.status").value("ENTWURF"));

        verify(service).anlegen(new BestellungEingabe(3L, "Bitte liefern", List.of(new BestellungEingabe.Position(10L, new BigDecimal("12.5"), "m", null, BestellHerkunft.PROJEKT, 7L, 55L, "Zuschnitt"))));
    }

    @Test
    @DisplayName("Ablehnungen des Dienstes kommen als 400, 404 und 409 mit Text an")
    void fehler() throws Exception {
        given(service.anlegen(any())).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Lieferanten wählen."));
        given(service.lade(9L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Bestellung gibt es nicht."));
        given(service.bestellen(eq(5L), any())).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Diese Bestellung ist schon bestellt."));

        mvc.perform(post("/api/lieferanten-bestellungen").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/lieferanten-bestellungen/9")).andExpect(status().isNotFound());
        mvc.perform(post("/api/lieferanten-bestellungen/5/bestellen").contentType(MediaType.APPLICATION_JSON).content("{\"per\":\"EMAIL\"}")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Liste reicht die Filter weiter; Vorschläge für Mindestbestand und Projekt")
    void listeUndVorschlaege() throws Exception {
        given(service.liste("BESTELLT", 3L, 7L, true)).willReturn(List.of(dto(1L, "B-2026-10-0001", BestellStatus.BESTELLT)));
        given(service.vorschlaegeMindestbestand()).willReturn(List.of(new BestellVorschlagDto(11L, "S-M8", "Schraube M8", "Stk", new BigDecimal("80"), BestellHerkunft.LAGER,
                null, null, null, 3L, "Stahlhandel Muster", null, null, "Bestand 20 Stk")));
        given(service.vorschlaegeProjekt(7L)).willReturn(List.of());

        mvc.perform(get("/api/lieferanten-bestellungen?stand=BESTELLT&lieferantId=3&projektId=7&nurUeberfaellig=true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bestellnummer").value("B-2026-10-0001"));
        mvc.perform(get("/api/lieferanten-bestellungen/vorschlaege/mindestbestand")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Schraube M8")).andExpect(jsonPath("$[0].herkunft").value("LAGER"));
        mvc.perform(get("/api/lieferanten-bestellungen/vorschlaege/projekt/7")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Ändern, Löschen, Bestellen (auch ohne Text), Liefern, Alles geliefert, Stornieren")
    void aktionen() throws Exception {
        given(service.aendern(eq(100L), any())).willReturn(dto(100L, null, BestellStatus.ENTWURF));
        given(service.bestellen(100L, null)).willReturn(dto(100L, "B-2026-10-0001", BestellStatus.BESTELLT));
        given(service.liefern(100L, 4L, true)).willReturn(dto(100L, "B-2026-10-0001", BestellStatus.BESTELLT));
        given(service.alleGeliefert(100L)).willReturn(dto(100L, "B-2026-10-0001", BestellStatus.BESTELLT));
        given(service.stornieren(100L, "Zu spät")).willReturn(dto(100L, "B-2026-10-0001", BestellStatus.STORNIERT));

        mvc.perform(put("/api/lieferanten-bestellungen/100").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(post("/api/lieferanten-bestellungen/100/bestellen")).andExpect(status().isOk()).andExpect(jsonPath("$.bestellnummer").value("B-2026-10-0001"));
        mvc.perform(post("/api/lieferanten-bestellungen/100/positionen/4/geliefert").contentType(MediaType.APPLICATION_JSON).content("{\"geliefert\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/lieferanten-bestellungen/100/alles-geliefert")).andExpect(status().isOk());
        mvc.perform(post("/api/lieferanten-bestellungen/100/stornieren").contentType(MediaType.APPLICATION_JSON).content("{\"grund\":\"Zu spät\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STORNIERT"));
        mvc.perform(delete("/api/lieferanten-bestellungen/100")).andExpect(status().isNoContent());

        verify(service).loeschen(100L);
    }

    @Test
    @DisplayName("PDF: application/pdf mit Dateiname aus der Bestellnummer")
    void pdf() throws Exception {
        given(service.lade(100L)).willReturn(dto(100L, "B-2026-10-0001", BestellStatus.BESTELLT));
        given(pdfService.pdf(100L)).willReturn("%PDF-test".getBytes());

        mvc.perform(get("/api/lieferanten-bestellungen/100/pdf")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"Bestellung_B-2026-10-0001.pdf\"")).andExpect(content().bytes("%PDF-test".getBytes()));
    }
}
