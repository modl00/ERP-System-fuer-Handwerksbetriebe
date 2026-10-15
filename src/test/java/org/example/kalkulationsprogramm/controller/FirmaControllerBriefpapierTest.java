package org.example.kalkulationsprogramm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.EmailAbsenderService;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Die Briefpapier-Endpunkte der Firma. */
@WebMvcTest(FirmaController.class)
@AutoConfigureMockMvc(addFilters = false)
class FirmaControllerBriefpapierTest {

    @Autowired MockMvc mockMvc;
    @MockBean FirmeninformationService firmeninformationService;
    @MockBean KostenstelleService kostenstelleService;
    @MockBean SteuerberaterKontaktService steuerberaterKontaktService;
    @MockBean EmailAbsenderService emailAbsenderService;

    @Test
    void uploadLiefertDieAktualisierteFirma() throws Exception {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setBriefpapierDateiname("briefpapier.png");
        when(firmeninformationService.speichereBriefpapier(any())).thenReturn(dto);

        mockMvc.perform(multipart("/api/firma/briefpapier").file(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2 })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefpapierDateiname").value("briefpapier.png"));
    }

    @Test
    void ungueltigeDateiIstEin400MitMeldung() throws Exception {
        when(firmeninformationService.speichereBriefpapier(any())).thenThrow(new IllegalArgumentException("Ungültiger Dateityp – erlaubt sind PNG, JPEG und PDF"));

        mockMvc.perform(multipart("/api/firma/briefpapier").file(new MockMultipartFile("datei", "x.exe", "application/octet-stream", new byte[] { 1 })))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Ungültiger Dateityp")));
    }

    @Test
    void einIoFehlerIstEin500() throws Exception {
        when(firmeninformationService.speichereBriefpapier(any())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/firma/briefpapier").file(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1 })))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void downloadLiefertBytesMitDemRichtigenTyp() throws Exception {
        byte[] bytes = { 9, 8, 7 };
        when(firmeninformationService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(bytes, true, MediaType.APPLICATION_PDF_VALUE));

        mockMvc.perform(get("/api/firma/briefpapier"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void ohneBriefpapierIstDerDownloadEin404() throws Exception {
        when(firmeninformationService.loadBriefpapier()).thenReturn(null);

        mockMvc.perform(get("/api/firma/briefpapier")).andExpect(status().isNotFound());
    }

    @Test
    void loeschenLiefertDieAktualisierteFirma() throws Exception {
        when(firmeninformationService.loescheBriefpapier()).thenReturn(new FirmeninformationDto());

        mockMvc.perform(delete("/api/firma/briefpapier")).andExpect(status().isOk());
        verify(firmeninformationService).loescheBriefpapier();
    }

    @Test
    void kopfFlagWirdGesetztUndFehlendeAngabeAbgelehnt() throws Exception {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setBriefpapierEnthaeltKopf(true);
        when(firmeninformationService.setzeBriefpapierEnthaeltKopf(true)).thenReturn(dto);

        mockMvc.perform(put("/api/firma/briefpapier/kopf").contentType(MediaType.APPLICATION_JSON).content("{\"enthaeltKopf\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.briefpapierEnthaeltKopf").value(true));
        mockMvc.perform(put("/api/firma/briefpapier/kopf").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/firma/briefpapier/kopf").contentType(MediaType.APPLICATION_JSON).content("{\"enthaeltKopf\":\"vielleicht\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void kopfFlagOhneBriefpapierIstEin400MitMeldung() throws Exception {
        when(firmeninformationService.setzeBriefpapierEnthaeltKopf(true)).thenThrow(new IllegalArgumentException("Es ist noch kein Briefpapier hinterlegt."));

        mockMvc.perform(put("/api/firma/briefpapier/kopf").contentType(MediaType.APPLICATION_JSON).content("{\"enthaeltKopf\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("kein Briefpapier")));
    }
}
