package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.MetallbauVorlagenService;
import org.example.kalkulationsprogramm.service.MetallbauVorlagenService.ArbeitsgangVorschau;
import org.example.kalkulationsprogramm.service.MetallbauVorlagenService.Ergebnis;
import org.example.kalkulationsprogramm.service.MetallbauVorlagenService.ProfilVorschau;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Vorlagen für Metallbau zum Übernehmen (Arbeitsgänge, Stahlprofile und Bleche). Nur Administratoren (SecurityConfig). */
@RestController
@RequestMapping("/api/vorlagen/metallbau")
@RequiredArgsConstructor
public class MetallbauVorlagenController {

    private final MetallbauVorlagenService service;

    public record ArbeitsgangAuswahl(List<String> namen, Long abteilungId) { }

    public record ProfilAuswahl(List<String> nummern) { }

    @GetMapping("/arbeitsgaenge")
    public ArbeitsgangVorschau arbeitsgaenge() {
        return service.arbeitsgangVorschau();
    }

    @PostMapping("/arbeitsgaenge/uebernehmen")
    public Ergebnis arbeitsgaengeUebernehmen(@RequestBody ArbeitsgangAuswahl a) {
        try {
            return service.arbeitsgaengeUebernehmen(a == null ? null : a.namen(), a == null ? null : a.abteilungId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ProfilVorschau profile() {
        return service.profilVorschau();
    }

    @PostMapping("/profile/uebernehmen")
    public Ergebnis profileUebernehmen(@RequestBody ProfilAuswahl a) {
        try {
            return service.profileUebernehmen(a == null ? null : a.nummern());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
