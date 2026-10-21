package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungDto;
import org.example.kalkulationsprogramm.dto.Lager.ArtikelAenderungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerZusammenfassungDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortEingabe;
import org.example.kalkulationsprogramm.dto.Lager.MindestbestandEingabe;
import org.example.kalkulationsprogramm.service.LagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Lagerverwaltung: Lagerorte, Bestand, Buchungen und Mindestbestand. */
@RestController
@RequestMapping("/api/lager")
@RequiredArgsConstructor
public class LagerController {

    private final LagerService lagerService;

    @GetMapping("/orte")
    public List<LagerortDto> orte() {
        return lagerService.orte();
    }

    @PostMapping("/orte")
    public ResponseEntity<LagerortDto> ortAnlegen(@RequestBody LagerortEingabe eingabe) {
        return ResponseEntity.ok(lagerService.ortAnlegen(eingabe));
    }

    @PutMapping("/orte/{id}")
    public ResponseEntity<LagerortDto> ortAendern(@PathVariable Long id, @RequestBody LagerortEingabe eingabe) {
        return ResponseEntity.ok(lagerService.ortAendern(id, eingabe));
    }

    /** Aktiviert oder deaktiviert einen Lagerort, Body: {@code {"aktiv": true}}. */
    @PutMapping("/orte/{id}/aktiv")
    public ResponseEntity<LagerortDto> ortAktiv(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean aktiv = body == null ? null : body.get("aktiv");
        if (aktiv == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bitte angeben, ob der Lagerort aktiv sein soll.");
        }
        return ResponseEntity.ok(lagerService.ortAktiv(id, aktiv));
    }

    @GetMapping("/bestand")
    public List<LagerBestandDto> bestand(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean nurHandlungsbedarf) {
        return lagerService.bestand(q, nurHandlungsbedarf);
    }

    @GetMapping("/zusammenfassung")
    public LagerZusammenfassungDto zusammenfassung() {
        return lagerService.zusammenfassung();
    }

    @GetMapping("/artikel/{artikelId}/bestand")
    public LagerBestandDto bestandVon(@PathVariable Long artikelId) {
        return lagerService.bestandVon(artikelId);
    }

    @GetMapping("/artikel/{artikelId}/buchungen")
    public List<LagerBuchungDto> verlauf(@PathVariable Long artikelId) {
        return lagerService.verlauf(artikelId);
    }

    @PostMapping("/buchungen")
    public ResponseEntity<LagerBestandDto> buche(@RequestBody LagerBuchungEingabe eingabe) {
        return ResponseEntity.ok(lagerService.buche(eingabe));
    }

    @PostMapping("/umbuchungen")
    public ResponseEntity<LagerBestandDto> umbuche(@RequestBody UmbuchungEingabe eingabe) {
        return ResponseEntity.ok(lagerService.umbuche(eingabe));
    }

    @PutMapping("/artikel/{artikelId}")
    public ResponseEntity<LagerBestandDto> artikelAendern(@PathVariable Long artikelId, @RequestBody ArtikelAenderungEingabe eingabe) {
        return ResponseEntity.ok(lagerService.artikelAendern(artikelId, eingabe));
    }

    @PutMapping("/artikel/{artikelId}/mindestbestand")
    public ResponseEntity<LagerBestandDto> mindestbestand(@PathVariable Long artikelId, @RequestBody MindestbestandEingabe eingabe) {
        return ResponseEntity.ok(lagerService.setzeMindestbestand(artikelId, eingabe == null ? null : eingabe.mindestbestand()));
    }

    @PostMapping("/orte/{id}/archivieren")
    public ResponseEntity<LagerortDto> ortArchivieren(@PathVariable Long id) {
        return ResponseEntity.ok(lagerService.ortArchivieren(id));
    }

    @PostMapping("/orte/{id}/wiederherstellen")
    public ResponseEntity<LagerortDto> ortWiederherstellen(@PathVariable Long id) {
        return ResponseEntity.ok(lagerService.ortWiederherstellen(id));
    }

    @DeleteMapping("/orte/{id}")
    public ResponseEntity<Void> ortLoeschen(@PathVariable Long id) {
        lagerService.ortLoeschen(id);
        return ResponseEntity.noContent().build();
    }
}
