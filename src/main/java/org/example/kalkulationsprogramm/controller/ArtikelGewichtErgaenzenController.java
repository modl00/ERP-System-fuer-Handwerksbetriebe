package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.ArtikelGewichtErgaenzenService;
import org.example.kalkulationsprogramm.service.ArtikelGewichtErgaenzenService.Ergebnis;
import org.example.kalkulationsprogramm.service.ArtikelGewichtErgaenzenService.Vorschau;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Gewichte im Artikelstamm ergänzen (Issue #252). Nur Administratoren (SecurityConfig). */
@RestController
@RequestMapping("/api/artikel/gewichte-ergaenzen")
@RequiredArgsConstructor
public class ArtikelGewichtErgaenzenController {

    private final ArtikelGewichtErgaenzenService service;

    public record Auswahl(List<Long> artikelIds) { }

    @GetMapping("/vorschau")
    public Vorschau vorschau() {
        return service.vorschau();
    }

    @PostMapping("/uebernehmen")
    public Ergebnis uebernehmen(@RequestBody Auswahl auswahl) {
        try {
            return service.uebernehme(auswahl == null ? null : auswahl.artikelIds());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
