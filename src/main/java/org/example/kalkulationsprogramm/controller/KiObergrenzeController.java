package org.example.kalkulationsprogramm.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Eingabe;
import org.example.kalkulationsprogramm.service.ki.assistent.KiWerkzeug;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Die zentrale Obergrenze für die KI lesen und ändern (Issue #125). Liegt unter {@code /api/settings/**} und ist damit nur für Administratoren. */
@RestController
@RequestMapping("/api/settings/ki-obergrenze")
@RequiredArgsConstructor
public class KiObergrenzeController {

    private final KiObergrenze obergrenze;
    private final List<KiWerkzeug> werkzeuge;

    public record AnbieterZeile(String typ, String anzeige, boolean lokal, boolean vertrag) { }

    public record WerkzeugZeile(String name, String beschreibung, boolean aendert, boolean freischaltbar, boolean freigegeben) { }

    public record Stand(String datenschutz, String eingriff, List<AnbieterZeile> anbieter, List<WerkzeugZeile> werkzeuge) { }

    public record Speichern(String datenschutz, String eingriff, Map<String, Boolean> vertrag, List<String> ohneRueckfrage) { }

    @GetMapping
    public Stand stand() {
        Set<String> frei = obergrenze.freigeschalteteWerkzeuge();
        List<AnbieterZeile> anbieter = java.util.Arrays.stream(AnbieterTyp.values())
                .map(a -> new AnbieterZeile(a.name(), a.anzeige(), a.lokal(), obergrenze.hatVertrag(a))).toList();
        List<WerkzeugZeile> zeilen = werkzeuge.stream()
                .map(w -> new WerkzeugZeile(w.name(), w.beschreibung(), w.aendert(), freischaltbar(w), frei.contains(w.name())))
                .toList();
        return new Stand(obergrenze.datenschutz().name(), obergrenze.eingriff().name(), anbieter, zeilen);
    }

    @PutMapping
    public Stand speichern(@RequestBody Speichern eingabe, Authentication authentication) {
        Set<String> erlaubt = werkzeuge.stream().filter(KiObergrenzeController::freischaltbar).map(KiWerkzeug::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Long profilId = authentication != null && authentication.getPrincipal() instanceof FrontendUserPrincipal p ? p.getId() : null;
        String benutzer = authentication == null ? null : authentication.getName();
        obergrenze.speichern(new Eingabe(eingabe.datenschutz(), eingabe.eingriff(), eingabe.vertrag(),
                eingabe.ohneRueckfrage() == null ? Set.of() : new LinkedHashSet<>(eingabe.ohneRueckfrage())), erlaubt, profilId, benutzer);
        return stand();
    }

    /** Nur ändernde Werkzeuge, die nichts löschen, können ohne Rückfrage freigeschaltet werden. */
    static boolean freischaltbar(KiWerkzeug w) {
        return w.aendert() && !w.loescht();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() == null ? "Ungültige Eingabe." : e.getMessage()));
    }
}
