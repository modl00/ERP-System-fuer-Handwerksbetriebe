package org.example.kalkulationsprogramm.controller.wartung;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.wartung.WartungAktivDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungObjektDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungObjektSpeichernDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungTuerDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungTuerSpeichernDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungVerschiebenDto;
import org.example.kalkulationsprogramm.service.wartung.WartungObjektService;
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

/** Objekte (Gebäude/Standorte) eines Kunden und ihre Türen/Tore. */
@RestController
@RequestMapping("/api/wartung")
@AllArgsConstructor
public class WartungObjektController {

    private final WartungObjektService objektService;

    @GetMapping("/kunden/{kundeId}/objekte")
    public ResponseEntity<List<WartungObjektDto>> objekteDesKunden(
            @PathVariable Long kundeId,
            @RequestParam(name = "inaktive", defaultValue = "false") boolean inaktive) {
        return ResponseEntity.ok(objektService.fuerKunde(kundeId, inaktive));
    }

    @PostMapping("/kunden/{kundeId}/objekte")
    public ResponseEntity<WartungObjektDto> objektAnlegen(
            @PathVariable Long kundeId, @Valid @RequestBody WartungObjektSpeichernDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(objektService.objektAnlegen(kundeId, dto));
    }

    @PutMapping("/objekte/{objektId}")
    public ResponseEntity<WartungObjektDto> objektAendern(
            @PathVariable Long objektId, @Valid @RequestBody WartungObjektSpeichernDto dto) {
        return ResponseEntity.ok(objektService.objektAendern(objektId, dto));
    }

    @PutMapping("/objekte/{objektId}/aktiv")
    public ResponseEntity<WartungObjektDto> objektAktivSetzen(
            @PathVariable Long objektId, @Valid @RequestBody WartungAktivDto dto) {
        return ResponseEntity.ok(objektService.objektAktivSetzen(objektId, Boolean.TRUE.equals(dto.aktiv())));
    }

    @PostMapping("/objekte/{objektId}/tueren")
    public ResponseEntity<WartungTuerDto> tuerAnlegen(
            @PathVariable Long objektId, @Valid @RequestBody WartungTuerSpeichernDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(objektService.tuerAnlegen(objektId, dto));
    }

    @PutMapping("/tueren/{tuerId}")
    public ResponseEntity<WartungTuerDto> tuerAendern(
            @PathVariable Long tuerId, @Valid @RequestBody WartungTuerSpeichernDto dto) {
        return ResponseEntity.ok(objektService.tuerAendern(tuerId, dto));
    }

    @PutMapping("/tueren/{tuerId}/aktiv")
    public ResponseEntity<WartungTuerDto> tuerAktivSetzen(
            @PathVariable Long tuerId, @Valid @RequestBody WartungAktivDto dto) {
        return ResponseEntity.ok(objektService.tuerAktivSetzen(tuerId, Boolean.TRUE.equals(dto.aktiv())));
    }

    @PostMapping("/tueren/{tuerId}/verschieben")
    public ResponseEntity<WartungTuerDto> tuerVerschieben(
            @PathVariable Long tuerId, @Valid @RequestBody WartungVerschiebenDto dto) {
        return ResponseEntity.ok(objektService.tuerVerschieben(tuerId, dto.zielObjektId()));
    }

    @PostMapping("/tueren/{tuerId}/kopieren")
    public ResponseEntity<WartungTuerDto> tuerKopieren(@PathVariable Long tuerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(objektService.tuerKopieren(tuerId));
    }
}
