package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.testbox.TestboxService;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Einstellung;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Punkt;
import org.example.kalkulationsprogramm.service.testbox.TestboxService.Vorschlag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Test-Box (nur Administratoren, siehe SecurityConfig): Rückfragen aus Gitea beantworten. */
@RestController
@RequestMapping("/api/testbox")
@RequiredArgsConstructor
public class TestboxController {

    private final TestboxService service;

    public record EinstellungEingabe(String url, String repo, String token) { }

    public record AufbereitenEingabe(int nummer, String antwort, String einordnung) { }

    public record SendenEingabe(int nummer, String einordnung, String kommentar, String original) { }

    @GetMapping("/einstellung")
    public Einstellung einstellung() {
        return service.einstellung();
    }

    @PutMapping("/einstellung")
    public Einstellung speichere(@RequestBody EinstellungEingabe e) {
        return service.speichereEinstellung(e.url(), e.repo(), e.token());
    }

    @GetMapping("/punkte")
    public List<Punkt> punkte() {
        return service.punkte();
    }

    @PostMapping("/aufbereiten")
    public Vorschlag aufbereiten(@RequestBody AufbereitenEingabe e) {
        return service.aufbereiten(e.nummer(), e.antwort(), e.einordnung());
    }

    @PostMapping("/senden")
    public Map<String, Boolean> senden(@RequestBody SendenEingabe e) {
        service.senden(e.nummer(), e.einordnung(), e.kommentar(), e.original());
        return Map.of("gesendet", true);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
