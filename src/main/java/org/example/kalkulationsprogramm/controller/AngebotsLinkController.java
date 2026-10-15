package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.AngebotsLinkService;
import org.example.kalkulationsprogramm.service.AngebotsLinkService.Konfiguration;
import org.example.kalkulationsprogramm.service.AngebotsLinkService.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Link „Angebot ansehen und annehmen“ (#88): Einstellung für Administratoren, Stand für den Dokumenteditor.
 */
@RestController
@RequiredArgsConstructor
public class AngebotsLinkController {

    private final AngebotsLinkService service;

    /** {@code gueltigkeitTage}: weglassen = unverändert. */
    public record SpeichernDto(boolean aktiv, String adresse, Integer gueltigkeitTage) { }

    /** Einstellungen (nur Administratoren, liegt unter {@code /api/settings/**}). */
    @GetMapping("/api/settings/angebots-link")
    public ResponseEntity<Konfiguration> konfiguration() {
        return ResponseEntity.ok(service.konfiguration());
    }

    @PutMapping("/api/settings/angebots-link")
    public ResponseEntity<Konfiguration> speichern(@RequestBody SpeichernDto eingabe) {
        return ResponseEntity.ok(service.speichern(eingabe.aktiv(), eingabe.adresse(), eingabe.gueltigkeitTage()));
    }

    /** Für den Dokumenteditor (jeder angemeldete Benutzer): entsteht in Angebots-E-Mails ein Link, und mit welcher Standard-Gültigkeit? */
    @GetMapping("/api/ausgangs-dokumente/angebots-link")
    public ResponseEntity<Status> status() {
        return ResponseEntity.ok(service.status());
    }
}
