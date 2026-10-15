package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.example.kalkulationsprogramm.service.NummernkreisService.Eingabe;
import org.example.kalkulationsprogramm.service.NummernkreisService.Zeile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Nummernkreise einstellen (Issue #62); nur Administratoren ({@code SecurityConfig}). */
@RestController
@RequestMapping("/api/nummernkreise")
@RequiredArgsConstructor
public class NummernkreisController {

    private final NummernkreisService service;

    @GetMapping
    public List<Zeile> alle() {
        return service.alle();
    }

    @PutMapping("/{art}")
    public Zeile speichern(@PathVariable String art, @RequestBody Eingabe eingabe) {
        return service.speichern(art, eingabe);
    }

    /** Zurück zum bisherigen Format. */
    @DeleteMapping("/{art}")
    public Zeile zuruecksetzen(@PathVariable String art) {
        return service.zuruecksetzen(art);
    }
}
