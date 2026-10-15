package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanungsKi;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.Schritt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** KI-Hilfe der Plantafel (#184): Vorgänge vorschlagen und den Termin erklären. Die KI speichert nichts; angelegt wird nur, was der Anwender auswählt. */
@RestController
@RequestMapping("/api/plantafel/ki")
@RequiredArgsConstructor
public class PlantafelKiController {

    private final PlanungsKi ki;
    private final PlanungsService planung;

    public record Auswahl(List<Schritt> schritte) { }

    @GetMapping("/auftraege/{projektId}/vorgaenge")
    public PlanungsKi.KettenVorschlag vorschlag(@PathVariable Long projektId) {
        return ki.kettenVorschlag(projektId);
    }

    @PostMapping("/auftraege/{projektId}/vorgaenge/uebernehmen")
    public AuftragDto uebernehmen(@PathVariable Long projektId, @RequestBody Auswahl a) {
        return planung.kettePerAuswahl(projektId, a == null ? null : a.schritte());
    }

    @GetMapping("/auftraege/{projektId}/erklaerung")
    public PlanungsKi.Erklaerung erklaerung(@PathVariable Long projektId) {
        return ki.erklaeren(projektId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
