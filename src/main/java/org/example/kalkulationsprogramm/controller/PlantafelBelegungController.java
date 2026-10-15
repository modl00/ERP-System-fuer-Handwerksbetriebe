package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanBelegung;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Antwort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Plantafel (#323): die Belegung von Mitarbeitern und Ressourcen in einem Zeitraum (nur Anzeige). */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelBelegungController {

    private final PlanBelegung service;

    @GetMapping("/belegung")
    public Antwort belegung(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {
        return service.belegung(von, bis);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
