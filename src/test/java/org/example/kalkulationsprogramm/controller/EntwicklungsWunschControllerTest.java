package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Stand;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Testergebnis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntwicklungsWunschController.class)
@AutoConfigureMockMvc(addFilters = false)
class EntwicklungsWunschControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private EntwicklungsWunschService service;

    @Test
    void standUndSpeichernGebenDenTokenNieZurueck() throws Exception {
        Stand stand = new Stand("http://nas:3050", "M/ERP", true, false, true);
        given(service.stand()).willReturn(stand);
        given(service.speichern(eq("http://nas:3050"), eq("M/ERP"), eq("dummy-token-1234567890"))).willReturn(stand);

        mvc.perform(get("/api/settings/ki-wunsch")).andExpect(status().isOk()).andExpect(jsonPath("$.tokenGesetzt").value(true)).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(put("/api/settings/ki-wunsch").contentType(MediaType.APPLICATION_JSON).content("{\"adresse\":\"http://nas:3050\",\"repository\":\"M/ERP\",\"token\":\"dummy-token-1234567890\"}"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dummy-token"))));
    }

    @Test
    void ungueltigeAngabenKommenAlsFehlerMitKlartextUndDerTestMeldetDasErgebnis() throws Exception {
        given(service.speichern(any(), any(), any())).willThrow(new IllegalArgumentException("Die Adresse muss mit http:// oder https:// beginnen."));
        mvc.perform(put("/api/settings/ki-wunsch").contentType(MediaType.APPLICATION_JSON).content("{\"adresse\":\"ftp://x\",\"repository\":\"M/ERP\"}")).andExpect(status().isBadRequest());

        given(service.teste()).willReturn(new Testergebnis(true, "Die Verbindung klappt."));
        mvc.perform(post("/api/settings/ki-wunsch/test")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.meldung").value("Die Verbindung klappt."));
    }
}
