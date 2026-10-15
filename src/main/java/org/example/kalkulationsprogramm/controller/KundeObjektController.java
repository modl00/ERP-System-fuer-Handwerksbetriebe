package org.example.kalkulationsprogramm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektSpeichernDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungAktivDto;
import org.example.kalkulationsprogramm.service.KundeObjektService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Objekte (Liegenschaften) eines Kunden. Läuft auch bei ausgeschaltetem Wartungsmodul. */
@RestController
@RequestMapping("/api/kunden/{kundeId}/objekte")
@RequiredArgsConstructor
public class KundeObjektController {

    private final KundeObjektService service;

    @GetMapping
    public ResponseEntity<List<KundeObjektDto>> liste(
            @PathVariable Long kundeId, @RequestParam(name = "inaktive", defaultValue = "false") boolean inaktive) {
        return ResponseEntity.ok(service.liste(kundeId, inaktive));
    }

    @PostMapping
    public ResponseEntity<KundeObjektDto> anlegen(
            @PathVariable Long kundeId, @Valid @RequestBody KundeObjektSpeichernDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.anlegen(kundeId, dto));
    }

    @PutMapping("/{objektId}")
    public ResponseEntity<KundeObjektDto> aendern(
            @PathVariable Long kundeId, @PathVariable Long objektId, @Valid @RequestBody KundeObjektSpeichernDto dto) {
        return ResponseEntity.ok(service.aendern(kundeId, objektId, dto));
    }

    @PutMapping("/{objektId}/aktiv")
    public ResponseEntity<KundeObjektDto> aktivSetzen(
            @PathVariable Long kundeId, @PathVariable Long objektId, @Valid @RequestBody WartungAktivDto dto) {
        return ResponseEntity.ok(service.aktivSetzen(kundeId, objektId, Boolean.TRUE.equals(dto.aktiv())));
    }
}
