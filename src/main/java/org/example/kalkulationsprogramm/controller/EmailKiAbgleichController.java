package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.EmailKiAbgleichService;
import org.example.kalkulationsprogramm.service.EmailKiAbgleichService.Ergebnis;
import org.example.kalkulationsprogramm.service.EmailKiAbgleichService.Vorschlag;
import org.example.kalkulationsprogramm.service.EmailKiAbgleichService.Zuordnung;
import org.example.kalkulationsprogramm.service.EmailKiAutomatik;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** KI-Abgleich im E-Mail-Center: Vorschläge für ausgewählte Mails, Übernahme nach Bestätigung, Schalter für die Vollautomatik (nur Administratoren). */
@RestController
@RequiredArgsConstructor
public class EmailKiAbgleichController {

    private final EmailKiAbgleichService abgleich;
    private final EmailKiAutomatik automatik;

    public record Auswahl(List<Long> emailIds) { }

    public record Schalter(boolean vollautomatisch) { }

    @PostMapping("/api/emails/ki-abgleich/vorschlaege")
    public List<Vorschlag> vorschlaege(@RequestBody Auswahl auswahl) {
        try {
            return abgleich.vorschlaege(auswahl == null ? null : auswahl.emailIds());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/api/emails/ki-abgleich/uebernehmen")
    public Ergebnis uebernehmen(@RequestBody List<Zuordnung> zuordnungen) {
        try {
            return abgleich.uebernehme(zuordnungen);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Unter {@code /api/settings/**}: nur Administratoren. */
    @GetMapping("/api/settings/email/ki-automatik")
    public EmailKiAutomatik.Status status() {
        return automatik.status();
    }

    @PutMapping("/api/settings/email/ki-automatik")
    public EmailKiAutomatik.Status schalten(@RequestBody Schalter schalter) {
        return automatik.setze(schalter != null && schalter.vollautomatisch());
    }
}
