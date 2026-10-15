package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Ergebnis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Die lesbare Ablage: ein Ordnerbaum mit verständlichen Namen auf dieselben Dateien. Nur für Administratoren (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/ablage/lesbar")
public class LesbareAblageController {

    /** Wo die lesbare Ablage liegt und ob sie jede Nacht automatisch aufgebaut wird. */
    public record Status(String zielordner, boolean automatisch) {
    }

    private final LesbareAblageService service;
    private final boolean automatisch;

    public LesbareAblageController(LesbareAblageService service, @Value("${erp.ablage.lesbar.automatisch:false}") boolean automatisch) {
        this.service = service;
        this.automatisch = automatisch;
    }

    @GetMapping
    public Status status() {
        return new Status(service.zielordner().toString(), automatisch);
    }

    /** Baut die lesbare Ablage auf, mit {@code probelauf=true} nur eine Vorschau ohne Änderung. */
    @PostMapping("/aufbauen")
    public ResponseEntity<Ergebnis> aufbauen(@RequestParam(defaultValue = "false") boolean probelauf) {
        return ResponseEntity.ok(service.aufbauen(probelauf));
    }
}
