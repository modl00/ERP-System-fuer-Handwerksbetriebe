package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.planung.PlanKonfliktException;
import org.example.kalkulationsprogramm.service.planung.PlanungsHistorie;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.TypDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#184): Aufträge mit Vorgängen, Abhängigkeiten und Terminen. Jede Änderung liefert den ganzen Auftrag mit den neu gerechneten Terminen zurück,
 * damit sich die Ansicht nur aktualisieren muss. Eingabefehler kommen als 400 mit Klartext.
 */
@RestController
@RequestMapping("/api/plantafel")
@RequiredArgsConstructor
public class PlantafelController {

    private final PlanungsService service;
    private final PlanungsHistorie historie;

    public record Termine(LocalDate start, LocalDate ziel) { }

    public record Puffer(Integer versatzTage) { }

    /** Abteilung und Stunden pro Arbeitstag ersetzen immer den bisherigen Wert (leer = kein Vorschlag aus dem Angebot). */
    public record TypEingabe(String name, String farbe, Integer sortierung, Integer standardDauerTage, Boolean aktiv, Long abteilungId, java.math.BigDecimal stundenProTag, Boolean wartet, Boolean buero) { }

    /** {@code datum} leer = kein frühester Beginn. */
    public record Fruehestens(LocalDate datum, Integer stand) { }

    /** Lieferant, Bestelldatum und bestätigter Liefertermin eines Wartevorgangs; alles leer = aufheben. */
    public record Lieferung(Long lieferantId, LocalDate bestelltAm, LocalDate lieferterminLieferant, Integer stand) { }

    /** {@code rang} leer = die Reihenfolge ist nicht entschieden. */
    public record Rang(Integer rang, Integer stand) { }

    public record Auswahl(List<Long> vorgangIds) { }

    @GetMapping("/auftraege")
    public List<AuftragDto> auftraege() {
        return service.auftraege();
    }

    @GetMapping("/auftraege/{projektId}")
    public AuftragDto auftrag(@PathVariable Long projektId) {
        return service.auftrag(projektId);
    }

    @PutMapping("/auftraege/{projektId}/termine")
    public AuftragDto termine(@PathVariable Long projektId, @RequestBody Termine t) {
        return service.setzeTermine(projektId, t == null ? null : t.start(), t == null ? null : t.ziel());
    }

    /** Vorschlag für die Dauern aus der Arbeitszeit des Auftrags. Ändert nichts. */
    @GetMapping("/auftraege/{projektId}/vorschlag")
    public PlanungsService.VorschlagAntwort vorschlag(@PathVariable Long projektId) {
        return service.vorschlag(projektId);
    }

    @PostMapping("/auftraege/{projektId}/vorschlag/uebernehmen")
    public AuftragDto vorschlagUebernehmen(@PathVariable Long projektId, @RequestBody Auswahl a) {
        return service.vorschlagUebernehmen(projektId, a == null ? null : a.vorgangIds());
    }

    /** Nimmt den Auftrag mit einer Kette aus allen aktiven Vorgangsarten in die Plantafel auf. */
    @PostMapping("/auftraege/{projektId}/standardkette")
    public AuftragDto standardkette(@PathVariable Long projektId) {
        return service.standardkette(projektId);
    }

    @PostMapping("/auftraege/{projektId}/vorgaenge")
    public AuftragDto vorgangAnlegen(@PathVariable Long projektId, @RequestBody VorgangEingabe e) {
        return service.vorgangAnlegen(projektId, e);
    }

    @PutMapping("/vorgaenge/{id}")
    public AuftragDto vorgangAendern(@PathVariable Long id, @RequestBody VorgangEingabe e) {
        return service.vorgangAendern(id, e);
    }

    public record Prioritaet(String prioritaet) { }

    /** Nimmt den ganzen Auftrag aus der Plantafel (alle Vorgänge); der Auftrag selbst bleibt bestehen. */
    @DeleteMapping("/auftraege/{projektId}")
    public Map<String, Boolean> auftragEntfernen(@PathVariable Long projektId) {
        service.auftragEntfernen(projektId);
        return Map.of("ok", true);
    }

    /** Wichtigkeit für alle noch nicht erledigten Vorgänge des Auftrags. */
    @PutMapping("/auftraege/{projektId}/prioritaet")
    public AuftragDto auftragPrioritaet(@PathVariable Long projektId, @RequestBody Prioritaet p) {
        return service.auftragPrioritaet(projektId, p == null ? null : p.prioritaet());
    }

