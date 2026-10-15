package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** Der automatische Bericht darf den Abschluss einer Prüfung nie gefährden. */
class WartungBerichtAutomatikTest {

    WartungBerichtService berichte = mock(WartungBerichtService.class);
    WartungBerichtAutomatik automatik = new WartungBerichtAutomatik(berichte);

    @Test
    void legtDenBerichtZumAbgeschlossenenProtokollAb() {
        automatik.nachAbschluss(new WartungProtokollAbgeschlossen(42L));
        verify(berichte).erstelleAutomatischImProjekt(42L);
    }

    @Test
    void einFehlerBeimBerichtWirdGeschluckt() {
        doThrow(new IllegalStateException("Platte voll")).when(berichte).erstelleAutomatischImProjekt(42L);
        assertThatCode(() -> automatik.nachAbschluss(new WartungProtokollAbgeschlossen(42L))).doesNotThrowAnyException();
    }
}
