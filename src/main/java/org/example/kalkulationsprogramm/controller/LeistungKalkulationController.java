package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto;
import org.example.kalkulationsprogramm.service.LeistungKalkulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Kalkulation einer Leistung aus Material und Arbeitszeit. */
@RestController
@RequestMapping("/api/leistungen/{id}/kalkulation")
@RequiredArgsConstructor
public class LeistungKalkulationController {

    private final LeistungKalkulationService kalkulationService;

    @GetMapping
    public ResponseEntity<LeistungKalkulationDto> laden(@PathVariable Long id) {
        return ResponseEntity.ok(kalkulationService.laden(id));
    }

    @PutMapping
    public ResponseEntity<LeistungKalkulationDto> speichern(@PathVariable Long id, @RequestBody LeistungKalkulationSpeichernDto eingabe) {
        return ResponseEntity.ok(kalkulationService.speichern(id, eingabe));
    }

    /** Rechnet die Zeilen durch, ohne zu speichern (Anzeige beim Bearbeiten). */
    @PostMapping("/vorschau")
    public ResponseEntity<LeistungKalkulationDto> vorschau(@PathVariable Long id, @RequestBody LeistungKalkulationSpeichernDto eingabe) {
        return ResponseEntity.ok(kalkulationService.vorschau(id, eingabe));
    }

    /** Mengenstaffel: Preis je Einheit bei mehreren Angebotsmengen, Rüstzeit auf die Menge verteilt. Ändert nichts. */
    public record StaffelEingabe(java.util.List<java.math.BigDecimal> mengen) { }

    @PostMapping("/staffel")
    public ResponseEntity<java.util.List<LeistungKalkulationService.StaffelZeile>> staffel(@PathVariable Long id, @RequestBody StaffelEingabe eingabe) {
        return ResponseEntity.ok(kalkulationService.staffel(id, eingabe == null ? null : eingabe.mengen()));
    }

    /** Die Aufstellung für eine Angebotsmenge (intern im Angebot sichtbar, nie im Kundendokument). Ändert nichts. */
    @GetMapping("/angebot")
    public ResponseEntity<LeistungKalkulationDto> fuerAngebot(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestParam java.math.BigDecimal menge) {
        return ResponseEntity.ok(kalkulationService.fuerAngebotsmenge(id, menge));
    }

    /** Setzt den gespeicherten Preis auf das aktuelle Ergebnis der Kalkulation. */
    @PostMapping("/preis-uebernehmen")
    public ResponseEntity<LeistungKalkulationDto> preisUebernehmen(@PathVariable Long id) {
        return ResponseEntity.ok(kalkulationService.preisUebernehmen(id));
    }
}
