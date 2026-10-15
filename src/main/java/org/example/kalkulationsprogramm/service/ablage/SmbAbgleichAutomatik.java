package org.example.kalkulationsprogramm.service.ablage;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gleicht automatisch mit der Netzwerkfreigabe ab, wenn der Abgleich eingeschaltet ist und ein Abstand gewählt wurde (Einstellungen, Dateien). Jede Minute wird
 * nur geprüft, ob der Abstand seit dem letzten Versuch vorbei ist; bei einem Fehler wird erst nach dem nächsten Abstand wieder versucht.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmbAbgleichAutomatik {

    private final SmbAbgleichEinstellungenService einstellungen;
    private final SmbAbgleichService abgleich;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void tick() {
        tick(LocalDateTime.now());
    }

    void tick(LocalDateTime jetzt) {
        try {
            int minuten = einstellungen.intervallMinuten();
            if (minuten <= 0 || !einstellungen.lade().aktiv()) {
                return;
            }
            LocalDateTime zuletzt = einstellungen.letzterLauf();
            if (zuletzt != null && Duration.between(zuletzt, jetzt).toMinutes() < minuten) {
                return;
            }
            abgleich.abgleichen(false);
        } catch (ResponseStatusException e) {
            // Läuft schon ein Abgleich oder ist die Freigabe nicht erreichbar: der Fehler steht beim letzten Ergebnis, nächster Versuch nach dem Abstand
            log.debug("Automatischer SMB-Abgleich nicht ausgeführt: {}", e.getReason());
        } catch (RuntimeException e) {
            log.warn("Automatischer SMB-Abgleich fehlgeschlagen", e);
        }
    }
}
