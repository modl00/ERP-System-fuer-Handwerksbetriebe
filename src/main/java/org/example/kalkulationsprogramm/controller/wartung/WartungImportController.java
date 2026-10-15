package org.example.kalkulationsprogramm.controller.wartung;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAusfuehrer;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAusfuehrer.Ergebnisbericht;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Übernahme von Wartungsprotokollen aus HandwerkPro. Nur für Administratoren ({@code /api/admin/**}). Die Vorschau schreibt nichts, die Übernahme läuft in einer Transaktion. */
@RestController
@RequestMapping("/api/admin/wartung-import")
@RequiredArgsConstructor
public class WartungImportController {

    private final WartungImportService service;
    private final WartungImportAusfuehrer ausfuehrer;

    @PostMapping("/vorschau")
    public Vorschau vorschau(@RequestBody JsonNode export) {
        return service.vorschau(export);
    }

    @PostMapping("/ausfuehren")
    public Ergebnisbericht ausfuehren(@RequestBody JsonNode export, @RequestParam(defaultValue = "true") boolean neueWerteInKatalog) {
        return ausfuehrer.ausfuehren(export, neueWerteInKatalog);
    }
}
