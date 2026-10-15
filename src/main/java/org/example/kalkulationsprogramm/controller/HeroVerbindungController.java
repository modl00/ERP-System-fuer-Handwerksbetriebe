package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.service.importe.HeroVerbindungService;
import org.example.kalkulationsprogramm.service.importe.HeroVerbindungService.Ergebnis;
import org.example.kalkulationsprogramm.service.importe.HeroVerbindungService.Feld;
import org.example.kalkulationsprogramm.service.importe.HeroVerbindungService.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Hero-Verbindung (Issue #76). Unter {@code /api/settings/**}: nur Administratoren. */
@RestController
@RequestMapping("/api/settings/import/hero")
@RequiredArgsConstructor
public class HeroVerbindungController {

    private final HeroVerbindungService hero;

    public record SchluesselAnfrage(String schluessel) { }

    public record AbfrageAnfrage(String abfrage) { }

    @GetMapping
    public Status status() {
        return hero.status();
    }

    @PutMapping("/schluessel")
    public Status schluessel(@RequestBody SchluesselAnfrage a) {
        return mit(() -> hero.speichereSchluessel(a == null ? null : a.schluessel()));
    }

    @PostMapping("/test")
    public Ergebnis test() {
        return mit(hero::teste);
    }

    @GetMapping("/schnittstelle")
    public List<Feld> schnittstelle() {
        return mit(hero::schnittstelle);
    }

    @PostMapping("/abfrage")
    public Ergebnis abfrage(@RequestBody AbfrageAnfrage a) {
        return mit(() -> hero.abfrage(a == null ? null : a.abfrage()));
    }

    private static <T> T mit(java.util.function.Supplier<T> aktion) {
        try {
            return aktion.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
