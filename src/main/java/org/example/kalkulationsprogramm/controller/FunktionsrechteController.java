package org.example.kalkulationsprogramm.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.AbteilungRechte;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.Rechte;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Rechte an den Funktionen des Programms je Abteilung. Ändern darf nur ein Administrator ({@code SecurityConfig}); lesen darf jeder Angemeldete.
 * {@code /api/funktionsrechte/meine} liefert dem Programm die Rechte des angemeldeten Benutzers, damit Menüpunkte ausgeblendet werden können.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FunktionsrechteController {

    private final FunktionsRechteService service;

    public record FunktionInfo(String name, String anzeige, String beschreibung) { }

    public record AbteilungDto(Long abteilungId, String abteilungName, Map<String, Rechte> rechte) { }

    public record UebersichtDto(List<FunktionInfo> funktionen, List<String> aktionen, List<AbteilungDto> abteilungen) { }

    public record SpeichernDto(Map<String, Rechte> rechte) { }

    /** Alle Funktionen, alle Abteilungen mit ihren Rechten. */
    @GetMapping("/abteilungen/funktionsrechte")
    public UebersichtDto uebersicht() {
        List<FunktionInfo> funktionen = new ArrayList<>();
        for (Funktion f : Funktion.values()) {
            funktionen.add(new FunktionInfo(f.name(), f.getAnzeige(), f.getBeschreibung()));
        }
        List<String> aktionen = new ArrayList<>();
        for (FunktionsAktion a : FunktionsAktion.values()) {
            aktionen.add(a.name());
        }
        List<AbteilungDto> abteilungen = service.alle().stream().map(FunktionsrechteController::dto).toList();
        return new UebersichtDto(funktionen, aktionen, abteilungen);
    }

    @PutMapping("/abteilungen/{id}/funktionsrechte")
    public ResponseEntity<AbteilungDto> speichern(@PathVariable Long id, @RequestBody SpeichernDto eingabe) {
        return ResponseEntity.ok(dto(service.speichern(id, eingabe == null ? null : eingabe.rechte())));
    }

    /** Die Rechte des angemeldeten Benutzers. Ohne Anmeldung am Profil (zum Beispiel ohne Mitarbeiter) ist alles erlaubt. */
    @GetMapping("/funktionsrechte/meine")
    public Map<String, Rechte> meine(Authentication authentication) {
        Long profilId = authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal p ? p.getId() : null;
        Map<String, Rechte> ergebnis = new LinkedHashMap<>();
        service.wirksamFuerProfil(profilId).forEach((f, r) -> ergebnis.put(f.name(), r));
        return ergebnis;
    }

    private static AbteilungDto dto(AbteilungRechte a) {
        Map<String, Rechte> rechte = new LinkedHashMap<>();
        a.rechte().forEach((f, r) -> rechte.put(f.name(), r));
        return new AbteilungDto(a.abteilungId(), a.abteilungName(), rechte);
    }
}
