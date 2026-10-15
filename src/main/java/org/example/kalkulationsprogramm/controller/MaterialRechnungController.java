package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.ZeileEingabe;
import org.example.kalkulationsprogramm.service.MaterialRechnungService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Material "kommt per Rechnung" auflösen (Issue #99). Die Schwelle ändern nur Administratoren ({@code /api/admin/**} ist in der Sicherheitskonfiguration
 * darauf beschränkt).
 */
@RestController
@RequiredArgsConstructor
public class MaterialRechnungController {

    private final MaterialRechnungService service;

    public record ZuordnungDto(List<ZeileEingabe> zeilen) { }

    public record SchwelleDto(Integer prozent) { }

    @GetMapping("/api/projekte/{projektId}/material-abrechnung")
    public MaterialAbrechnungDto uebersicht(@PathVariable Long projektId) {
        return service.uebersicht(projektId);
    }

    @PostMapping("/api/projekte/{projektId}/material-abrechnung/{dokumentId}")
    public MaterialAbrechnungDto zuordnen(@PathVariable Long projektId, @PathVariable Long dokumentId, @RequestBody ZuordnungDto body) {
        return service.speichern(projektId, dokumentId, body == null ? null : body.zeilen());
    }

    @PutMapping("/api/admin/material-abrechnung/schwelle")
    public Map<String, Integer> schwelle(@RequestBody SchwelleDto body) {
        return Map.of("prozent", service.setzeSchwelle(body == null || body.prozent() == null ? -1 : body.prozent()));
    }
}
