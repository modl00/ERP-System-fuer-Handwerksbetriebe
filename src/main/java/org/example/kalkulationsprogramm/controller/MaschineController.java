package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Maschine.MaschineDto;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineEingabeDto;
import org.example.kalkulationsprogramm.service.MaschineService;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Aufstellung;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Maschinenliste mit Kostenrechner (Stufe 2 von #166). Nur für Administratoren (siehe SecurityConfig). */
@RestController
@RequestMapping("/api/maschinen")
@RequiredArgsConstructor
public class MaschineController {

    private final MaschineService service;

    @GetMapping
    public List<MaschineDto> alle() {
        return service.alle();
    }

    @PostMapping
    public MaschineDto anlegen(@Valid @RequestBody MaschineEingabeDto eingabe, Authentication auth) {
        return service.anlegen(eingabe, auth == null ? null : auth.getName());
    }

    @PutMapping("/{id}")
    public MaschineDto aendern(@PathVariable Long id, @Valid @RequestBody MaschineEingabeDto eingabe, Authentication auth) {
        return service.aendern(id, eingabe, auth == null ? null : auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/verlauf")
    public List<MaschineDto.Verlauf> verlauf(@PathVariable Long id) {
        return service.verlauf(id);
    }

    /** Rechnet die Angaben durch, ohne zu speichern (für die Vorschau im Dialog). */
    @PostMapping("/rechner")
    public Aufstellung rechner(@RequestBody MaschinenKostenRechner.Eingabe eingabe) {
        return service.rechne(eingabe);
    }
}
