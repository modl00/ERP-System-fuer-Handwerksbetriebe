package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ki.assistent.KiBelegService;
import org.example.kalkulationsprogramm.service.ki.assistent.KiBelegService.Vorschlag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

/** Beleg im KI-Chat: Auswertung mit Vorschlag zum Ablegen (Issue #127). Speichert nichts; abgelegt wird über den Import des Lieferanten. */
@RestController
@RequestMapping("/api/ki-assistent/beleg")
@RequiredArgsConstructor
public class KiBelegController {

    private final KiBelegService service;

    @PostMapping(value = "/pruefen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Vorschlag pruefen(@RequestPart("datei") MultipartFile datei) {
        return service.pruefe(datei);
    }
}
