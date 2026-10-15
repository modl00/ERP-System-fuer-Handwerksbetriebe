package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.DarstellungService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * Aussehen des Programms. {@code GET /api/darstellung} dürfen alle Angemeldeten lesen, damit sich die Oberfläche danach färbt;
 * {@code PUT /api/settings/darstellung} liegt unter {@code /api/settings/**} und ist damit nur für Admins.
 */
@RestController
@AllArgsConstructor
public class DarstellungController {

    private final DarstellungService darstellung;

    /** {@code farbe} ist {@code #rrggbb} oder {@code null}, wenn das Standard-Rosé gilt. */
    public record DarstellungDto(String farbe) { }

    public record FarbeDto(@Size(max = 16, message = "Die Farbe ist zu lang.") String farbe) { }

    @GetMapping("/api/darstellung")
    public ResponseEntity<DarstellungDto> lesen() {
        return ResponseEntity.ok(stand());
    }

    @PutMapping("/api/settings/darstellung")
    public ResponseEntity<DarstellungDto> speichern(@Valid @RequestBody FarbeDto dto) {
        try {
            darstellung.setzeFarbe(dto.farbe());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand());
    }

    private DarstellungDto stand() {
        return new DarstellungDto(darstellung.farbe().orElse(null));
    }
}
