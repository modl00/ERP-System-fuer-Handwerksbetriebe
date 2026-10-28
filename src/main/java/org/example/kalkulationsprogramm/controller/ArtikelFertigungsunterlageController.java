package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Bestellwesen.FertigungsunterlageDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.service.ArtikelFertigungsunterlageService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fertigungsunterlagen (DXF, STEP, DWG, STP, DXB, PDF) am Artikelstamm (Issue #510).
 * Upload/Liste/Download/Loeschen unter /api/artikel/{artikelId}/fertigungsunterlagen.
 */
@Slf4j
@RestController
@RequestMapping("/api/artikel")
@RequiredArgsConstructor
public class ArtikelFertigungsunterlageController {

    private final ArtikelFertigungsunterlageService service;

    @GetMapping("/{artikelId}/fertigungsunterlagen")
    public ResponseEntity<List<FertigungsunterlageDto>> liste(@PathVariable Long artikelId) {
        return ResponseEntity.ok(service.liste(artikelId));
    }

    @PostMapping(value = "/{artikelId}/fertigungsunterlagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> hochladen(@PathVariable Long artikelId,
            @org.springframework.web.bind.annotation.RequestPart("datei") MultipartFile datei) {
        try {
            FertigungsunterlageDto ergebnis = service.ladeHoch(artikelId, datei);
            return ResponseEntity.status(HttpStatus.CREATED).body(ergebnis);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("Fertigungsunterlage-Upload fuer Artikel {} fehlgeschlagen", artikelId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Die Datei konnte nicht gespeichert werden."));
        }
    }

    @GetMapping("/fertigungsunterlagen/{id}/datei")
    public ResponseEntity<org.springframework.core.io.Resource> ladeDatei(@PathVariable Long id) {
        ArtikelFertigungsunterlageService.DateiErgebnis datei = service.ladeDatei(id);
        ContentDisposition disposition = (datei.istPdf()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(datei.originalDateiname(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(datei.contentType()))
                .header("Content-Disposition", disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(datei.resource());
    }

    @DeleteMapping("/fertigungsunterlagen/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }
}
