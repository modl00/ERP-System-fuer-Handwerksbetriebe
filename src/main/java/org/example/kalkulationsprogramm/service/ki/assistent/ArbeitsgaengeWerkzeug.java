package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.Comparator;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: die Arbeitsgänge (Tätigkeiten), auf die man Zeit buchen kann, mit ihrer ID. Wird für „Zeit buchen“ gebraucht. */
@Component
@RequiredArgsConstructor
public class ArbeitsgaengeWerkzeug implements KiWerkzeug {

    static final int MAX_ZEILEN = 60;

    private final ArbeitsgangRepository arbeitsgaenge;

    @Override
    public String name() {
        return "arbeitsgaenge_auflisten";
    }

    @Override
    public String beschreibung() {
        return "Listet die Arbeitsgänge (Tätigkeiten wie Schweißen, Montage, Fahrzeit) mit ihrer ID auf. Die ID wird zum Zeit buchen gebraucht.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.JSON.createObjectNode().put("type", "object").set("properties", WerkzeugHilfe.JSON.createObjectNode());
    }

    @Override
    public boolean aendert() {
        return false;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        List<Arbeitsgang> alle = arbeitsgaenge.findAll().stream().sorted(Comparator.comparing(a -> a.getBeschreibung() == null ? "" : a.getBeschreibung(), String.CASE_INSENSITIVE_ORDER)).toList();
        if (alle.isEmpty()) {
            return "Es sind noch keine Arbeitsgänge angelegt.";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Arbeitsgang a : alle) {
            if (++n > MAX_ZEILEN) {
                sb.append("… weitere Arbeitsgänge nicht aufgelistet.");
                break;
            }
            sb.append("- ID ").append(a.getId()).append(" | ").append(WerkzeugHilfe.kuerzen(a.getBeschreibung(), 80)).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
