package org.example.kalkulationsprogramm.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.Rechte;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Rechte für die Handy-App: welche Funktionen darf der Mitarbeiter zu diesem Zugangslink sehen, anlegen, ändern und löschen, und welche Module sind
 * eingeschaltet. Es sind dieselben Rechte wie am PC (Rechteverwaltung). Die App blendet danach Bereiche aus; maßgeblich bleibt die Prüfung auf dem
 * Server bei jedem Endpunkt ({@link FunktionsRechteService#verlangeFuerToken}).
 */
@RestController
@RequestMapping("/api/zeiterfassung/rechte")
@RequiredArgsConstructor
public class ZeiterfassungRechteController {

    private final FunktionsRechteService service;
    private final WartungModulSchalterService wartungSchalter;
    private final ObjectProvider<LagerModulSchalterService> lagerSchalter;

    public record Antwort(Map<String, Rechte> funktionen, Map<String, Boolean> module) { }

    @GetMapping("/{token}")
    public Antwort rechte(@PathVariable String token) {
        Map<String, Rechte> funktionen = new LinkedHashMap<>();
        service.wirksamFuerToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig. Bitte neu anmelden."))
                .forEach((f, r) -> funktionen.put(f.name(), r));
        LagerModulSchalterService lager = lagerSchalter.getIfAvailable();
        Map<String, Boolean> module = new LinkedHashMap<>();
        module.put("wartung", wartungSchalter.istAktiv());
        module.put("lager", lager == null || lager.istAktiv());
        return new Antwort(funktionen, module);
    }
}
