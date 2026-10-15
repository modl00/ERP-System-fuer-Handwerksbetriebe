package org.example.kalkulationsprogramm.service.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Der Speicher für die letzten Server-Fehler (#255) mit dem echten Log-System. */
class ServerFehlerPufferTest {

    private final ServerFehlerPuffer puffer = new ServerFehlerPuffer();
    private final Logger log = LoggerFactory.getLogger("org.example.kalkulationsprogramm.service.TestDienst");

    @BeforeEach
    void anmelden() {
        puffer.anmelden();
    }

    @AfterEach
    void abmelden() {
        puffer.abmelden();
    }

    @Test
    void einFehlerImLogLandetImPufferMitKurznamenUndFehlerart() {
        log.error("Speichern fehlgeschlagen für {}", "Auftrag 7", new IllegalStateException("kaputt"));

        List<ServerFehlerPuffer.Eintrag> letzte = puffer.letzte(5, null);
        assertThat(letzte).hasSize(1);
        ServerFehlerPuffer.Eintrag e = letzte.get(0);
        assertThat(e.logger()).isEqualTo("TestDienst");
        assertThat(e.meldung()).isEqualTo("Speichern fehlgeschlagen für Auftrag 7");
        assertThat(e.fehlerart()).isEqualTo("java.lang.IllegalStateException");
        assertThat(e.stellen()).anyMatch(s -> s.startsWith("org.example."));
    }

    @Test
    void warnungenUndInfosWerdenNichtGemerkt() {
        log.warn("nur eine Warnung");
        log.info("nur eine Info");
        assertThat(puffer.letzte(5, null)).isEmpty();
    }

    @Test
    void ueberlangeMeldungenWerdenGekuerztUndEinzeilig() {
        log.error("a\nb\n" + "z".repeat(1_000));
        String m = puffer.letzte(1, null).get(0).meldung();
        assertThat(m).doesNotContain("\n").hasSizeLessThan(420).endsWith("…");
    }

    @Test
    void derPufferIstBegrenztUndNennetdieNeuestenZuletzt() {
        for (int i = 0; i < ServerFehlerPuffer.MAX + 50; i++) {
            log.error("Fehler Nummer {}", i);
        }
        List<ServerFehlerPuffer.Eintrag> alle = puffer.letzte(10_000, null);
        assertThat(alle).hasSize(ServerFehlerPuffer.MAX);
        assertThat(alle.get(alle.size() - 1).meldung()).isEqualTo("Fehler Nummer " + (ServerFehlerPuffer.MAX + 49));
        assertThat(puffer.letzte(3, null)).extracting(ServerFehlerPuffer.Eintrag::meldung).endsWith("Fehler Nummer " + (ServerFehlerPuffer.MAX + 49));
    }

    @Test
    void nurDieLetztenEinträgeInDemGewuenschtenZeitraum() {
        log.error("alt");
        Instant spaeter = Instant.now().plusSeconds(60);
        assertThat(puffer.letzte(5, spaeter)).isEmpty();
        assertThat(puffer.letzte(5, Instant.now().minusSeconds(60))).hasSize(1);
    }

    @Test
    void nachDemAbmeldenWirdNichtsMehrGemerkt() {
        puffer.abmelden();
        log.error("danach");
        assertThat(puffer.letzte(5, null)).isEmpty();
        puffer.anmelden();
    }
}
