package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.service.ArbeitsgangManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Arbeitsgang löschen oder ausblenden (#235): Der Grund kommt als Meldung an. Nur Dummy-Daten. */
class ArbeitsgangArchivierenControllerTest {

    private final ArbeitsgangManagementService service = mock(ArbeitsgangManagementService.class);
    private final ArbeitsgangMapper mapper = mock(ArbeitsgangMapper.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArbeitsgangController(service, mapper, mock(AbteilungRepository.class))).build();

    @Test
    void einBenutzterArbeitsgangGibtKonfliktMitGrund() throws Exception {
        doThrow(new IllegalStateException("Der Arbeitsgang „Sägen“ wird noch benutzt (1 Zeitbuchung).")).when(service).loescheArbeitsgang(5L);

        mvc.perform(delete("/api/arbeitsgaenge/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Der Arbeitsgang „Sägen“ wird noch benutzt (1 Zeitbuchung)."));
    }

    @Test
    void einFreierArbeitsgangWirdGeloescht() throws Exception {
        mvc.perform(delete("/api/arbeitsgaenge/5")).andExpect(status().isNoContent());
        verify(service).loescheArbeitsgang(5L);
    }

    @Test
    void ausblendenGibtDenArbeitsgangMitKennzeichenZurueck() throws Exception {
        Arbeitsgang gang = new Arbeitsgang();
        ArbeitsgangResponseDto dto = new ArbeitsgangResponseDto();
        dto.setId(5L);
        dto.setArchiviert(true);
        when(service.aktualisiereArchiviert(eq(5L), eq(true))).thenReturn(gang);
        when(mapper.toArbeitsgangResponseDto(gang)).thenReturn(dto);

        mvc.perform(put("/api/arbeitsgaenge/5/archiviert").contentType(MediaType.APPLICATION_JSON).content("{\"archiviert\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archiviert").value(true));
    }

    @Test
    void ohneAngabeWirdWiedereingeblendet() throws Exception {
        Arbeitsgang gang = new Arbeitsgang();
        when(service.aktualisiereArchiviert(eq(5L), eq(false))).thenReturn(gang);
        when(mapper.toArbeitsgangResponseDto(gang)).thenReturn(new ArbeitsgangResponseDto());

        mvc.perform(put("/api/arbeitsgaenge/5/archiviert").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(service).aktualisiereArchiviert(5L, false);
    }
}
