package org.example.kalkulationsprogramm.controller.wartung;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.wartung.WartungAktivDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungGlobalesDatenfeldDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulUebersichtDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungReihenfolgeDto;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Modul-Editor: Pflege des Vorlagenkatalogs. Der ganze Pfad {@code /api/wartung/admin/**} ist in der
 * Sicherheitskonfiguration auf die Rolle ADMIN beschränkt, auch lesend.
 */
@RestController
@RequestMapping("/api/wartung/admin")
@AllArgsConstructor
public class WartungModulAdminController {

    private final WartungModulAdminService service;

    @GetMapping("/module")
    public ResponseEntity<List<WartungModulUebersichtDto>> alleModule() {
        return ResponseEntity.ok(service.alleModule());
    }

    @GetMapping("/module/{id}")
    public ResponseEntity<WartungModulBearbeitenDto> modul(@PathVariable Long id) {
        return ResponseEntity.ok(service.laden(id));
    }

    @PostMapping("/module")
    public ResponseEntity<WartungModulBearbeitenDto> anlegen(@Valid @RequestBody WartungModulBearbeitenDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.anlegen(dto));
    }

    @PutMapping("/module/{id}")
    public ResponseEntity<WartungModulBearbeitenDto> speichern(
            @PathVariable Long id, @Valid @RequestBody WartungModulBearbeitenDto dto) {
        return ResponseEntity.ok(service.speichern(id, dto));
    }

    @PutMapping("/module/{id}/aktiv")
    public ResponseEntity<WartungModulBearbeitenDto> aktivSetzen(
            @PathVariable Long id, @Valid @RequestBody WartungAktivDto dto) {
        return ResponseEntity.ok(service.aktivSetzen(id, Boolean.TRUE.equals(dto.aktiv())));
    }

    @DeleteMapping("/module/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/module/reihenfolge")
    public ResponseEntity<List<WartungModulUebersichtDto>> reihenfolge(@Valid @RequestBody WartungReihenfolgeDto dto) {
        return ResponseEntity.ok(service.reihenfolgeSetzen(dto.ids()));
    }

    @GetMapping("/globale-felder")
    public ResponseEntity<List<WartungGlobalesDatenfeldDto>> globaleFelder() {
        return ResponseEntity.ok(service.globaleFelder());
    }

    @PutMapping("/globale-felder")
    public ResponseEntity<List<WartungGlobalesDatenfeldDto>> globaleFelderSpeichern(
            @RequestBody @Valid List<@Valid WartungGlobalesDatenfeldDto> felder) {
        return ResponseEntity.ok(service.globaleFelderSpeichern(felder));
    }
}
