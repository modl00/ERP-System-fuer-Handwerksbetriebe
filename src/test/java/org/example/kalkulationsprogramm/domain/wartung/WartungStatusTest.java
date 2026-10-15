package org.example.kalkulationsprogramm.domain.wartung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WartungStatusTest {

    @Test
    @DisplayName("Ein offenes Protokoll darf in Arbeit gehen, abgeschlossen oder archiviert werden")
    void offenKannWeiter() {
        assertThat(WartungStatus.OFFEN.darfWechselnZu(WartungStatus.IN_BEARBEITUNG)).isTrue();
        assertThat(WartungStatus.OFFEN.darfWechselnZu(WartungStatus.ABGESCHLOSSEN)).isTrue();
        assertThat(WartungStatus.OFFEN.darfWechselnZu(WartungStatus.ARCHIVIERT)).isTrue();
    }

    @Test
    @DisplayName("Ein abgeschlossenes Protokoll lässt sich wieder öffnen (in Arbeit) oder archivieren, aber nicht auf offen stellen")
    void abgeschlossenKannNurWiederGeoeffnetOderArchiviertWerden() {
        assertThat(WartungStatus.ABGESCHLOSSEN.darfWechselnZu(WartungStatus.IN_BEARBEITUNG)).isTrue();
        assertThat(WartungStatus.ABGESCHLOSSEN.darfWechselnZu(WartungStatus.ARCHIVIERT)).isTrue();
        assertThat(WartungStatus.ABGESCHLOSSEN.darfWechselnZu(WartungStatus.OFFEN)).isFalse();
    }

    @Test
    @DisplayName("Aus dem Archiv geht es nur zurück auf offen oder abgeschlossen")
    void archivierteWiederherstellen() {
        assertThat(WartungStatus.ARCHIVIERT.erlaubteZiele())
                .containsExactlyInAnyOrder(WartungStatus.OFFEN, WartungStatus.ABGESCHLOSSEN);
    }

    @Test
    @DisplayName("Kein Status darf auf sich selbst wechseln und null ist nie erlaubt")
    void keinWechselAufSichSelbstOderNull() {
        for (WartungStatus status : WartungStatus.values()) {
            assertThat(status.darfWechselnZu(status)).as(status.name()).isFalse();
            assertThat(status.darfWechselnZu(null)).as(status.name()).isFalse();
        }
    }

    @Test
    @DisplayName("Inhalte sind nur bei offen und in Arbeit bearbeitbar")
    void bearbeitbarNurSolangeNichtAbgeschlossen() {
        assertThat(WartungStatus.OFFEN.istInhaltBearbeitbar()).isTrue();
        assertThat(WartungStatus.IN_BEARBEITUNG.istInhaltBearbeitbar()).isTrue();
        assertThat(WartungStatus.ABGESCHLOSSEN.istInhaltBearbeitbar()).isFalse();
        assertThat(WartungStatus.ARCHIVIERT.istInhaltBearbeitbar()).isFalse();
    }
}
