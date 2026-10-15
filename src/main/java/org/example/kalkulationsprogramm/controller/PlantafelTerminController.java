package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanTermine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Plantafel (#323): einen Kalendertermin in der Ansicht verschieben. */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelTerminController {

    private final PlanTermine service;

    public record Verschiebung(LocalDate datum, LocalTime von, LocalTime bis, Long vonMitarbeiterId, Long nachMitarbeiterId) { }

    @PutMapping("/termine/{id}/verschieben")
    public Map<String, Long> verschiebe(@PathVariable Long id, @RequestBody Verschiebung v) {
        service.verschiebe(id, v.datum(), v.von(), v.bis(), v.vonMitarbeiterId(), v.nachMitarbeiterId());
        return Map.of("id", id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
