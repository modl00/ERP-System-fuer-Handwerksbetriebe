package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungDto;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungEingabeDto;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungVergleichDto;
import org.example.kalkulationsprogramm.service.FremdleistungService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Preisliste der Veredler und Fremdbetriebe (Fremdleistungen) mit Preisvergleich. */
@RestController
@RequestMapping("/api/fremdleistungen")
@RequiredArgsConstructor
public class FremdleistungController {

    private final FremdleistungService service;

    @GetMapping
    public List<FremdleistungDto> alle(@RequestParam(required = false) Long lieferantId) {
        return service.alle(lieferantId);
    }

    @PostMapping
    public FremdleistungDto anlegen(@Valid @RequestBody FremdleistungEingabeDto eingabe) {
        return service.anlegen(eingabe);
    }

    @PutMapping("/{id}")
    public FremdleistungDto aendern(@PathVariable Long id, @Valid @RequestBody FremdleistungEingabeDto eingabe) {
        return service.aendern(id, eingabe);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    /** Dieselbe Fremdleistung bei mehreren Veredlern für eine Menge; der günstigste steht vorn. */
    @GetMapping("/vergleich")
    public List<FremdleistungVergleichDto> vergleich(@RequestParam String bezeichnung, @RequestParam BigDecimal menge, @RequestParam(required = false) BigDecimal gewichtKg) {
        return service.vergleich(bezeichnung, menge, gewichtKg);
    }
}
