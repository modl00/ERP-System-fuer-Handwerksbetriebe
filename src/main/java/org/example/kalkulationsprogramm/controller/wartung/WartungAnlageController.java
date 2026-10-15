package org.example.kalkulationsprogramm.controller.wartung;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.wartung.WartungAnlageDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungBerichtDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungGespeicherteUnterschriftDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektZuordnungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftSpeichernDto;
import org.example.kalkulationsprogramm.service.wartung.WartungAnlageService;
import org.example.kalkulationsprogramm.service.wartung.WartungBenutzerService;
import org.example.kalkulationsprogramm.service.wartung.WartungBerichtService;
import org.example.kalkulationsprogramm.service.wartung.WartungMitarbeiterUnterschriftService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Die Wartung als Ganzes (Detailseite mit Historie, Projekt-Zuordnung), der Bereich "Wartungen" im Projekt,
 * der Bericht im Projekt und die gespeicherte Unterschrift des angemeldeten Mitarbeiters.
 */
@RestController
@RequestMapping("/api/wartung")
@AllArgsConstructor
public class WartungAnlageController {

    private final WartungAnlageService anlageService;
    private final WartungBerichtService berichtService;
    private final WartungProtokollService protokollService;
    private final WartungMitarbeiterUnterschriftService unterschriftService;
    private final WartungBenutzerService benutzerService;

    @GetMapping("/tueren/{tuerId}")
    public ResponseEntity<WartungAnlageDto> anlage(@PathVariable Long tuerId) {
        return ResponseEntity.ok(anlageService.laden(tuerId));
    }

    @PutMapping("/tueren/{tuerId}/projekt")
    public ResponseEntity<WartungAnlageDto> projektZuordnen(@PathVariable Long tuerId, @RequestBody WartungProjektZuordnungDto dto) {
        return ResponseEntity.ok(anlageService.projektZuordnen(tuerId, dto.projektId()));
    }

    /** Die Projekte des Kunden, aus denen man bei einer Wartung eines wählt. */
    @GetMapping("/kunden/{kundeId}/projekte")
    public ResponseEntity<List<WartungAnlageDto.Bezug>> projekteDesKunden(@PathVariable Long kundeId) {
        return ResponseEntity.ok(anlageService.projekteDesKunden(kundeId));
    }

    @GetMapping("/projekte/{projektId}/wartungen")
    public ResponseEntity<List<WartungProjektWartungDto>> projektWartungen(@PathVariable Long projektId) {
        return ResponseEntity.ok(anlageService.projektWartungen(projektId));
    }

    /** Erzeugt den PDF-Bericht der abgeschlossenen Prüfung und legt ihn im Projekt ab. */
    @PostMapping("/protokolle/{id}/bericht")
    public ResponseEntity<WartungBerichtDto> bericht(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(berichtService.erstelleImProjekt(id));
    }

    // ── Gespeicherte Unterschrift des angemeldeten Mitarbeiters ─────────────

    @GetMapping("/unterschrift/meine")
    public ResponseEntity<WartungGespeicherteUnterschriftDto> meineUnterschrift(Authentication authentication) {
        return unterschriftService.finde(benutzerService.mitarbeiterId(authentication))
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/unterschrift/meine")
    public ResponseEntity<WartungGespeicherteUnterschriftDto> meineUnterschriftSpeichern(
            Authentication authentication, @Valid @RequestBody WartungUnterschriftSpeichernDto dto) {
        return ResponseEntity.ok(unterschriftService.speichern(mitarbeiterIdOderFehler(authentication), dto.bild(), dto.name()));
    }

    @DeleteMapping("/unterschrift/meine")
    public ResponseEntity<Void> meineUnterschriftLoeschen(Authentication authentication) {
        unterschriftService.loeschen(benutzerService.mitarbeiterId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Setzt die gespeicherte Unterschrift des angemeldeten Mitarbeiters als Techniker-Unterschrift ein (als Kopie). */
    @PostMapping("/protokolle/{id}/unterschrift/gespeichert")
    public ResponseEntity<WartungProtokollDto> gespeicherteUnterschriftVerwenden(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(protokollService.gespeicherteUnterschriftVerwenden(id, benutzerService.mitarbeiterId(authentication)));
    }

    private Long mitarbeiterIdOderFehler(Authentication authentication) {
        Long id = benutzerService.mitarbeiterId(authentication);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ihr Benutzer ist keinem Mitarbeiter zugeordnet.");
        }
        return id;
    }
}
