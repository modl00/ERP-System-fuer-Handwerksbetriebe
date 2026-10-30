package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.service.KostensatzService;
import org.example.kalkulationsprogramm.service.KostensatzService.Ergebnis;
import org.example.kalkulationsprogramm.service.KostensatzService.Zeile;
import org.example.kalkulationsprogramm.service.SatzUebernahmeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KostenrechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
class KostenrechnungControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private KostensatzService service;
    @MockitoBean private SatzUebernahmeService uebernahme;
    @MockitoBean private org.example.kalkulationsprogramm.service.GemeinkostenSchaetzung schaetzung;
    @MockitoBean private org.example.kalkulationsprogramm.service.KostenstelleService kostenstellen;
    @MockitoBean private org.example.kalkulationsprogramm.service.KostenrechnungVorgaben vorgaben;

    @Test
    void dieVorgabenWerdenGelesenUndGespeichertUndEinFehlerKommtAls400() throws Exception {
        given(vorgaben.lese()).willReturn(new org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Vorgaben(new BigDecimal("3"), new BigDecimal("12"), 6));
        mvc.perform(get("/api/kostenrechnung/vorgaben")).andExpect(status().isOk())
                .andExpect(jsonPath("$.wagnis").value(3)).andExpect(jsonPath("$.gewinn").value(12)).andExpect(jsonPath("$.interneZeit").value(6));

        given(vorgaben.speichere(new BigDecimal("3"), null, 6)).willReturn(new org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Vorgaben(new BigDecimal("3"), null, 6));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/vorgaben")
                .contentType("application/json").content("{\"wagnis\": 3, \"interneZeit\": 6}")).andExpect(status().isOk()).andExpect(jsonPath("$.gewinn").doesNotExist());

        given(vorgaben.speichere(new BigDecimal("500"), null, null)).willThrow(new IllegalArgumentException("Das Wagnis muss zwischen 0 und 100 Prozent liegen."));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/vorgaben")
                .contentType("application/json").content("{\"wagnis\": 500}")).andExpect(status().isBadRequest());
    }

    @Test
    void dieSchaetzungDerInternenZeitWirdGeliefert() throws Exception {
        given(vorgaben.schaetze(2025)).willReturn(new org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Schaetzung(2025, 7, new BigDecimal("140.00"), new BigDecimal("2000.00"), null));
        mvc.perform(get("/api/kostenrechnung/interne-zeit-schaetzung").param("jahr", "2025")).andExpect(status().isOk()).andExpect(jsonPath("$.prozent").value(7));
    }

    @Test
    void dasSollEinerKostenstelleWirdGesetztUndEinFehlerKommtAls400() throws Exception {
        var dto = new org.example.kalkulationsprogramm.dto.KostenstelleDto();
        dto.setId(7L);
        dto.setJahresSoll(new BigDecimal("12000.00"));
        given(kostenstellen.setzeSoll(7L, new BigDecimal("12000"))).willReturn(dto);
        given(kostenstellen.setzeSoll(8L, new BigDecimal("5"))).willThrow(new IllegalArgumentException("Einen geschätzten Jahresbetrag gibt es nur für Gemeinkosten (Fixkosten)."));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/kostenstellen/7/soll")
                .contentType("application/json").content("{\"jahresSoll\": 12000}")).andExpect(status().isOk()).andExpect(jsonPath("$.jahresSoll").value(12000.00));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/kostenstellen/8/soll")
                .contentType("application/json").content("{\"jahresSoll\": 5}")).andExpect(status().isBadRequest());
    }

    @Test
    void dieSchaetzungWirdGelesenUndGespeichert() throws Exception {
        given(schaetzung.lese()).willReturn(new BigDecimal("36000.00"));
        mvc.perform(get("/api/kostenrechnung/gemeinkosten-schaetzung")).andExpect(status().isOk()).andExpect(jsonPath("$.jahresbetrag").value(36000.00));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/gemeinkosten-schaetzung")
                .contentType("application/json").content("{\"jahresbetrag\": 24000}")).andExpect(status().isOk());
        org.mockito.Mockito.verify(schaetzung).speichere(new BigDecimal("24000"));
    }

    @Test
    void eineUngueltigeSchaetzungKommtAlsFehlerMitKlartext() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Die geschätzten Gemeinkosten dürfen nicht negativ sein.")).when(schaetzung).speichere(new BigDecimal("-5"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kostenrechnung/gemeinkosten-schaetzung")
                .contentType("application/json").content("{\"jahresbetrag\": -5}")).andExpect(status().isBadRequest());
    }

    @Test
    void liefertDieUebersichtUndGibtDieParameterWeiter() throws Exception {
        Zeile betrieb = new Zeile("BETRIEB", "Ganzer Betrieb", 3, new BigDecimal("120000.00"), new BigDecimal("3100.00"), new BigDecimal("38.71"), new BigDecimal("9.68"),
                new BigDecimal("48.39"), new BigDecimal("2.42"), new BigDecimal("5.08"), new BigDecimal("55.89"), null);
        given(service.berechne(eq(2026), any(), eq(new BigDecimal("5")), eq(new BigDecimal("10"))))
                .willReturn(new Ergebnis(2026, "HOCHRECHNUNG", new BigDecimal("120000.00"), new BigDecimal("3100.00"), new BigDecimal("30000.00"), new BigDecimal("9.68"),
                        new BigDecimal("5"), new BigDecimal("10"), betrieb, List.of(), List.of(), List.of(),
                        new KostensatzService.Gemeinkosten(List.of(), null, false, false, false)));

        mvc.perform(get("/api/kostenrechnung/kostensaetze").param("jahr", "2026").param("wagnis", "5").param("gewinn", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.betrieb.verrechnungssatz").value(55.89)).andExpect(jsonPath("$.betrieb.kostensatz").value(48.39)).andExpect(jsonPath("$.modus").value("HOCHRECHNUNG"));
    }

    @Test
    void ungueltigeWerteKommenAlsFehlerMitKlartext() throws Exception {
        given(service.berechne(eq(1999), any(), any(), any())).willThrow(new IllegalArgumentException("Bitte ein Jahr zwischen 2000 und 2100 wählen."));
        mvc.perform(get("/api/kostenrechnung/kostensaetze").param("jahr", "1999")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/kostenrechnung/kostensaetze")).andExpect(status().isBadRequest());
    }

    @Test
    void dieVorschauKommtAlsListeMitAltNeuUndArt() throws Exception {
        given(uebernahme.vorschau(eq(2026), any(), any(), any())).willReturn(new SatzUebernahmeService.Vorschau(2026, new BigDecimal("80.00"),
                List.of(new SatzUebernahmeService.Zeile(1L, "Schweißen", "Werkstatt", new BigDecimal("70.00"), new BigDecimal("80.00"), new BigDecimal("10.00"), "AENDERT", "VERRECHNUNG", "FACHARBEITER"))));
        mvc.perform(get("/api/kostenrechnung/uebernahme/vorschau").param("jahr", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$.neuerSatz").value(80.00)).andExpect(jsonPath("$.zeilen[0].name").value("Schweißen")).andExpect(jsonPath("$.zeilen[0].art").value("AENDERT"));
    }

    @Test
    void dieVorschauOhneBerechenbarenSatzIstEinFehlerMitKlartext() throws Exception {
        given(uebernahme.vorschau(eq(2026), any(), any(), any())).willThrow(new SatzUebernahmeService.UebernahmeFehler("kein Verrechnungssatz"));
        mvc.perform(get("/api/kostenrechnung/uebernahme/vorschau").param("jahr", "2026")).andExpect(status().isBadRequest());
    }

    @Test
    void dasUebernehmenMeldetDieAnzahlUndBeiGeaenderterRechnungEinenKonflikt() throws Exception {
        given(uebernahme.uebernehme(eq(2026), any(), any(), any(), eq(new BigDecimal("80.00")), eq(List.of(1L, 2L)))).willReturn(2);
        given(uebernahme.uebernehme(eq(2026), any(), any(), any(), eq(new BigDecimal("70.00")), any())).willThrow(new SatzUebernahmeService.UebernahmeFehler("Vorschau neu laden"));
        String koerper = "{\"jahr\":2026,\"erwarteterSatz\":%s,\"arbeitsgangIds\":[1,2]}";

        mvc.perform(post("/api/kostenrechnung/uebernahme").contentType("application/json").content(koerper.formatted("80.00"))).andExpect(status().isOk()).andExpect(jsonPath("$.geschrieben").value(2));
        mvc.perform(post("/api/kostenrechnung/uebernahme").contentType("application/json").content(koerper.formatted("70.00"))).andExpect(status().isConflict());
    }
}
