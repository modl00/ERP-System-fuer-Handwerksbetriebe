package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.Modul;
import org.example.kalkulationsprogramm.service.ModulSchalterService;
import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;

/**
 * Schalter für ganze Module (Wartung, Lager). {@code GET /api/features} dürfen alle Angemeldeten lesen, damit Menü und Seiten sich
 * danach richten; {@code PUT /api/settings/features/wartung} und {@code .../lager} liegen unter {@code /api/settings/**} und sind damit
 * nur für Admins.
 */
@RestController
@AllArgsConstructor
public class ModulSchalterController {

    private final WartungModulSchalterService wartungSchalter;
    private final ObjectProvider<LagerModulSchalterService> lagerSchalter;
    private final ObjectProvider<ModulSchalterService> modulSchalter;

    /** {@code miete} und {@code website} stehen im allgemeinen Schalter ({@link Modul}); fehlt der Dienst, gelten sie als eingeschaltet. */
    public record FeaturesDto(boolean wartung, boolean lager, boolean miete, boolean website, boolean plantafel) { }

    public record SchalterDto(@NotNull(message = "Bitte angeben, ob das Modul eingeschaltet ist.") Boolean aktiv) { }

    @GetMapping("/api/features")
    public ResponseEntity<FeaturesDto> features() {
        return ResponseEntity.ok(stand());
    }

    @PutMapping("/api/settings/features/wartung")
    public ResponseEntity<FeaturesDto> wartungSchalten(@Valid @RequestBody SchalterDto dto) {
        wartungSchalter.setzeAktiv(Boolean.TRUE.equals(dto.aktiv()));
        return ResponseEntity.ok(stand());
    }

    @PutMapping("/api/settings/features/lager")
    public ResponseEntity<FeaturesDto> lagerSchalten(@Valid @RequestBody SchalterDto dto) {
        LagerModulSchalterService lager = lagerSchalter.getIfAvailable();
        if (lager == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Lagermodul ist nicht installiert.");
        }
        lager.setzeAktiv(Boolean.TRUE.equals(dto.aktiv()));
        return ResponseEntity.ok(stand());
    }

    /** Schaltet eines der Module aus {@link Modul} ein oder aus. Wartung und Lager haben ihre eigenen Adressen darüber. */
    @PutMapping("/api/settings/features/{modul}")
    public ResponseEntity<FeaturesDto> modulSchalten(@PathVariable String modul, @Valid @RequestBody SchalterDto dto) {
        Modul m = Modul.ausName(modul).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieses Modul gibt es nicht."));
        ModulSchalterService dienst = modulSchalter.getIfAvailable();
        if (dienst == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, m.getAnzeige() + " ist nicht installiert.");
        }
        dienst.setzeAktiv(m, Boolean.TRUE.equals(dto.aktiv()));
        return ResponseEntity.ok(stand());
    }

    private FeaturesDto stand() {
        LagerModulSchalterService lager = lagerSchalter.getIfAvailable();
        ModulSchalterService dienst = modulSchalter.getIfAvailable();
        return new FeaturesDto(wartungSchalter.istAktiv(), lager == null || lager.istAktiv(),
                dienst == null || dienst.istAktiv(Modul.MIETE), dienst == null || dienst.istAktiv(Modul.WEBSITE),
                dienst == null || dienst.istAktiv(Modul.PLANTAFEL));
    }
}
