package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Stand;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Testergebnis;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Einstellungen für „Wunsch an die Entwicklung“ (Gitea). Unter {@code /api/settings}, also nur für Administratoren; der Token wird nie zurückgegeben. */
@RestController
@RequestMapping("/api/settings/ki-wunsch")
@RequiredArgsConstructor
public class EntwicklungsWunschController {

    private final EntwicklungsWunschService service;

    /** {@code token}: fehlt = unverändert lassen, leer = entfernen. */
    public record Eingabe(String adresse, String repository, String token) { }

    @GetMapping
    public Stand stand() {
        return service.stand();
    }

    @PutMapping
    public Stand speichern(@RequestBody Eingabe e) {
        try {
            return service.speichern(e.adresse(), e.repository(), e.token());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/test")
    public Testergebnis teste() {
        return service.teste();
    }
}
