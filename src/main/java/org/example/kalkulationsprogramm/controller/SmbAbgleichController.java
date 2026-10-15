package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.service.ablage.SmbAbgleichEinstellungen;
import org.example.kalkulationsprogramm.service.ablage.SmbAbgleichEinstellungenService;
import org.example.kalkulationsprogramm.service.ablage.SmbAbgleichService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Abgleich der Dokumente mit einer Netzwerkfreigabe (SMB). Nur für Administratoren (/api/admin/**). Das Passwort wird nie zurückgegeben. */
@RestController
@RequestMapping("/api/admin/ablage/smb")
@RequiredArgsConstructor
public class SmbAbgleichController {

    public record Antwort(boolean aktiv, String server, String freigabe, String unterordner, String benutzer, String domaene, boolean passwortGesetzt,
            boolean verschluesselt, int intervallMinuten, String letzterLauf, String letztesErgebnis) {
    }

    public record Anfrage(boolean aktiv, String server, String freigabe, String unterordner, String benutzer, String passwort, boolean passwortLoeschen,
            String domaene, Integer intervallMinuten) {
        SmbAbgleichEinstellungen alsEinstellungen() {
            return new SmbAbgleichEinstellungen(aktiv, server, freigabe, unterordner, benutzer, passwort, domaene);
        }
    }

    private final SmbAbgleichEinstellungenService einstellungen;
    private final SmbAbgleichService abgleich;

    @GetMapping
    public Antwort stand() {
        SmbAbgleichEinstellungen e = einstellungen.lade();
        return new Antwort(e.aktiv(), e.server(), e.freigabe(), e.unterordner(), e.benutzer(), e.domaene(), einstellungen.passwortGesetzt(), einstellungen.verschluesselt(),
                einstellungen.intervallMinuten(), einstellungen.letzterLauf() == null ? "" : einstellungen.letzterLauf().toString(), einstellungen.letztesErgebnis());
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> speichere(@RequestBody Anfrage a) {
        try {
            einstellungen.speichere(a.alsEinstellungen(), a.passwortLoeschen());
            if (a.intervallMinuten() != null) {
                einstellungen.speichereIntervall(a.intervallMinuten());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("message", "Gespeichert."));
    }

    private SmbAbgleichEinstellungen mitGespeichertemPasswort(Anfrage a) {
        SmbAbgleichEinstellungen e = a.alsEinstellungen();
        if (a.passwortLoeschen()) {
            return e.mitPasswort("");
        }
        return e.passwort() == null || e.passwort().isEmpty() ? e.mitPasswort(einstellungen.lade().passwort()) : e;
    }

    /** Testet die eingetragenen Angaben, ohne sie zu speichern. Bleibt das Passwortfeld leer, gilt das gespeicherte. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> teste(@RequestBody Anfrage a) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "message", abgleich.teste(mitGespeichertemPasswort(a))));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /** Meldet sich mit Gerät, Benutzer und Passwort an und liefert die Freigaben zur Auswahl. Bleibt das Passwortfeld leer, gilt das gespeicherte. */
    @PostMapping("/freigaben")
    public ResponseEntity<Map<String, Object>> freigaben(@RequestBody Anfrage a) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "freigaben", abgleich.freigaben(mitGespeichertemPasswort(a))));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", ex.getMessage(), "freigaben", java.util.List.of()));
        }
    }

    /** Die Unterordner eines Ordners ({@code pfad} leer = oberste Ebene) in einer Freigabe, zum Auswählen. */
    @PostMapping("/ordner")
    public ResponseEntity<Map<String, Object>> ordner(@RequestBody Anfrage a, @RequestParam String freigabe, @RequestParam(defaultValue = "") String pfad) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "ordner", abgleich.ordner(mitGespeichertemPasswort(a), freigabe, pfad)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.ok(Map.of("success", false, "message", ex.getMessage(), "ordner", java.util.List.of()));
        }
    }

    /** Gleicht in beide Richtungen ab, mit {@code probelauf=true} nur als Vorschau. */
    @PostMapping("/abgleichen")
    public ResponseEntity<SmbAbgleichService.Ergebnis> abgleichen(@RequestParam(defaultValue = "false") boolean probelauf) {
        return ResponseEntity.ok(abgleich.abgleichen(probelauf));
    }
}
