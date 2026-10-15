package org.example.kalkulationsprogramm.service.ki.assistent;

import org.example.kalkulationsprogramm.service.KiMerkzettelService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung, nur für Administratoren): Die KI merkt sich einen Hinweis des Betriebs für künftige Gespräche (#123, „Dazulernen“). Der Satz
 * steht danach auf der sichtbaren Merkliste in den Einstellungen und lässt sich dort löschen.
 */
@Component
@RequiredArgsConstructor
public class MerkenWerkzeug implements KiWerkzeug {

    private final KiMerkzettelService merkzettel;

    @Override
    public String name() {
        return "merken";
    }

    @Override
    public String beschreibung() {
        return "Merkt einen Hinweis des Betriebs für künftige Gespräche (nur nach Bestätigung des Administrators), zum Beispiel \"Lieferant Muster liefert nur freitags\" oder "
                + "\"Pulverbeschichtung geht immer an Meier\". Einen kurzen, sachlichen Satz schreiben; keine Namen von Privatpersonen, keine E-Mail-Adressen, Bankdaten oder Passwörter.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        o.putObject("properties").putObject("satz").put("type", "string").put("description", "Der Hinweis in einem kurzen Satz (höchstens 300 Zeichen)");
        o.putArray("required").add("satz");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return kontext != null && kontext.admin();
    }

    private String lies(JsonNode a) {
        String satz = WerkzeugHilfe.text(a, "satz", KiMerkzettelService.MAX_ZEICHEN * 2, true, "den Satz");
        try {
            return merkzettel.pruefe(satz);
        } catch (IllegalArgumentException e) {
            throw new KiWerkzeugFehler(e.getMessage());
        }
    }

    @Override
    public String vorschau(JsonNode angaben) {
        return "Auf die Merkliste setzen: \"" + lies(angaben) + "\"";
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        if (kontext == null || !kontext.admin()) {
            throw new KiWerkzeugFehler("Die Merkliste dürfen nur Administratoren ändern.");
        }
        try {
            merkzettel.merke(lies(angaben), kontext.benutzer());
            return "Gemerkt. Der Satz steht jetzt auf der Merkliste (Einstellungen → KI → Merkliste) und fließt in künftige Gespräche ein.";
        } catch (IllegalStateException e) {
            throw new KiWerkzeugFehler(e.getMessage());
        }
    }
}
