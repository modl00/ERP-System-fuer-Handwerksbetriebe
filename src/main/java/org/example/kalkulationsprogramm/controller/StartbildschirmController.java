package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.StartbildschirmService;
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
 * Startbildschirm (#290). {@code /api/startbildschirm/**} gilt für den angemeldeten Benutzer selbst; die Vorgabe der Firma ändert nur der Admin über
 * {@code /api/settings/startbildschirm/vorgabe} (liegt unter {@code /api/settings/**}).
 */
@RestController
@AllArgsConstructor
public class StartbildschirmController {

    private final StartbildschirmService dienst;

    /** {@code vorgabe} und {@code eigene} sind {@code null}, solange nichts gespeichert wurde. */
    public record AufbauDto(List<String> vorgabe, List<String> eigene) { }

    public record AuswahlDto(List<String> eintraege) { }

    @GetMapping("/api/startbildschirm")
    public ResponseEntity<AufbauDto> lesen(Authentication auth) {
        return ResponseEntity.ok(stand(benutzerId(auth)));
    }

    @PutMapping("/api/startbildschirm/eigene")
    public ResponseEntity<AufbauDto> eigeneSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            dienst.setzeEigene(id, dto.eintraege());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zur Vorgabe der Firma. */
    @DeleteMapping("/api/startbildschirm/eigene")
    public ResponseEntity<AufbauDto> eigeneLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        dienst.loescheEigene(id);
        return ResponseEntity.ok(stand(id));
    }

    @PutMapping("/api/settings/startbildschirm/vorgabe")
    public ResponseEntity<AufbauDto> vorgabeSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            dienst.setzeVorgabe(dto.eintraege());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zum mitgelieferten Aufbau. */
    @DeleteMapping("/api/settings/startbildschirm/vorgabe")
    public ResponseEntity<AufbauDto> vorgabeLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        dienst.loescheVorgabe();
        return ResponseEntity.ok(stand(id));
    }

    private AufbauDto stand(long benutzerId) {
        return new AufbauDto(dienst.vorgabe().orElse(null), dienst.eigene(benutzerId).orElse(null));
    }

    private static long benutzerId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal p && p.getId() != null) {
            return p.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
    }
}
