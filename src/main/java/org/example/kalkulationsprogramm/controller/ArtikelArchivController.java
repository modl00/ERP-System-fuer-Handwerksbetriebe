package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ArtikelArchivService;
import org.example.kalkulationsprogramm.service.ArtikelArchivService.Geloescht;
import org.example.kalkulationsprogramm.service.ArtikelArchivService.Verwendungen;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Artikel-Archiv: archivieren und wiederherstellen darf jeder Angemeldete, endgültig löschen nur ein Administrator (siehe SecurityConfig). */
@RestController
@RequestMapping("/api/artikel")
@RequiredArgsConstructor
public class ArtikelArchivController {

    private final ArtikelArchivService service;

    public record Archiviert(int anzahl) { }

    @PostMapping("/{id}/archivieren")
    public Archiviert archivieren(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean mitVarianten, Authentication auth) {
        return new Archiviert(service.archiviere(id, mitVarianten, auth == null ? null : auth.getName()));
    }

    @PostMapping("/{id}/wiederherstellen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void wiederherstellen(@PathVariable Long id) {
        service.stelleWiederHer(id);
    }

    @GetMapping("/{id}/verwendungen")
    public Verwendungen verwendungen(@PathVariable Long id) {
        return service.verwendungen(id);
    }

    @DeleteMapping("/{id}")
    public Geloescht loeschen(@PathVariable Long id) {
        return service.loesche(id);
    }
}
