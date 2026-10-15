package org.example.kalkulationsprogramm.controller.wartung;

import org.example.kalkulationsprogramm.dto.wartung.WartungVertragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragSpeichernDto;
import org.example.kalkulationsprogramm.service.wartung.WartungVertragService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

/** Der Wartungsvertrag eines Projekts (Laufzeit, Intervall, Preis). */
@RestController
@RequestMapping("/api/wartung/projekte/{projektId}/vertrag")
@AllArgsConstructor
public class WartungVertragController {

    private final WartungVertragService vertragService;

    /** Der Vertrag des Projekts; 204, wenn es noch keinen gibt. */
    @GetMapping
    public ResponseEntity<WartungVertragDto> vertrag(@PathVariable Long projektId) {
        return vertragService.finde(projektId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Legt den Vertrag an oder ersetzt seine Angaben. */
    @PutMapping
    public ResponseEntity<WartungVertragDto> speichern(@PathVariable Long projektId, @RequestBody WartungVertragSpeichernDto eingabe) {
        return ResponseEntity.ok(vertragService.speichern(projektId, eingabe));
    }

    @DeleteMapping
    public ResponseEntity<Void> loeschen(@PathVariable Long projektId) {
        vertragService.loeschen(projektId);
        return ResponseEntity.noContent().build();
    }
}
