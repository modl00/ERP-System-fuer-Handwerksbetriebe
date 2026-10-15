package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.MaschineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Übernimmt beim Start den alten Maschinenstand (Satz am Arbeitsgang) in die Maschinenliste (Stufe 2 von #166), damit die Leistungspreise gleich bleiben. Läuft in Docker
 * und unter Windows gleich (dort ohne Flyway), mehrfach ausführbar, und ein Fehler hier verhindert den Start nicht.
 */
@Component
@RequiredArgsConstructor
public class MaschinenUebernahmeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MaschinenUebernahmeRunner.class);

    private final MaschineService service;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int neu = service.uebernehmeAltbestand();
            if (neu > 0) {
                log.info("Maschinenliste: {} Maschine(n) aus den Arbeitsgängen übernommen", neu);
            }
        } catch (RuntimeException e) {
            log.warn("Maschinenliste: Übernahme des alten Stands nicht möglich: {}", e.getClass().getSimpleName());
        }
    }
}
