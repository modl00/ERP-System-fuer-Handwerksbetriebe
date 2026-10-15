package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto;
import org.example.kalkulationsprogramm.service.RechteUebersichtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** "Wer darf was?" (Issue #87). Nur lesen; {@code /api/admin/**} ist in der Sicherheitskonfiguration auf Administratoren beschränkt. */
@RestController
@RequestMapping("/api/admin/rechte-uebersicht")
@RequiredArgsConstructor
public class RechteUebersichtController {

    private final RechteUebersichtService service;

    @GetMapping
    public RechteUebersichtDto uebersicht() {
        return service.uebersicht();
    }
}
