package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.FeiertagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Das Bundesland, nach dem Feiertage gelten (Zeitkonto, Lohn, Plantafel). Nur für Administratoren (Pfad {@code /api/settings/**}). */
@RestController
@RequestMapping("/api/settings/feiertage-bundesland")
@RequiredArgsConstructor
public class FeiertagBundeslandController {

    private final FeiertagService feiertagService;

    public record Land(String kuerzel, String name) { }

    public record Antwort(String bundesland, List<Land> auswahl) { }

    public record Anfrage(String bundesland) { }

    @GetMapping
    public Antwort lesen() {
        return antwort(feiertagService.bundesland());
    }

    @PutMapping
    public Antwort speichern(@RequestBody Anfrage anfrage) {
        return antwort(feiertagService.setzeBundesland(anfrage.bundesland()));
    }

    private static Antwort antwort(String aktuell) {
        return new Antwort(aktuell, FeiertagService.BUNDESLAENDER.entrySet().stream().map(e -> new Land(e.getKey(), e.getValue())).toList());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
