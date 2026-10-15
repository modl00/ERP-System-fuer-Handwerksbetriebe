package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanungsIst;
import org.example.kalkulationsprogramm.service.planung.PlanungsIst.Zuordnung;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#331, Abschnitt 3.10): zu welcher Vorgangsart die gestempelte Zeit eines Arbeitsgangs zählt. Gilt, wenn die Abteilung allein nicht reicht
 * (mehrere Vorgangsarten teilen eine Abteilung). Nach jeder Änderung rechnet sich die Plantafel neu.
 */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelZuordnungController {

    private final PlanungsIst ist;
    private final PlanungsService planung;

    /** {@code typId} leer = automatisch über die Abteilung. */
    public record Eingabe(Long arbeitsgangId, Long typId) { }

    @GetMapping("/arbeitsgang-zuordnung")
    public List<Zuordnung> zuordnungen() {
        return ist.zuordnungen();
    }

    @PutMapping("/arbeitsgang-zuordnung")
    public List<Zuordnung> zuordnen(@RequestBody Eingabe e) {
        if (e == null || e.arbeitsgangId() == null) {
            throw new IllegalArgumentException("Bitte einen Arbeitsgang angeben.");
        }
        ist.zuordnen(e.arbeitsgangId(), e.typId());
        planung.neuBerechnenAlle();
        return ist.zuordnungen();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
