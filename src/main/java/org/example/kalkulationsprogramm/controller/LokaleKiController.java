package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.LokalerKiService;
import org.example.kalkulationsprogramm.service.LokalerKiService.Konfiguration;
import org.example.kalkulationsprogramm.service.LokalerKiService.Testergebnis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Einstellungen der lokalen KI (Ollama u. a.). Liegt unter {@code /api/settings/**} und ist damit nur für Admins. */
@RestController
@RequestMapping("/api/settings/ki-lokal")
@RequiredArgsConstructor
public class LokaleKiController {

    private final LokalerKiService lokaleKi;

    /** {@code schluessel}: leer lassen (null) = unverändert, "" = entfernen. */
    public record SpeichernDto(String adresse, String modell, String schluessel, boolean hilfeLokal) { }

    @GetMapping
    public ResponseEntity<Konfiguration> konfiguration() {
        return ResponseEntity.ok(lokaleKi.konfiguration());
    }

    @PutMapping
    public ResponseEntity<Konfiguration> speichern(@RequestBody SpeichernDto eingabe) {
        return ResponseEntity.ok(lokaleKi.speichern(eingabe.adresse(), eingabe.modell(), eingabe.schluessel(), eingabe.hilfeLokal()));
    }

    /** Die Modelle, die der lokale Server anbietet. */
    @GetMapping("/modelle")
    public ResponseEntity<List<String>> modelle() {
        return ResponseEntity.ok(lokaleKi.modelle());
    }

    @PostMapping("/test")
    public ResponseEntity<Testergebnis> test() {
        return ResponseEntity.ok(lokaleKi.teste());
    }
}
