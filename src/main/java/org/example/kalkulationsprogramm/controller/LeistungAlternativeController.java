package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.service.LeistungAlternativeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Alternative Kalkulation an einer Position im Angebot: temporäre Leistung aus einer Katalogleistung oder einem Artikel (nicht im Katalog). */
@RestController
@RequestMapping("/api/leistungen")
@RequiredArgsConstructor
public class LeistungAlternativeController {

    private final LeistungAlternativeService service;

    public record AusArtikel(Long artikelId, String name, Verrechnungseinheit unit, BigDecimal price) { }

    /** Kopiert die Leistung samt Kalkulation als temporäre Leistung „… (Alternative)“. */
    @PostMapping("/{id}/alternative")
    public LeistungDto alternative(@PathVariable Long id) {
        return service.alternativeZuLeistung(id);
    }

    /** Legt eine temporäre Leistung mit dem Artikel als Material an. */
    @PostMapping("/temporaer/aus-artikel")
    public LeistungDto ausArtikel(@RequestBody AusArtikel a) {
        if (a == null || a.artikelId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bitte einen Artikel angeben.");
        }
        return service.alternativeZuArtikel(a.artikelId(), a.name(), a.unit(), a.price());
    }
}
