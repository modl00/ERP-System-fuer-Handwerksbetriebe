package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.BeitraegeWebsiteClient;
import org.example.kalkulationsprogramm.service.BeitraegeWebsiteException;
import org.example.kalkulationsprogramm.service.WebsiteZugangService;
import org.example.kalkulationsprogramm.service.WebsiteZugangService.Konfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Zugang zur Website für „Neuigkeiten“: einstellen und testen. Liegt unter {@code /api/settings/**} und ist damit nur für Admins. */
@RestController
@RequestMapping("/api/settings/website-zugang")
@RequiredArgsConstructor
public class WebsiteZugangController {

    private final WebsiteZugangService zugang;
    private final BeitraegeWebsiteClient client;

    /** {@code token}: weglassen (null) = unverändert, "" = entfernen. */
    public record SpeichernDto(String adresse, String token) { }

    /** Ergebnis des Tests. {@code art}: OK, NICHT_EINGERICHTET, NICHT_ERREICHBAR, KEIN_ZUGRIFF, FEHLER. */
    public record Testergebnis(boolean ok, String art, String meldung) { }

    @GetMapping
    public ResponseEntity<Konfiguration> konfiguration() {
        return ResponseEntity.ok(zugang.konfiguration());
    }

    @PutMapping
    public ResponseEntity<Konfiguration> speichern(@RequestBody SpeichernDto eingabe) {
        return ResponseEntity.ok(zugang.speichern(eingabe.adresse(), eingabe.token()));
    }

    /** Ruft die Beitragsliste der Website ab und erklärt das Ergebnis in Klartext. */
    @PostMapping("/test")
    public ResponseEntity<Testergebnis> test() {
        if (!zugang.konfiguration().eingerichtet()) {
            return ResponseEntity.ok(new Testergebnis(false, "NICHT_EINGERICHTET", "Bitte Adresse und Schlüssel eintragen und speichern."));
        }
        try {
            int anzahl = client.listeAlle().size();
            return ResponseEntity.ok(new Testergebnis(true, "OK", "Die Website antwortet. Es gibt " + anzahl + (anzahl == 1 ? " Beitrag." : " Beiträge.")));
        } catch (BeitraegeWebsiteException e) {
            Integer status = e.getStatusCode();
            if (status == null) {
                String m = e.getMessage() == null ? "" : e.getMessage();
                boolean konfiguration = m.startsWith("Keine Website-Adresse") || m.startsWith("Kein API-Token") || m.startsWith("Website-URL muss") || m.startsWith("Ungültige Website-URL");
                return ResponseEntity.ok(new Testergebnis(false, konfiguration ? "NICHT_EINGERICHTET" : "NICHT_ERREICHBAR",
                        konfiguration ? m : "Die Website ist nicht erreichbar. Stimmt die Adresse, läuft die Website und ist sie vom Server aus erreichbar (zum Beispiel über das VPN)?"));
            }
            return ResponseEntity.ok(switch (status) {
                case 401, 403 -> new Testergebnis(false, "KEIN_ZUGRIFF", "Die Website lehnt den Schlüssel ab (Fehler " + status + "). Bitte den Schlüssel prüfen.");
                case 404 -> new Testergebnis(false, "FEHLER", "Unter dieser Adresse gibt es die Beiträge-Schnittstelle nicht (Fehler 404). Bitte die Adresse prüfen.");
                default -> new Testergebnis(false, "FEHLER", "Die Website meldet einen Fehler (" + status + ").");
            });
        }
    }
}
