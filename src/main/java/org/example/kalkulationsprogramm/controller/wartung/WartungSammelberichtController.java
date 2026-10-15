package org.example.kalkulationsprogramm.controller.wartung;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.wartung.WartungSammelberichtAnfrageDto;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService;
import org.example.kalkulationsprogramm.service.wartung.WartungSammelberichtService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Sammelbericht: ein PDF aus mehreren Wartungsprotokollen. */
@RestController
@RequestMapping("/api/wartung/sammelbericht")
@AllArgsConstructor
public class WartungSammelberichtController {

    private final WartungSammelberichtService sammelberichtService;

    /** Ein POST, weil bis zu 200 Protokoll-Nummern nicht sicher in eine Adresse passen. */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@Valid @RequestBody WartungSammelberichtAnfrageDto anfrage) {
        WartungProtokollPdfService.PdfDatei datei = sammelberichtService.erstelle(anfrage);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(datei.dateiname(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(datei.inhalt());
    }
}
