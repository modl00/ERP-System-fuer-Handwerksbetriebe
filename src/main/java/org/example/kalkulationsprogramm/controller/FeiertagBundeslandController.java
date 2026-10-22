package org.example.kalkulationsprogramm.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final FirmeninformationRepository firmeninformationRepository;

    public record Land(String kuerzel, String name) { }

    public record Antwort(String bundesland, List<Land> auswahl) { }

    public record Anfrage(String bundesland) { }

    @GetMapping
    public Antwort lesen() {
        return antwort(feiertagService.aktuellesBundesland());
    }

    @PutMapping
    @Transactional
    public Antwort speichern(@RequestBody Anfrage anfrage) {
        String kuerzel = anfrage.bundesland();
        Bundesland land = Bundesland.ausKuerzel(kuerzel)
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Bundesland: " + kuerzel));
        var firma = firmeninformationRepository.getOrCreate();
        firma.setBundesland(land.name());
        firmeninformationRepository.save(firma);
        feiertagService.bundeslandGeaendert(land.name());
        return antwort(land.name());
    }

    private static Antwort antwort(String aktuell) {
        return new Antwort(aktuell, Arrays.stream(Bundesland.values()).map(b -> new Land(b.name(), b.getAnzeigename())).toList());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
