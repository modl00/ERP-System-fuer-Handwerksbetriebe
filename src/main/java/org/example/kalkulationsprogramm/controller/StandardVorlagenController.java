package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.dto.Formular.VorlagenEinstellungen;
import org.example.kalkulationsprogramm.service.StandardVorlagenService;
import org.example.kalkulationsprogramm.service.StandardVorlagenService.Ergebnis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Mitgelieferte Standardvorlagen für Angebot, Rechnung, Mahnung und Co. sowie die Firmen-Platzhalter. */
@RestController
@RequestMapping("/api/formulare")
@RequiredArgsConstructor
public class StandardVorlagenController {

    private final StandardVorlagenService standardVorlagenService;

    /** Legt fehlende Standardvorlagen samt Standardtexten an. Vorhandenes bleibt unverändert. */
    @PostMapping("/standardvorlagen")
    public ResponseEntity<Ergebnis> anlegen() {
        return ResponseEntity.ok(standardVorlagenService.anlegen());
    }

    /** Die Standardeinstellungen einer Vorlage ({@code art=lieferschein}: ohne Preise, mit Artikelnummer), Ausgangspunkt für den Einstellungsdialog. */
    @GetMapping("/layout-einstellungen/standard")
    public ResponseEntity<VorlagenEinstellungen> standardEinstellungen(@RequestParam(defaultValue = "standard") String art) {
        return ResponseEntity.ok(standardVorlagenService.standardEinstellungen("lieferschein".equalsIgnoreCase(art)));
    }

    /** Baut das Seitenlayout nach den Einstellungen und liefert es als Vorlagen-HTML (mit den Einstellungen als Meta-Tag). */
    @PostMapping("/layout-aus-einstellungen")
    public ResponseEntity<Map<String, Object>> layoutAusEinstellungen(@RequestBody VorlagenEinstellungen einstellungen) {
        VorlagenEinstellungen bereinigt = einstellungen.bereinigt();
        return ResponseEntity.ok(Map.of("html", standardVorlagenService.layoutAusEinstellungen(bereinigt), "einstellungen", bereinigt));
    }

    /** Werte der Firmen-Platzhalter aus den Einstellungen, zum Beispiel {@code FIRMENNAME}. */
    @GetMapping("/firmen-platzhalter")
    public ResponseEntity<Map<String, String>> firmenPlatzhalter() {
        return ResponseEntity.ok(standardVorlagenService.firmenPlatzhalter());
    }
}
