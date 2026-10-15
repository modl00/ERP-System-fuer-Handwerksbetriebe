package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Der Scanner benennt Fotos über den gewählten KI-Anbieter. Nur Dummy-Daten. */
class GeminiScannerServiceTest {

    KiTextWeiche weiche = mock(KiTextWeiche.class);
    GeminiScannerService service = new GeminiScannerService(new ObjectMapper(), mock(SystemSettingsService.class));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "weiche", weiche);
        when(weiche.istGemini(KiFunktion.SCANNER)).thenReturn(false);
    }

    @Test
    void einAnbieterAusserGeminiBenenntDasFotoUndDerNameWirdBereinigt() throws Exception {
        when(weiche.frageMitDateien(eq(KiFunktion.SCANNER), anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyBoolean()))
                .thenReturn("  Rechnung Müller/GmbH_2026-10-11.pdf ");

        assertThat(service.generateFilename(new byte[] { 1, 2, 3 })).isEqualTo("Rechnung_MllerGmbH_2026-10-11pdf");
    }

    @Test
    void fehltDemAnbieterEtwasGibtEsEinenErsatznamenStattEinesFehlers() throws Exception {
        when(weiche.frageMitDateien(any(), any(), any(), any(), anyDouble(), anyInt(), anyBoolean())).thenThrow(new IOException("kein Schlüssel"));

        assertThat(service.generateFilename(new byte[] { 1 })).startsWith("Scan_");
    }

    @Test
    void einLeeresFotoGibtEinenErsatznamen() {
        assertThat(service.generateFilename(new byte[0])).startsWith("Scan_");
    }

    @Test
    void bereinigenLaesstNurBuchstabenZiffernUnterstrichUndBindestrichStehen() {
        assertThat(GeminiScannerService.bereinige("Brief Finanzamt 2024-01-05")).isEqualTo("Brief_Finanzamt_2024-01-05");
        assertThat(GeminiScannerService.bereinige("***")).startsWith("Scan_");
        assertThat(GeminiScannerService.bereinige(null)).startsWith("Scan_");
    }
}
