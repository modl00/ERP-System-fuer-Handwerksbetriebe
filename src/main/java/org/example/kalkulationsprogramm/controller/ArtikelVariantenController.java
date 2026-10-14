package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.GrundartikelEingabe;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Kombination;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Reihen;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.VarianteZeile;
import org.example.kalkulationsprogramm.service.ArtikelVariantenService.Vorgabe;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Grundartikel und ihre Varianten: anlegen, als Raster (Kantenlängen × Wandstärken) vorschauen und in einem Zug anlegen. */
@RestController
@RequestMapping("/api/artikel")
@RequiredArgsConstructor
public class ArtikelVariantenController {

    private final ArtikelVariantenService service;

    public record GrundartikelRequest(String kurzbezeichnung, String produktname, Profilform profilform, Long werkstoffId, Long kategorieId,
            Verrechnungseinheit verrechnungseinheit, BigDecimal verkaufsaufschlagProzent, Integer standardlaenge) { }

    public record GrundartikelAntwort(Long id, String produktname, String kurzbezeichnung) { }

    public record RasterRequest(List<String> a, List<String> b, List<String> ausgeschlossen) { }

    @PostMapping("/grundartikel")
    @ResponseStatus(HttpStatus.CREATED)
    public GrundartikelAntwort legeGrundartikelAn(@RequestBody GrundartikelRequest r) {
        Artikel g = service.legeGrundartikelAn(new GrundartikelEingabe(r.kurzbezeichnung(), r.produktname(), r.profilform(), r.werkstoffId(), r.kategorieId(),
                r.verrechnungseinheit(), r.verkaufsaufschlagProzent(), r.standardlaenge()));
        return new GrundartikelAntwort(g.getId(), g.getProduktname(), g.getKurzbezeichnung());
    }

    /** Welche Formen ein Raster haben und welche Maße es dafür braucht (für die Beschriftung der Eingabe). */
    @GetMapping("/grundartikel/formen")
    public List<Vorgabe> formen() {
        return java.util.Arrays.stream(Profilform.values()).map(service::vorgabe).filter(java.util.Objects::nonNull).toList();
    }

    @GetMapping("/{id}/varianten")
    public List<VarianteZeile> varianten(@PathVariable Long id) {
        return service.varianten(id);
    }

    @GetMapping("/{id}/varianten/raster")
    public Vorgabe raster(@PathVariable Long id) {
        return service.rasterVorgabe(id);
    }

    @PostMapping("/{id}/varianten/vorschau")
    public List<Kombination> vorschau(@PathVariable Long id, @RequestBody RasterRequest r) {
        return service.vorschau(id, new Reihen(r.a(), r.b(), r.ausgeschlossen()));
    }

    @PostMapping("/{id}/varianten")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Kombination> legeVariantenAn(@PathVariable Long id, @RequestBody RasterRequest r) {
        return service.legeVariantenAn(id, new Reihen(r.a(), r.b(), r.ausgeschlossen()));
    }
}
