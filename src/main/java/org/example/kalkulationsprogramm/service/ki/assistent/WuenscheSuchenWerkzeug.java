package org.example.kalkulationsprogramm.service.ki.assistent;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Auskunft (nur lesen, nur Administratoren): sucht offene Issues in Gitea, die zu einem Wunsch oder Fehler passen. Die KI ruft das vor
 * {@code wunsch_an_entwicklung} auf, damit ein Wunsch nicht doppelt angelegt wird, sondern als Kommentar unter das vorhandene Issue kommt (Issue #249).
 */
@Component
@RequiredArgsConstructor
public class WuenscheSuchenWerkzeug implements KiWerkzeug {

    private final EntwicklungsWunschService wunsch;

    @Override
    public String name() {
        return "wuensche_suchen";
    }

    @Override
    public String beschreibung() {
        return "Sucht offene Wünsche und Fehler (Issues) der Entwicklung, die zu einem neuen Wunsch passen. IMMER vor wunsch_an_entwicklung aufrufen, mit Titel und Beschreibung des "
                + "geplanten Wunsches. Gibt es einen Treffer, der dasselbe meint, den Text bei wunsch_an_entwicklung als kommentarZuIssue unter diese Nummer schreiben lassen.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        o.putObject("properties").putObject("suche").put("type", "string").put("description", "Titel und Beschreibung des geplanten Wunsches, in eigenen Worten");
        o.putArray("required").add("suche");
        return o;
    }

    @Override
    public boolean aendert() {
        return false;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return kontext != null && kontext.admin() && wunsch.istBereit();
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        String suche = WerkzeugHilfe.text(angaben, "suche", 2_000, true, "die Suche");
        var treffer = wunsch.sucheAehnliche(suche);
        if (treffer.isEmpty()) {
            return "Kein ähnliches offenes Issue gefunden. Ein neuer Wunsch kann angelegt werden.";
        }
        StringBuilder sb = new StringBuilder("Ähnliche offene Issues (bitte prüfen, ob eines dasselbe meint):\n");
        for (var t : treffer) {
            sb.append("- #").append(t.nummer()).append(": ").append(WerkzeugHilfe.kuerzen(t.titel(), 150)).append('\n');
        }
        return sb.append("Meint eines dasselbe, bei wunsch_an_entwicklung kommentarZuIssue mit dieser Nummer angeben.").toString();
    }
}
