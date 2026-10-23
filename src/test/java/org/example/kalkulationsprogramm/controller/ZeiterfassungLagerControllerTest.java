package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.service.LagerAppService;
import org.example.kalkulationsprogramm.service.LagerAppService.EanTreffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ZeiterfassungLagerController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeiterfassungLagerControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private LagerAppService service;

    private static LagerBestandDto zeile() {
        return new LagerBestandDto(9L, "Schraube M8", "S-8", "Stk", new BigDecimal("40"), null, false, false,
                List.of(new LagerBestandDto.OrtBestand(1L, "Werkstatt", new BigDecimal("40"))), null, null, null);
    }

    @Test
    @DisplayName("Orte, Suche und Strichcode liefern die Daten; der Zugangslink steht im Pfad")
    void lesen() throws Exception {
        given(service.orte("tok")).willReturn(List.of(new LagerortDto(1L, "Werkstatt", null, true, 1, false, null)));
        given(service.suche("tok", "schraube")).willReturn(List.of(zeile()));
        given(service.nachEan("tok", "4006381333931")).willReturn(new EanTreffer(true, "4006381333931", zeile()));
        given(service.nachEan("tok", "4012345678901")).willReturn(new EanTreffer(false, "4012345678901", null));

        mvc.perform(get("/api/zeiterfassung/lager/tok/orte")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Werkstatt"));
        mvc.perform(get("/api/zeiterfassung/lager/tok/suche").param("q", "schraube")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Schraube M8")).andExpect(jsonPath("$[0].orte[0].lagerortName").value("Werkstatt"));
        mvc.perform(get("/api/zeiterfassung/lager/tok/ean/4006381333931")).andExpect(jsonPath("$.gefunden").value(true)).andExpect(jsonPath("$.artikel.artikelId").value(9));
        mvc.perform(get("/api/zeiterfassung/lager/tok/ean/4012345678901")).andExpect(jsonPath("$.gefunden").value(false)).andExpect(jsonPath("$.artikel").doesNotExist());
    }

    @Test
    @DisplayName("Häufig benutzt: die meistgebuchten Artikel kommen über den Zugangslink im Pfad")
    void haeufig() throws Exception {
        given(service.haeufig("tok")).willReturn(List.of(zeile()));

        mvc.perform(get("/api/zeiterfassung/lager/tok/haeufig")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Schraube M8"));
    }

    @Test
    @DisplayName("Buchung: Zugangslink, Art, Menge und Projekt aus dem Text der Anfrage gehen an den Dienst")
    void buchung() throws Exception {
        given(service.buche(any(), any())).willReturn(zeile());

        mvc.perform(post("/api/zeiterfassung/lager/buchung").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"art\":\"ABGANG\",\"artikelId\":9,\"lagerortId\":1,\"menge\":3,\"projektId\":77,\"notiz\":\"Baustelle Süd\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.artikelId").value(9));

        org.mockito.Mockito.verify(service).buche("tok", new LagerBuchungEingabe("ABGANG", 9L, 1L, new BigDecimal("3"), 77L, "Baustelle Süd"));
    }

    @Test
    @DisplayName("Umbuchung und Strichcode zuordnen")
    void umbuchungUndEan() throws Exception {
        given(service.umbuche(any(), any())).willReturn(zeile());
        given(service.eanZuordnen("tok", 9L, "4006381333931")).willReturn(zeile());

        mvc.perform(post("/api/zeiterfassung/lager/umbuchung").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"artikelId\":9,\"vonLagerortId\":1,\"nachLagerortId\":2,\"menge\":2}")).andExpect(status().isOk());
        org.mockito.Mockito.verify(service).umbuche("tok", new UmbuchungEingabe(9L, 1L, 2L, new BigDecimal("2"), null));
        mvc.perform(post("/api/zeiterfassung/lager/ean-zuordnen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"artikelId\":9,\"ean\":\"4006381333931\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ablehnungen des Dienstes (401, 403, 409) erreichen die App mit ihrem Status")
    void ablehnungen() throws Exception {
        given(service.orte("falsch")).willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig."));
        given(service.buche(any(), any())).willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung: Lager, Anlegen."));
        given(service.eanZuordnen(any(), any(), any())).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Strichcode gehört schon zu einem anderen Artikel."));

        mvc.perform(get("/api/zeiterfassung/lager/falsch/orte")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/zeiterfassung/lager/buchung").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\",\"art\":\"ABGANG\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/zeiterfassung/lager/ean-zuordnen").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok\",\"artikelId\":9,\"ean\":\"4006381333931\"}"))
                .andExpect(status().isConflict());
    }
}
