package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerSpeichernDto;
import org.example.kalkulationsprogramm.service.KundeAnsprechpartnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ansprechpartner eines Kunden. */
@RestController
@RequestMapping("/api/kunden/{kundeId}/ansprechpartner")
@RequiredArgsConstructor
public class KundeAnsprechpartnerController {

    private final KundeAnsprechpartnerService service;

    @GetMapping
    public ResponseEntity<List<KundeAnsprechpartnerDto>> liste(@PathVariable Long kundeId) {
        return ResponseEntity.ok(service.liste(kundeId));
    }

    @PostMapping
    public ResponseEntity<KundeAnsprechpartnerDto> anlegen(
            @PathVariable Long kundeId, @Valid @RequestBody KundeAnsprechpartnerSpeichernDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.anlegen(kundeId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KundeAnsprechpartnerDto> aendern(
            @PathVariable Long kundeId, @PathVariable Long id, @Valid @RequestBody KundeAnsprechpartnerSpeichernDto dto) {
        return ResponseEntity.ok(service.aendern(kundeId, id, dto));
    }

    @PutMapping("/{id}/haupt")
    public ResponseEntity<KundeAnsprechpartnerDto> hauptSetzen(@PathVariable Long kundeId, @PathVariable Long id) {
        return ResponseEntity.ok(service.hauptSetzen(kundeId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long kundeId, @PathVariable Long id) {
        service.loeschen(kundeId, id);
        return ResponseEntity.noContent().build();
    }
}
