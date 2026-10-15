package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.SchnellzugriffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;

/**
 * Schnellzugriffsleiste (#290). {@code /api/schnellzugriff/**} gilt für den angemeldeten Benutzer selbst; die Vorgabe der Firma ändert nur der Admin über
 * {@code /api/settings/schnellzugriff/vorgabe} (liegt unter {@code /api/settings/**}).
 */
@RestController
@AllArgsConstructor
public class SchnellzugriffController {

    private final SchnellzugriffService dienst;

    /** {@code vorgabe} und {@code eigene} sind {@code null}, solange nichts gespeichert wurde. */
    public record LeisteDto(List<String> vorgabe, List<String> eigene) { }

    public record AuswahlDto(List<String> eintraege) { }

    @GetMapping("/api/schnellzugriff")
    public ResponseEntity<LeisteDto> lesen(Authentication auth) {
        return ResponseEntity.ok(stand(benutzerId(auth)));
    }

    @PutMapping("/api/schnellzugriff/eigene")
    public ResponseEntity<LeisteDto> eigeneSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            dienst.setzeEigene(id, dto.eintraege());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zur Vorgabe der Firma. */
    @DeleteMapping("/api/schnellzugriff/eigene")
    public ResponseEntity<LeisteDto> eigeneLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        dienst.loescheEigene(id);
        return ResponseEntity.ok(stand(id));
    }

    @PutMapping("/api/settings/schnellzugriff/vorgabe")
    public ResponseEntity<LeisteDto> vorgabeSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            dienst.setzeVorgabe(dto.eintraege());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zu „keine Vorgabe“ (leere Leiste). */
    @DeleteMapping("/api/settings/schnellzugriff/vorgabe")
    public ResponseEntity<LeisteDto> vorgabeLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        dienst.loescheVorgabe();
        return ResponseEntity.ok(stand(id));
    }

    private LeisteDto stand(long benutzerId) {
        return new LeisteDto(dienst.vorgabe().orElse(null), dienst.eigene(benutzerId).orElse(null));
    }

    private static long benutzerId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal p && p.getId() != null) {
            return p.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
    }
}
