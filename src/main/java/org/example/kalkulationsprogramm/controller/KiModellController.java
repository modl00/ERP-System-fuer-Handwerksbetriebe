package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiModellService;
import org.example.kalkulationsprogramm.service.KiModellService.Testergebnis;
import org.example.kalkulationsprogramm.service.KiModellService.Uebersicht;
import org.example.kalkulationsprogramm.service.KiModellService.VerfuegbaresModell;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Modellwahl der KI-Funktionen (Einstellungen → KI). Liegt unter {@code /api/settings/**} und ist damit nur für Admins. */
@RestController
@RequestMapping("/api/settings/ki-modelle")
@RequiredArgsConstructor
public class KiModellController {

    private final KiModellService modelle;

    public record SpeichernDto(Map<String, String> modelle, Boolean websuche) { }

    public record TestDto(String modell) { }

    @GetMapping
    public ResponseEntity<Uebersicht> uebersicht() {
        return ResponseEntity.ok(modelle.uebersicht());
    }

    @PutMapping
    public ResponseEntity<Uebersicht> speichern(@RequestBody SpeichernDto eingabe) {
        return ResponseEntity.ok(modelle.speichern(eingabe.modelle(), eingabe.websuche()));
    }

    /** Die Modelle, die der hinterlegte Schlüssel benutzen kann. */
    @GetMapping("/verfuegbar")
    public ResponseEntity<List<VerfuegbaresModell>> verfuegbar() {
        return ResponseEntity.ok(modelle.verfuegbare());
    }

    /** Mini-Anfrage an ein Modell: zeigt, ob es antwortet oder warum nicht. */
    @PostMapping("/test")
    public ResponseEntity<Testergebnis> test(@RequestBody TestDto eingabe) {
        return ResponseEntity.ok(modelle.teste(eingabe.modell()));
    }
}
