package org.example.kalkulationsprogramm.service.ablage;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Der automatische Abgleich: nur wenn eingeschaltet, nur nach dem gewählten Abstand, nie mit einem Fehler nach außen. */
class SmbAbgleichAutomatikTest {

    static final LocalDateTime JETZT = LocalDateTime.of(2026, 10, 8, 12, 0);

    SmbAbgleichEinstellungenService einstellungen = mock(SmbAbgleichEinstellungenService.class);
    SmbAbgleichService abgleich = mock(SmbAbgleichService.class);
    SmbAbgleichAutomatik automatik = new SmbAbgleichAutomatik(einstellungen, abgleich);

    @BeforeEach
    void setUp() {
        when(einstellungen.lade()).thenReturn(new SmbAbgleichEinstellungen(true, "192.168.178.21", "Unternehmen", "ERP", "", "", ""));
    }

    @Test
    void ohneAbstandNurAufKnopfdruck() {
        when(einstellungen.intervallMinuten()).thenReturn(0);
        automatik.tick(JETZT);
        verify(abgleich, never()).abgleichen(anyBoolean());
    }

    @Test
    void ausgeschaltetGleichtNichtAb() {
        when(einstellungen.intervallMinuten()).thenReturn(5);
        when(einstellungen.lade()).thenReturn(new SmbAbgleichEinstellungen(false, "192.168.178.21", "Unternehmen", "ERP", "", "", ""));
        automatik.tick(JETZT);
        verify(abgleich, never()).abgleichen(anyBoolean());
    }

    @Test
    void erstesMalOhneFruehereLaufGleichtSofortAb() {
        when(einstellungen.intervallMinuten()).thenReturn(15);
        when(einstellungen.letzterLauf()).thenReturn(null);
        automatik.tick(JETZT);
        verify(abgleich).abgleichen(false);
    }

    @Test
    void erstNachDemAbstandWiederAbgleichen() {
        when(einstellungen.intervallMinuten()).thenReturn(15);
        when(einstellungen.letzterLauf()).thenReturn(JETZT.minusMinutes(14));
        automatik.tick(JETZT);
        verify(abgleich, never()).abgleichen(anyBoolean());

        when(einstellungen.letzterLauf()).thenReturn(JETZT.minusMinutes(15));
        automatik.tick(JETZT);
        verify(abgleich).abgleichen(false);
    }

    @Test
    void fehlerWerdenGeschlucktDamitDerPlanerWeiterLaeuft() {
        when(einstellungen.intervallMinuten()).thenReturn(5);
        when(einstellungen.letzterLauf()).thenReturn(null);
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Das Gerät antwortet nicht.")).when(abgleich).abgleichen(false);
        automatik.tick(JETZT);
        org.mockito.Mockito.doThrow(new IllegalStateException("kaputt")).when(abgleich).abgleichen(false);
        automatik.tick(JETZT);
    }
}
