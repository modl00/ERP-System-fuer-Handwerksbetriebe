package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.PersonDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.RessourceDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.Zuweisung;
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

/** Plantafel (#323): Ressourcen verwalten und Vorgänge Mitarbeitern und Ressourcen zuweisen. Eingabefehler kommen als 400 mit Klartext. */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelZuweisungController {

    private final PlanungsZuweisung service;

    /** Leere Felder bleiben unverändert, außer beim Anlegen. */
    public record RessourceEingabe(String name, String art, Integer sortierung, Boolean aktiv) { }

    @GetMapping("/personen")
    public List<PersonDto> personen() {
        return service.personen();
    }

    @GetMapping("/ressourcen")
    public List<RessourceDto> ressourcen() {
        return service.ressourcen();
    }

    @PostMapping("/ressourcen")
    public RessourceDto anlegen(@RequestBody RessourceEingabe r) {
        return service.ressourceSpeichern(null, r.name(), r.art(), r.sortierung(), r.aktiv());
    }

    @PutMapping("/ressourcen/{id}")
    public RessourceDto aendern(@PathVariable Long id, @RequestBody RessourceEingabe r) {
        return service.ressourceSpeichern(id, r.name(), r.art(), r.sortierung(), r.aktiv());
    }

    @PutMapping("/vorgaenge/{id}/zuweisung")
    public AuftragDto zuweisen(@PathVariable Long id, @RequestBody Zuweisung z) {
        return service.zuweisen(id, z);
    }

    /** Jemand anderes hat den Vorgang inzwischen geändert (HTTP 409): Die Ansicht lädt neu. */
    @ExceptionHandler(org.example.kalkulationsprogramm.service.planung.PlanKonfliktException.class)
    public ResponseEntity<Map<String, String>> konflikt(org.example.kalkulationsprogramm.service.planung.PlanKonfliktException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
