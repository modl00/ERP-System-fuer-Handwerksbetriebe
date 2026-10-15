package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.KostensatzService;
import org.example.kalkulationsprogramm.service.SatzUebernahmeService;
import org.example.kalkulationsprogramm.service.KostensatzService.Ergebnis;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Kostenrechnung (Konzept #166): Kostensätze und Verrechnungssätze im Überblick. <b>Nur Administratoren</b> (SecurityConfig): Lohnkosten je Person sind personenbezogen.
 * Gehört nicht in die Handy-App und nicht in den KI-Kontext.
 */
@RestController
@RequestMapping("/api/kostenrechnung")
@RequiredArgsConstructor
public class KostenrechnungController {

    private final KostensatzService kostensaetze;
    private final SatzUebernahmeService uebernahme;
    private final org.example.kalkulationsprogramm.service.GemeinkostenSchaetzung gemeinkostenSchaetzung;

    private final org.example.kalkulationsprogramm.service.KostenstelleService kostenstellen;
    private final org.example.kalkulationsprogramm.service.KostenrechnungVorgaben vorgaben;

    public record VorgabenAnfrage(BigDecimal wagnis, BigDecimal gewinn, Integer interneZeit) { }

    /** Die gespeicherten Vorgaben für Wagnis, Gewinn und interne Zeit; leere Felder sind nicht gespeichert. */
    @GetMapping("/vorgaben")
    public org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Vorgaben vorgabenLesen() {
        return vorgaben.lese();
    }

    /** Speichert die Vorgaben (ein leeres Feld löscht sie). Ändert keine Sätze. */
    @org.springframework.web.bind.annotation.PutMapping("/vorgaben")
    public org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Vorgaben vorgabenSpeichern(@org.springframework.web.bind.annotation.RequestBody VorgabenAnfrage a) {
        try {
            return vorgaben.speichere(a == null ? null : a.wagnis(), a == null ? null : a.gewinn(), a == null ? null : a.interneZeit());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Schätzung der internen Zeit aus den gebuchten Stunden eines abgeschlossenen Jahres. Ändert nichts. */
    @GetMapping("/interne-zeit-schaetzung")
    public org.example.kalkulationsprogramm.service.KostenrechnungVorgaben.Schaetzung interneZeitSchaetzen(@RequestParam int jahr) {
        return vorgaben.schaetze(jahr);
    }

    public record SchaetzungAnfrage(BigDecimal jahresbetrag) { }

    public record SollAnfrage(BigDecimal jahresSoll) { }

    /** Setzt den geschätzten Jahresbetrag (Soll) einer Kostenstelle; ein leerer Betrag löscht ihn. Nur Fixkosten-Kostenstellen. */
    @org.springframework.web.bind.annotation.PutMapping("/kostenstellen/{id}/soll")
    public java.util.Map<String, Object> sollSetzen(@org.springframework.web.bind.annotation.PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody SollAnfrage a) {
        try {
            var ks = kostenstellen.setzeSoll(id, a == null ? null : a.jahresSoll());
            java.util.Map<String, Object> antwort = new java.util.HashMap<>();
            antwort.put("id", ks.getId());
            antwort.put("jahresSoll", ks.getJahresSoll());
            return antwort;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Die eingetragene Schätzung der Gemeinkosten (Jahresbetrag); {@code null}, wenn keine eingetragen ist. */
    @GetMapping("/gemeinkosten-schaetzung")
    public java.util.Map<String, Object> schaetzungLesen() {
        java.util.Map<String, Object> antwort = new java.util.HashMap<>();
        antwort.put("jahresbetrag", gemeinkostenSchaetzung.lese());
        return antwort;
    }

    /** Trägt die Schätzung ein; ein leerer Betrag löscht sie. */
    @org.springframework.web.bind.annotation.PutMapping("/gemeinkosten-schaetzung")
    public java.util.Map<String, Object> schaetzungSpeichern(@org.springframework.web.bind.annotation.RequestBody SchaetzungAnfrage a) {
        try {
            gemeinkostenSchaetzung.speichere(a == null ? null : a.jahresbetrag());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return schaetzungLesen();
    }

    @GetMapping("/kostensaetze")
    public Ergebnis kostensaetze(@RequestParam int jahr, @RequestParam(required = false) Integer interneQuote,
            @RequestParam(required = false) BigDecimal wagnis, @RequestParam(required = false) BigDecimal gewinn) {
        try {
            return kostensaetze.berechne(jahr, interneQuote, wagnis, gewinn);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Vorschau der Übernahme: je Arbeitsgang bisheriger und neuer Satz. Ändert nichts. */
    @GetMapping("/uebernahme/vorschau")
    public SatzUebernahmeService.Vorschau uebernahmeVorschau(@RequestParam int jahr, @RequestParam(required = false) Integer interneQuote,
            @RequestParam(required = false) BigDecimal wagnis, @RequestParam(required = false) BigDecimal gewinn) {
        try {
            return uebernahme.vorschau(jahr, interneQuote, wagnis, gewinn);
        } catch (IllegalArgumentException | SatzUebernahmeService.UebernahmeFehler e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public record UebernahmeAnfrage(int jahr, Integer interneQuote, BigDecimal wagnis, BigDecimal gewinn, BigDecimal erwarteterSatz, java.util.List<Long> arbeitsgangIds) { }

    /** Schreibt den Satz für die gewählten Arbeitsgänge; nur wenn die Zahlen noch zur Vorschau passen. */
    @org.springframework.web.bind.annotation.PostMapping("/uebernahme")
    public java.util.Map<String, Object> uebernehmen(@org.springframework.web.bind.annotation.RequestBody UebernahmeAnfrage a) {
        try {
            int n = uebernahme.uebernehme(a.jahr(), a.interneQuote(), a.wagnis(), a.gewinn(), a.erwarteterSatz(), a.arbeitsgangIds());
            return java.util.Map.of("geschrieben", n);
        } catch (SatzUebernahmeService.UebernahmeFehler e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
