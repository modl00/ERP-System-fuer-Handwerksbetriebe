package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.AbteilungRechte;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.Rechte;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class FunktionsrechteControllerTest {

    private final FunktionsRechteService service = mock(FunktionsRechteService.class);
    private final WartungModulSchalterService wartung = mock(WartungModulSchalterService.class);
    private final LagerModulSchalterService lager = mock(LagerModulSchalterService.class);
    private MockMvc mvc;

    private static AbteilungRechte abteilung() {
        Map<Funktion, Rechte> r = new EnumMap<>(Funktion.class);
        r.put(Funktion.LAGER, new Rechte(true, true, false, false));
        r.put(Funktion.WARTUNG, Rechte.ALLES);
        return new AbteilungRechte(1L, "Montage", r);
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ObjectProvider<LagerModulSchalterService> lagerProvider = mock(ObjectProvider.class);
        when(lagerProvider.getIfAvailable()).thenReturn(lager);
        when(wartung.istAktiv()).thenReturn(true);
        when(lager.istAktiv()).thenReturn(false);
        mvc = MockMvcBuilders.standaloneSetup(new FunktionsrechteController(service), new ZeiterfassungRechteController(service, wartung, lagerProvider)).build();
    }

    @Test
    void uebersichtListetFunktionenAktionenUndAbteilungen() throws Exception {
        when(service.alle()).thenReturn(List.of(abteilung()));

        mvc.perform(get("/api/abteilungen/funktionsrechte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funktionen[0].name").value("LAGER"))
                .andExpect(jsonPath("$.funktionen[0].anzeige").value("Lager"))
                .andExpect(jsonPath("$.aktionen[0]").value("ANSEHEN"))
                .andExpect(jsonPath("$.abteilungen[0].abteilungName").value("Montage"))
                .andExpect(jsonPath("$.abteilungen[0].rechte.LAGER.anlegen").value(true))
                .andExpect(jsonPath("$.abteilungen[0].rechte.LAGER.aendern").value(false));
    }

    @Test
    void speichernGibtDieRechteAnDenDienst() throws Exception {
        when(service.speichern(eq(1L), any())).thenReturn(abteilung());

        mvc.perform(put("/api/abteilungen/1/funktionsrechte").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechte\":{\"LAGER\":{\"ansehen\":true,\"anlegen\":true,\"aendern\":false,\"loeschen\":false}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rechte.LAGER.ansehen").value(true));
        verify(service).speichern(eq(1L), eq(Map.of("LAGER", new Rechte(true, true, false, false))));
    }

    @Test
    void fehlerDesDienstesKommenMitStatus() throws Exception {
        when(service.speichern(eq(9L), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Abteilung gibt es nicht."));

        mvc.perform(put("/api/abteilungen/9/funktionsrechte").contentType(MediaType.APPLICATION_JSON).content("{\"rechte\":{}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechteDerAppZumZugangslinkMitModulstand() throws Exception {
        Map<Funktion, Rechte> r = new EnumMap<>(Funktion.class);
        r.put(Funktion.LAGER, new Rechte(true, false, false, false));
        r.put(Funktion.WARTUNG, Rechte.ALLES);
        when(service.wirksamFuerToken("abc")).thenReturn(Optional.of(r));

        mvc.perform(get("/api/zeiterfassung/rechte/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funktionen.LAGER.ansehen").value(true))
                .andExpect(jsonPath("$.funktionen.LAGER.anlegen").value(false))
                .andExpect(jsonPath("$.module.wartung").value(true))
                .andExpect(jsonPath("$.module.lager").value(false));
    }

    @Test
    void unbekannterZugangslinkGibt401() throws Exception {
        when(service.wirksamFuerToken("falsch")).thenReturn(Optional.empty());

        mvc.perform(get("/api/zeiterfassung/rechte/falsch")).andExpect(status().isUnauthorized());
    }
}
