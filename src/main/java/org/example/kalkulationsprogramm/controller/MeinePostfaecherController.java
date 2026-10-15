package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.BenutzerPostfachService;
import org.example.kalkulationsprogramm.service.BenutzerPostfachService.Eingabe;
import org.example.kalkulationsprogramm.service.BenutzerPostfachService.PostfachDto;
import org.example.kalkulationsprogramm.service.BenutzerPostfachService.Testergebnis;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** „Meine Postfächer“: jeder angemeldete Benutzer verwaltet seine eigenen (Issue #272). Fremde Postfächer sind nie erreichbar. */
@RestController
@RequestMapping("/api/mein/postfaecher")
@RequiredArgsConstructor
public class MeinePostfaecherController {

    private final BenutzerPostfachService service;

    @GetMapping
    public List<PostfachDto> liste(Authentication a) {
        return service.liste(profilId(a));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostfachDto anlegen(Authentication a, @RequestBody Eingabe e) {
        return mitFehler(() -> service.anlegen(profilId(a), e));
    }

    @PutMapping("/{id}")
    public PostfachDto aendern(Authentication a, @PathVariable Long id, @RequestBody Eingabe e) {
        return mitFehler(() -> service.aendern(profilId(a), id, e));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(Authentication a, @PathVariable Long id) {
        mitFehler(() -> { service.loesche(profilId(a), id); return null; });
    }

    /** Prüft die Anmeldung für ein neues (noch nicht gespeichertes) Postfach. */
    @PostMapping("/test")
    public Testergebnis testeNeu(Authentication a, @RequestBody Eingabe e) {
        return mitFehler(() -> service.teste(profilId(a), null, e));
    }

    /** Prüft die Anmeldung für ein gespeichertes Postfach; ein leeres Passwort in der Eingabe heißt: das gespeicherte benutzen. */
    @PostMapping("/{id}/test")
    public Testergebnis testeGespeichert(Authentication a, @PathVariable Long id, @RequestBody Eingabe e) {
        return mitFehler(() -> service.teste(profilId(a), id, e));
    }

    private static Long profilId(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof FrontendUserPrincipal p) || p.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Persönliche Postfächer gibt es nur für angemeldete Benutzer.");
        }
        return p.getId();
    }

    private static <T> T mitFehler(java.util.function.Supplier<T> aktion) {
        try {
            return aktion.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
