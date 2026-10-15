package org.example.kalkulationsprogramm.service.ablage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Baut die lesbare Ablage jede Nacht neu auf, wenn {@code erp.ablage.lesbar.automatisch=true} gesetzt ist (Standard: aus). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "erp.ablage.lesbar.automatisch", havingValue = "true")
public class LesbareAblageNachtlauf {

    private final LesbareAblageService service;

    @Scheduled(cron = "${erp.ablage.lesbar.cron:0 30 3 * * *}")
    public void aufbauen() {
        try {
            service.aufbauen(false);
        } catch (Exception e) {
            log.warn("Der nächtliche Aufbau der lesbaren Ablage ist fehlgeschlagen", e);
        }
    }
}
