package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.service.MailOrdnerService;
import org.example.kalkulationsprogramm.service.MailOrdnerService.Eintrag;
import org.example.kalkulationsprogramm.service.MailOrdnerService.Uebersicht;
import org.example.kalkulationsprogramm.service.MailOrdnerService.Wahl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Ordnerwahl des E-Mail-Centers. Nur Administratoren ({@code /api/settings/**}, SecurityConfig). */
@RestController
@RequestMapping("/api/settings/email/ordner")
@RequiredArgsConstructor
public class MailOrdnerController {

    private final MailOrdnerService ordner;

    /** Verbindet sich mit dem Postfach und liefert alle Ordner samt gespeicherter Auswahl. */
    @GetMapping
    public Uebersicht uebersicht() {
        try {
            return ordner.lade();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @PutMapping
    public List<Eintrag> speichern(@RequestBody List<Wahl> wahl) {
        try {
            return ordner.speichere(wahl, LocalDate.now());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
