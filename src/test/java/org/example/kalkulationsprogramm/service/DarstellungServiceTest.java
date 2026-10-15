package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DarstellungServiceTest {

    @Mock
    private SystemSettingRepository repository;

    @InjectMocks
    private DarstellungService service;

    private static SystemSetting eintrag(String wert) {
        SystemSetting s = new SystemSetting();
        s.setKey(DarstellungService.SCHLUESSEL_FARBE);
        s.setValue(wert);
        return s;
    }

    @Test
    void ohneEintragGiltDasStandardRose() {
        when(repository.findById(DarstellungService.SCHLUESSEL_FARBE)).thenReturn(Optional.empty());
        assertTrue(service.farbe().isEmpty());
    }

    @Test
    void gespeicherteFarbeWirdGeliefert() {
        when(repository.findById(DarstellungService.SCHLUESSEL_FARBE)).thenReturn(Optional.of(eintrag("#0f766e")));
        assertEquals("#0f766e", service.farbe().orElseThrow());
    }

    @Test
    void kaputterGespeicherterWertWirdIgnoriert() {
        when(repository.findById(DarstellungService.SCHLUESSEL_FARBE)).thenReturn(Optional.of(eintrag("rot; background:url(x)")));
        assertTrue(service.farbe().isEmpty());
    }

    @Test
    void speichertInKleinbuchstabenUndOhneLeerzeichen() {
        when(repository.findById(DarstellungService.SCHLUESSEL_FARBE)).thenReturn(Optional.empty());

        assertEquals("#0f766e", service.setzeFarbe("  #0F766E ").orElseThrow());

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals(DarstellungService.SCHLUESSEL_FARBE, captor.getValue().getKey());
        assertEquals("#0f766e", captor.getValue().getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rot", "#fff", "#12345", "#1234567", "0f766e", "#gggggg", "#0f766e;color:red",
            "'; DROP TABLE x; --", "<script>alert(1)</script>"})
    void ungueltigeFarbenWerdenAbgelehnt(String farbe) {
        assertThrows(IllegalArgumentException.class, () -> service.setzeFarbe(farbe));
        verify(repository, never()).save(any());
    }

    @Test
    void ueberlangeEingabeWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> service.setzeFarbe("#" + "a".repeat(10_001)));
        verify(repository, never()).save(any());
    }

    @Test
    void leereEingabeStelltDasStandardRoseWiederHer() {
        SystemSetting vorhanden = eintrag("#0f766e");
        when(repository.findById(DarstellungService.SCHLUESSEL_FARBE)).thenReturn(Optional.of(vorhanden));

        assertTrue(service.setzeFarbe("").isEmpty());

        verify(repository).delete(vorhanden);
    }
}
