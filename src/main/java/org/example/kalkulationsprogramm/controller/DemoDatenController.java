package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.service.demo.DemoDatenService;
import org.example.kalkulationsprogramm.service.demo.DemoDatenService.Ergebnis;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Die früheren Demo-Daten wieder entfernen (nur Administratoren, siehe SecurityConfig). Entfernt nur, was die Demo-Daten selbst angelegt haben. */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoDatenController {

    private final DemoDatenService service;

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("vorhanden", service.vorhanden());
    }

    @DeleteMapping("/daten")
    public Ergebnis entfernen() {
        return service.entfernen();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> wirdVerwendet(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Die Beispieldaten konnten nicht entfernt werden, weil an einem Beispiel-Auftrag oder -Mitarbeiter schon echte Daten hängen (zum Beispiel Zeitbuchungen oder Rechnungen). Es wurde nichts entfernt."));
    }
}
