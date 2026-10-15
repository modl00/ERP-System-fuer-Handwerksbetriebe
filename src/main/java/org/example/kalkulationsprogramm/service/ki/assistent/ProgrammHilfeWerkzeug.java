package org.example.kalkulationsprogramm.service.ki.assistent;

import org.example.kalkulationsprogramm.service.KiWissenService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * Auskunft: Wie funktioniert etwas im Programm? Liefert die passenden Auszüge aus dem Programmwissen (dasselbe Wissen, aus dem früher die „KI-Hilfe“ antwortete).
 * Damit gibt es nur noch ein KI-Fenster: Fragen zum Programm und Fragen zu den Daten laufen durch denselben Assistenten, der das passende Werkzeug wählt.
 * Das Werkzeug enthält keine Daten des Betriebs und steht deshalb auch bei strengem Datenschutz zur Verfügung.
 */
@Component
@RequiredArgsConstructor
public class ProgrammHilfeWerkzeug implements KiWerkzeug {

    static final int MAX_ZEICHEN = 6000;

    private final KiWissenService wissen;

    @Override
    public String name() {
        return "programm_hilfe";
    }

    @Override
    public String beschreibung() {
        return "Erklärt, wie etwas im Programm funktioniert und wo man es findet (Bedienung, Einstellungen, Abläufe). Nicht für Daten des Betriebs wie Kunden, Artikel oder Lager.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Die Frage zum Programm in einem Satz, zum Beispiel: Wie lege ich einen Lieferschein an?");
    }

    @Override
    public boolean aendert() {
        return false;
    }

    @Override
    public boolean beruehrtProgrammdaten() {
        return false;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return true;
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        String frage = WerkzeugHilfe.suchbegriff(angaben);
        String auszug = wissen.relevantes(frage, MAX_ZEICHEN);
        if (auszug.isBlank()) {
            return "Dazu steht nichts im Programmwissen. Sage ehrlich, dass du es nicht sicher weißt.";
        }
        String grenzen = wissen.grenzen();
        return "Auszüge aus dem Programmwissen:\n" + auszug
                + (grenzen == null || grenzen.isBlank() ? "" : "\n\nWas das Programm nicht kann:\n" + grenzen);
    }
}
