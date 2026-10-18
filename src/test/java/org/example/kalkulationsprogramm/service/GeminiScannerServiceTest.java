package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.kalkulationsprogramm.service.ki.KiAnbieter;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTypen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Der Scanner benennt Fotos über den gewählten KI-Anbieter. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class GeminiScannerServiceTest {

    @Mock KiAnbieterService kiAnbieterService;
    @Mock KiAnbieter kiAnbieter;

    @InjectMocks
    GeminiScannerService service;

    @Test
    void einAnbieterBenenntDasFotoUndDerNameWirdBereinigt() throws Exception {
        when(kiAnbieterService.waehle(KiFunktion.SCANNER)).thenReturn(kiAnbieter);
        when(kiAnbieter.chat(any())).thenReturn(
                new KiTypen.KiAntwort("  Rechnung Müller/GmbH_2026-10-11.pdf ", List.of()));

        assertThat(service.generateFilename(new byte[] {1, 2, 3})).isEqualTo("Rechnung_MllerGmbH_2026-10-11pdf");
    }

    @Test
    void keinAnbieterKonfiguriert_gibtErsatzname() throws Exception {
        when(kiAnbieterService.waehle(KiFunktion.SCANNER)).thenThrow(new KiAnbieterFehler("kein Anbieter"));

        assertThat(service.generateFilename(new byte[] {1})).startsWith("Scan_");
    }

    @Test
    void kiFehler_gibtErsatzname() throws Exception {
        when(kiAnbieterService.waehle(KiFunktion.SCANNER)).thenReturn(kiAnbieter);
        when(kiAnbieter.chat(any())).thenThrow(new RuntimeException("Verbindung unterbrochen"));

        assertThat(service.generateFilename(new byte[] {1, 2, 3})).startsWith("Scan_");
    }

    @Test
    void nurSonderzeichen_gibtErsatzname() throws Exception {
        when(kiAnbieterService.waehle(KiFunktion.SCANNER)).thenReturn(kiAnbieter);
        when(kiAnbieter.chat(any())).thenReturn(new KiTypen.KiAntwort("***", List.of()));

        assertThat(service.generateFilename(new byte[] {1, 2, 3})).startsWith("Scan_");
    }
}
