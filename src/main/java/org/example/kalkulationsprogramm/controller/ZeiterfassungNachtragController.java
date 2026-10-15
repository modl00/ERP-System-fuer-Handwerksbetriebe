package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Aenderung;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Eingabe;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Ergebnis;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.HinweisNoetigException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

/**
 * Rückwirkende Zeiterfassung für die Handy-App (Issue #70). Der Zugangslink steht bei Abfragen im Pfad, bei Änderungen im Text der Anfrage; das
 * Funktionsrecht prüft der {@link ZeiterfassungNachtragService} bei jedem Aufruf. Muss der Mitarbeiter etwas bestätigen (Urlaub am selben Tag, sehr
 * langer Tag), antwortet der Server mit 409 und {@code code: BESTAETIGUNG_NOETIG}; die App fragt nach und sendet erneut mit {@code bestaetigt: true}.
 */
@RestController
@RequestMapping("/api/zeiterfassung/nachtrag")
@RequiredArgsConstructor
public class ZeiterfassungNachtragController {

    private final ZeiterfassungNachtragService service;

    public record BuchungDto(String token, String typ, LocalDate datum, LocalTime start, LocalTime ende, Integer dauerMinuten, Long projektId,
            Long arbeitsgangId, Long produktkategorieId, String notiz, Boolean bestaetigt, String idempotencyKey) { }

    public record AenderungDto(String token, LocalDate datum, LocalTime start, LocalTime ende, Integer dauerMinuten, Long projektId, Long arbeitsgangId,
            Long produktkategorieId, String notiz, String grund, Boolean bestaetigt) { }

    @GetMapping("/{token}/tag")
    public Map<String, Object> tag(@PathVariable String token, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
        return service.status(token, datum);
    }

    @PostMapping
    public ResponseEntity<?> erfassen(@RequestBody BuchungDto b) {
        try {
            Ergebnis e = service.erfasse(new Eingabe(b.token(), b.typ(), b.datum(), b.start(), b.ende(), b.dauerMinuten(), b.projektId(), b.arbeitsgangId(),
                    b.produktkategorieId(), b.notiz(), Boolean.TRUE.equals(b.bestaetigt()), b.idempotencyKey()));
            return ResponseEntity.status(HttpStatus.CREATED).body(antwort(e));
        } catch (HinweisNoetigException h) {
            return hinweis(h);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> aendern(@PathVariable Long id, @RequestBody AenderungDto a) {
        try {
            Ergebnis e = service.aendere(id, new Aenderung(a.token(), a.datum(), a.start(), a.ende(), a.dauerMinuten(), a.projektId(), a.arbeitsgangId(),
                    a.produktkategorieId(), a.notiz(), a.grund(), Boolean.TRUE.equals(a.bestaetigt())));
            return ResponseEntity.ok(antwort(e));
        } catch (HinweisNoetigException h) {
            return hinweis(h);
        }
    }

    /** Löschen als Storno mit Grund; der Zugangslink steht im Text, damit er nicht in Adressprotokollen landet. */
    public record StornoDto(String token, String grund) { }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stornieren(@PathVariable Long id, @RequestBody StornoDto s) {
        service.storniere(s.token(), id, s.grund());
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> antwort(Ergebnis e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("buchung", e.buchung());
        r.put("hinweise", e.hinweise());
        return r;
    }

    private static ResponseEntity<Map<String, Object>> hinweis(HinweisNoetigException h) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", "BESTAETIGUNG_NOETIG");
        r.put("message", h.getMessage());
        r.put("hinweise", h.getHinweise());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
    }
}
