package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme;
import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme.Aufnahme;
import org.example.kalkulationsprogramm.service.planung.PlanungsAufnahme.Eingabe;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.OffenerAuftrag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#331, Abschnitt 5): das Fenster bei der Auftragsbestätigung und der Knopf im Projekt. {@code GET} liefert die vorausgefüllten Angaben,
 * {@code POST …/planen} legt den Auftrag an (oder mit {@code spaeter} die Standardkette mit geschätzten Werten).
 */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelAufnahmeController {

    private final PlanungsAufnahme aufnahme;
    private final PlanungsService planung;

    public record Fenster(boolean aktiv) { }

    @GetMapping("/auftraege/{projektId}/aufnahme")
    public Aufnahme aufnahme(@PathVariable Long projektId) {
        return aufnahme.aufnahme(projektId);
    }

    @PostMapping("/auftraege/{projektId}/planen")
    public AuftragDto planen(@PathVariable Long projektId, @RequestBody Eingabe e) {
        return aufnahme.planen(projektId, e);
    }

    /** Aufträge, deren Planung mit „Später“ übersprungen wurde: neu, noch nicht eingeplant. */
    @GetMapping("/offene-planung")
    public List<OffenerAuftrag> offene() {
        return planung.offeneAuftraege();
    }

    /** Zeigt das Fenster bei der Auftragsbestätigung für den angemeldeten Benutzer? */
    @GetMapping("/ab-fenster")
    public Fenster fenster() {
        return new Fenster(aufnahme.fensterAktiv());
    }

    @PutMapping("/ab-fenster")
    public Fenster fensterSetzen(@RequestBody Fenster f) {
        return new Fenster(aufnahme.fensterSetzen(f != null && f.aktiv()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
