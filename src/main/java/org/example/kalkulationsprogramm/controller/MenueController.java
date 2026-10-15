package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.MenueService;
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
 * Ausgeblendete Menüpunkte. {@code /api/menue/**} gilt für den angemeldeten Benutzer selbst; die Vorgabe der Firma ändert nur
 * der Admin über {@code /api/settings/menue/vorgabe} (liegt unter {@code /api/settings/**}).
 */
@RestController
@AllArgsConstructor
public class MenueController {

    private final MenueService menue;

    /** {@code vorgabe} und {@code eigene} sind {@code null}, solange nichts gespeichert wurde. */
    public record MenueDto(List<String> vorgabe, List<String> eigene) { }

    public record AuswahlDto(List<String> ausgeblendet) { }

    @GetMapping("/api/menue")
    public ResponseEntity<MenueDto> lesen(Authentication auth) {
        return ResponseEntity.ok(stand(benutzerId(auth)));
    }

    @PutMapping("/api/menue/eigene")
    public ResponseEntity<MenueDto> eigeneSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            menue.setzeEigene(id, dto.ausgeblendet());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zur Vorgabe der Firma. */
    @DeleteMapping("/api/menue/eigene")
    public ResponseEntity<MenueDto> eigeneLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        menue.loescheEigene(id);
        return ResponseEntity.ok(stand(id));
    }

    @PutMapping("/api/settings/menue/vorgabe")
    public ResponseEntity<MenueDto> vorgabeSpeichern(Authentication auth, @RequestBody AuswahlDto dto) {
        long id = benutzerId(auth);
        try {
            menue.setzeVorgabe(dto.ausgeblendet());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(stand(id));
    }

    /** Zurück zum mitgelieferten Vorschlag. */
    @DeleteMapping("/api/settings/menue/vorgabe")
    public ResponseEntity<MenueDto> vorgabeLoeschen(Authentication auth) {
        long id = benutzerId(auth);
        menue.loescheVorgabe();
        return ResponseEntity.ok(stand(id));
    }

    private MenueDto stand(long benutzerId) {
        return new MenueDto(menue.vorgabe().orElse(null), menue.eigene(benutzerId).orElse(null));
    }

    private static long benutzerId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal p && p.getId() != null) {
            return p.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
    }
}
