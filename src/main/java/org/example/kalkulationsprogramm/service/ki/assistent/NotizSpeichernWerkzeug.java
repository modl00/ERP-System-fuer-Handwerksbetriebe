package org.example.kalkulationsprogramm.service.ki.assistent;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeNotiz;
import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): speichert eine Information als <b>Notiz</b> bei einem Lieferanten oder Kunden, zum Beispiel aus einer E-Mail im Chat
 * („das ist interessant, speichere das beim Lieferanten“). Legt dieselbe Notiz an wie das Notizfeld beim Lieferanten beziehungsweise Kunden; es wird nichts anderes verändert.
 */
@Component
@RequiredArgsConstructor
public class NotizSpeichernWerkzeug implements KiWerkzeug {

    static final int MAX_TEXT = 5_000;

    private final LieferantenRepository lieferanten;
    private final LieferantNotizRepository lieferantNotizen;
    private final KundeRepository kunden;
    private final KundeNotizRepository kundeNotizen;

    @Override
    public String name() {
        return "notiz_speichern";
    }

    @Override
    public String beschreibung() {
        return "Speichert eine Information als Notiz bei einem Lieferanten (art LIEFERANT) oder Kunden (art KUNDE), nur nach Bestätigung des Benutzers. Vorher mit lieferanten_suchen "
                + "beziehungsweise kunden_suchen die ID klären. Der Text soll die Information sachlich und kurz festhalten (nicht die ganze E-Mail), mit Quelle wie "
                + "\"Aus E-Mail vom 10.10.2026, Betreff ...\". Nichts erfinden und keine Zugangsdaten oder Bankdaten speichern.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("art").put("type", "string").put("description", "LIEFERANT oder KUNDE").putArray("enum").add("LIEFERANT").add("KUNDE");
        p.putObject("id").put("type", "integer").put("description", "ID des Lieferanten oder Kunden (aus lieferanten_suchen beziehungsweise kunden_suchen)");
        p.putObject("text").put("type", "string").put("description", "Die Information als kurze Notiz, mit Quelle");
        o.putArray("required").add("art").add("id").add("text");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return true;
    }

    private record Eingabe(boolean lieferant, Long id, String text) { }

    private Eingabe lies(JsonNode a) {
        String art = WerkzeugHilfe.text(a, "art", 20, true, "die Art (LIEFERANT oder KUNDE)").toUpperCase(java.util.Locale.ROOT);
        if (!art.equals("LIEFERANT") && !art.equals("KUNDE")) {
            throw new KiWerkzeugFehler("Die Art muss LIEFERANT oder KUNDE sein.");
        }
        Long id = WerkzeugHilfe.nummer(a, "id", "die ID");
        if (id == null) {
            throw new KiWerkzeugFehler("Bitte die ID angeben (zuerst suchen).");
        }
        String text = WerkzeugHilfe.text(a, "text", MAX_TEXT, true, "den Notiztext");
        if (text.length() < 3) {
            throw new KiWerkzeugFehler("Der Notiztext ist zu kurz.");
        }
        return new Eingabe(art.equals("LIEFERANT"), id, text);
    }

    @Override
    @Transactional(readOnly = true)
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        String ziel = e.lieferant()
                ? "Lieferant \"" + lieferanten.findById(e.id()).map(Lieferanten::getLieferantenname).orElseThrow(() -> new KiWerkzeugFehler("Den Lieferanten " + e.id() + " gibt es nicht.")) + "\""
                : "Kunde \"" + kunden.findById(e.id()).map(Kunde::getName).orElseThrow(() -> new KiWerkzeugFehler("Den Kunden " + e.id() + " gibt es nicht.")) + "\"";
        return "Notiz beim " + ziel + " speichern:\n" + e.text();
    }

    @Override
    @Transactional
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        if (e.lieferant()) {
            Lieferanten l = lieferanten.findById(e.id()).orElseThrow(() -> new KiWerkzeugFehler("Den Lieferanten " + e.id() + " gibt es nicht."));
            LieferantNotiz n = new LieferantNotiz();
            n.setLieferant(l);
            n.setText(e.text());
            lieferantNotizen.save(n);
            return "Die Notiz wurde beim Lieferanten \"" + l.getLieferantenname() + "\" gespeichert.";
        }
        Kunde k = kunden.findById(e.id()).orElseThrow(() -> new KiWerkzeugFehler("Den Kunden " + e.id() + " gibt es nicht."));
        KundeNotiz n = new KundeNotiz();
        n.setKunde(k);
        n.setText(e.text());
        kundeNotizen.save(n);
        return "Die Notiz wurde beim Kunden \"" + k.getName() + "\" gespeichert.";
    }
}
