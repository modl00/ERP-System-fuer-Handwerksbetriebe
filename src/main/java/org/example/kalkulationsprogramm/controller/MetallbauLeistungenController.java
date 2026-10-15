package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.MetallbauLeistungenService;
import org.example.kalkulationsprogramm.service.MetallbauLeistungenService.Ergebnis;
import org.example.kalkulationsprogramm.service.MetallbauLeistungenService.Vorschau;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Fertige Leistungen für Metallbau zum Übernehmen (mit Baugruppen, Artikeln, Arbeitsgängen, Maschinen, Fremdleistungen). Nur Administratoren (SecurityConfig). */
@RestController
@RequestMapping("/api/vorlagen/metallbau/leistungen")
@RequiredArgsConstructor
public class MetallbauLeistungenController {

    private final MetallbauLeistungenService service;

    public record Auswahl(List<String> namen) { }

    @GetMapping
    public Vorschau vorschau() {
        return service.vorschau();
    }

    @PostMapping("/uebernehmen")
    public Ergebnis uebernehmen(@RequestBody Auswahl a) {
        try {
            return service.uebernehmen(a == null ? null : a.namen());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