    /** Das Ziehen mit der Maus: der Vorgang beginnt nicht früher, darf sich aber mit der Kette nach hinten bewegen. Kein Fixtermin. */
    @PutMapping("/vorgaenge/{id}/fruehestens")
    public AuftragDto fruehestens(@PathVariable Long id, @RequestBody Fruehestens f) {
        return service.fruehestensSetzen(id, f == null ? null : f.datum(), f == null ? null : f.stand());
    }

    /** Wartevorgang: Lieferant, „bestellt am“ und der von Hand bestätigte Liefertermin (Auftragsbestätigung des Lieferanten). */
    @PutMapping("/vorgaenge/{id}/lieferung")
    public AuftragDto lieferung(@PathVariable Long id, @RequestBody Lieferung l) {
        return service.lieferungSetzen(id, l == null ? null : l.lieferantId(), l == null ? null : l.bestelltAm(), l == null ? null : l.lieferterminLieferant(), l == null ? null : l.stand());
    }

    /** Die Reihenfolge, die der Planer unter gleich wichtigen Vorgängen festlegt (kleiner = zuerst). */
    @PutMapping("/vorgaenge/{id}/rang")
    public AuftragDto rang(@PathVariable Long id, @RequestBody Rang r) {
        return service.rangSetzen(id, r == null ? null : r.rang(), r == null ? null : r.stand());
    }

    /** Rechnet die Änderung durch und sagt, was sich verschiebt (Vorschau). Ändert und speichert nichts. */
    @PostMapping("/vorgaenge/{id}/vorschau")
    public PlanungsService.Vorschau vorschau(@PathVariable Long id, @RequestBody PlanungsService.Probe probe) {
        return service.vorschau(id, probe);
    }

    /** Die letzten Änderungen von Hand, jüngste zuerst. */
    @GetMapping("/aenderungen")
    public List<PlanungsHistorie.Eintrag> aenderungen(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        return historie.liste(limit);
    }

    /** Nimmt die jüngste Änderung zurück und liefert den betroffenen Auftrag neu gerechnet. */
    @PostMapping("/aenderungen/{id}/rueckgaengig")
    public AuftragDto rueckgaengig(@PathVariable Long id) {
        return service.rueckgaengig(id);
    }

    /** Rechnet die ganze Plantafel neu (Urlaub, Termine und der neue Tag ändern die Termine, ohne dass jemand sie anfasst). */
    @PostMapping("/neu-berechnen")
    public Map<String, Boolean> neuBerechnen() {
        service.neuBerechnenAlle();
        return Map.of("ok", true);
    }

    @DeleteMapping("/vorgaenge/{id}")
    public AuftragDto vorgangLoeschen(@PathVariable Long id) {
        return service.vorgangLoeschen(id);
    }

    @PutMapping("/vorgaenge/{id}/vorgaenger/{vorgaengerId}")
    public AuftragDto abhaengigkeit(@PathVariable Long id, @PathVariable Long vorgaengerId, @RequestBody(required = false) Puffer p) {
        return service.abhaengigkeitSetzen(id, vorgaengerId, p == null || p.versatzTage() == null ? 0 : p.versatzTage());
    }

    @DeleteMapping("/vorgaenge/{id}/vorgaenger/{vorgaengerId}")
    public AuftragDto abhaengigkeitLoeschen(@PathVariable Long id, @PathVariable Long vorgaengerId) {
        return service.abhaengigkeitLoeschen(id, vorgaengerId);
    }

    @GetMapping("/vorgangstypen")
    public List<TypDto> typen() {
        return service.typen();
    }

    @PostMapping("/vorgangstypen")
    public TypDto typAnlegen(@RequestBody TypEingabe t) {
        return service.typSpeichern(null, t.name(), t.farbe(), t.sortierung(), t.standardDauerTage(), t.aktiv(), t.abteilungId(), t.stundenProTag(), t.wartet(), t.buero());
    }

    @PutMapping("/vorgangstypen/{id}")
    public TypDto typAendern(@PathVariable Long id, @RequestBody TypEingabe t) {
        return service.typSpeichern(id, t.name(), t.farbe(), t.sortierung(), t.standardDauerTage(), t.aktiv(), t.abteilungId(), t.stundenProTag(), t.wartet(), t.buero());
    }

    /** Jemand anderes hat den Vorgang inzwischen geändert (HTTP 409): Die Ansicht lädt neu. */
    @ExceptionHandler(PlanKonfliktException.class)
    public ResponseEntity<Map<String, String>> konflikt(PlanKonfliktException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> eingabefehler(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Die Eingabe ist nicht gültig." : e.getMessage()));
    }
}
