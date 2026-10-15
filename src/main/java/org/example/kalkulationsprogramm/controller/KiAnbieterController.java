package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.AnbieterEingabe;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEingabe;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.Testergebnis;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.Uebersicht;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** KI-Anbieter und Datenschutz-Stufen einstellen, testen und das KI-Protokoll lesen. Liegt unter {@code /api/settings/**} und ist damit nur für Admins. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class KiAnbieterController {

    private final KiAnbieterService service;
    private final KiAktionProtokollRepository protokoll;

    public record SpeichernDto(Map<String, FunktionsEingabe> funktionen, AnbieterEingabe claude, AnbieterEingabe mistral) { }

    public record TestDto(String anbieter, String modell) { }

    @GetMapping("/ki-anbieter")
    public Uebersicht uebersicht() {
        return service.uebersicht();
    }

    @PutMapping("/ki-anbieter")
    public Uebersicht speichern(@RequestBody SpeichernDto eingabe) {
        return service.speichern(eingabe.funktionen(), eingabe.claude(), eingabe.mistral());
    }

    @PostMapping("/ki-anbieter/test")
    public Testergebnis test(@RequestBody TestDto eingabe) {
        AnbieterTyp typ = AnbieterTyp.ausName(eingabe == null ? null : eingabe.anbieter())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diesen Anbieter gibt es nicht."));
        return service.teste(typ, eingabe.modell());
    }

    /** Die Modelle, die dieser Anbieter anbietet (für die Auswahlfelder); 502, wenn der Anbieter nicht antwortet. */
    @GetMapping("/ki-anbieter/{anbieter}/modelle")
    public List<KiAnbieterService.Modell> modelle(@PathVariable String anbieter) {
        AnbieterTyp typ = AnbieterTyp.ausName(anbieter).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Anbieter gibt es nicht."));
        try {
            return service.verfuegbareModelle(typ);
        } catch (KiAnbieterFehler e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** Die letzten KI-Aktionen, neueste zuerst (höchstens 200). */
    @GetMapping("/ki-protokoll")
    public ResponseEntity<List<KiAktionProtokoll>> protokoll(@RequestParam(defaultValue = "50") int limit) {
        int n = Math.max(1, Math.min(200, limit));
        return ResponseEntity.ok(protokoll.findAllByOrderByZeitDescIdDesc(PageRequest.of(0, n)));
    }
}
