package org.example.kalkulationsprogramm.controller.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulDto;
import org.example.kalkulationsprogramm.service.wartung.WartungKatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Vorlagenkatalog der Wartung: welche Module (Brandschutztür, Feststellanlage ...) es gibt. */
@RestController
@RequestMapping("/api/wartung/module")
@AllArgsConstructor
public class WartungKatalogController {

    private final WartungKatalogService katalogService;

    @GetMapping
    public ResponseEntity<List<WartungModulDto>> aktiveModule() {
        return ResponseEntity.ok(katalogService.aktiveModule());
    }
}
