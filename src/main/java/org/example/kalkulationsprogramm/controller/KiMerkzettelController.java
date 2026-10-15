package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.KiMerkzettelService;
import org.example.kalkulationsprogramm.service.KiMerkzettelService.Eintrag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Die Merkliste der KI in den Einstellungen: ansehen und löschen. Unter {@code /api/settings}, also nur für Administratoren. Angelegt wird nur über das KI-Werkzeug mit Bestätigung. */
@RestController
@RequestMapping("/api/settings/ki-merkliste")
@RequiredArgsConstructor
public class KiMerkzettelController {

    private final KiMerkzettelService service;

    @GetMapping
    public List<Eintrag> alle() {
        return service.alle();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loesche(id);
        return ResponseEntity.noContent().build();
    }
}
