package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.AufschlagService.Uebersicht;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Aufschläge einstellen: allgemein und je Kategorie (Issue #121). Der Aufschlag am Artikel bleibt am Artikel. */
@RestController
@RequestMapping("/api/aufschlaege")
@RequiredArgsConstructor
public class AufschlagController {

    private final AufschlagService service;

    /** {@code prozent} leer = kein Aufschlag eingestellt. */
    public record Eingabe(BigDecimal prozent) { }

    @GetMapping
    public Uebersicht uebersicht() {
        return service.uebersicht();
    }

    /** Welcher Aufschlag für diesen Artikel gilt und woher er kommt (Artikel, Kategorie, allgemein oder keiner). */
    @GetMapping("/artikel/{id}")
    public AufschlagService.Wirksam fuerArtikel(@PathVariable Long id) {
        return service.wirksamerFuerArtikel(id);
    }

    @PutMapping("/allgemein")
    public Uebersicht allgemein(@RequestBody Eingabe eingabe) {
        return service.setzeAllgemein(eingabe == null ? null : eingabe.prozent());
    }

    @PutMapping("/kategorien/{id}")
    public Uebersicht kategorie(@PathVariable Integer id, @RequestBody Eingabe eingabe) {
        return service.setzeKategorie(id, eingabe == null ? null : eingabe.prozent());
    }
}
