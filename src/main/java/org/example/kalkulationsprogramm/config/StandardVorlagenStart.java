package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.StandardVorlagenService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Legt bei einer frischen Installation die Standardvorlagen an, damit Angebot und Rechnung sofort brauchbar sind. Gibt es schon
 * Vorlagen oder Textbausteine, passiert nichts: Wer schon eigene hat, drückt bei Bedarf selbst auf "Standardvorlagen anlegen".
 * Ein Fehler hier darf den Start nie verhindern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "erp.standardvorlagen.beim-start", havingValue = "true", matchIfMissing = true)
public class StandardVorlagenStart implements ApplicationRunner {

    private final StandardVorlagenService standardVorlagenService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (standardVorlagenService.nochNichtsVorhanden()) {
                standardVorlagenService.anlegen();
            }
        } catch (Exception e) {
            log.warn("Standardvorlagen konnten beim Start nicht angelegt werden (über den Formularwesen-Editor nachholbar)", e);
        }
    }
}
