package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung, nur für Administratoren): legt aus einem Gespräch einen Verbesserungswunsch als Issue in Gitea an (#138, #123). Das Issue
 * enthält keine Kundendaten und keine Zugangsdaten: Das Werkzeug lehnt Texte mit E-Mail-Adressen, IBAN oder Passwort-Angaben ab, und die Vorschau zeigt den ganzen Text.
 * Ohne eingerichteten Zugang (Einstellungen → KI → Wünsche an die Entwicklung) gibt es das Werkzeug nicht.
 */
@Component
@RequiredArgsConstructor
public class WunschAnEntwicklungWerkzeug implements KiWerkzeug {

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]++@[^\\s@.]++\\.[^\\s@]++");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}\\b");
    private static final Pattern GEHEIM = Pattern.compile("(?i)(passwort|kennwort|password|api[-_ ]?key|token|secret|geheimnis)\\s*[:=]");

    private final EntwicklungsWunschService wunsch;
    private final FehlerBerichtService bericht;

    @Override
    public String name() {
        return "wunsch_an_entwicklung";
    }

    @Override
    public String beschreibung() {
        return "Legt einen Verbesserungswunsch oder Fehlerbericht für die Entwicklung als Issue an (nur nach Bestätigung des Administrators). Rufe VORHER immer wuensche_suchen auf: Gibt es schon ein passendes offenes Issue, gib dessen Nummer bei \"kommentarZuIssue\" an, dann wird nur ein Kommentar darunter geschrieben und kein neues Issue angelegt. Wähle bei \"art\" FEHLER, wenn etwas nicht funktioniert: Dann hängt das Programm selbst Seite, Browser-Fehler und die letzten Server-Fehler an, die du nicht kennst und nicht schreiben musst. Schreibe allgemein: "
                + "KEINE Namen von Kunden oder Mitarbeitern, keine E-Mail-Adressen, Bankdaten oder Passwörter. Beschreibe, was gewünscht ist oder nicht klappt, auf welcher Seite, und was erwartet wird.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("titel").put("type", "string").put("description", "Kurzer Titel, zum Beispiel \"Lieferschein: Unterschrift löschen können\"");
        p.putObject("beschreibung").put("type", "string").put("description", "Was ist gewünscht oder passiert, wo im Programm, was wird erwartet (ohne Kundendaten)");
        p.putObject("art").put("type", "string").put("description", "FEHLER, wenn etwas nicht funktioniert oder eine Fehlermeldung kommt; WUNSCH für eine Verbesserung oder neue Funktion").putArray("enum").add("FEHLER").add("WUNSCH");
        p.putObject("kommentarZuIssue").put("type", "integer").put("description", "Nummer eines vorhandenen offenen Issues (aus wuensche_suchen), unter das der Text als Kommentar geschrieben werden soll; weglassen, wenn nichts Passendes existiert");
        o.putArray("required").add("titel").add("beschreibung").add("art");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return kontext != null && kontext.admin() && wunsch.istBereit();
    }

    private record Eingabe(String titel, String beschreibung, boolean fehler, String kontext, Integer kommentarZu) { }

    /** Der Server hängt den Fehlerkontext an (und überschreibt, was die KI unter diesem Namen schickt); nur bei Fehlerberichten. */
    @Override
    public JsonNode erweitere(JsonNode angaben, KiKontext kontext) {
        ObjectNode kopie = angaben instanceof ObjectNode o ? o.deepCopy() : WerkzeugHilfe.JSON.createObjectNode();
        kopie.remove("kontext");
        if (istFehler(kopie) && kontext != null && kontext.fehler() != null) {
            String anhang = bericht.anhang(kontext.fehler());
            if (!anhang.isBlank()) {
                kopie.put("kontext", anhang);
            }
        }
        return kopie;
    }

    private static boolean istFehler(JsonNode a) {
        return "FEHLER".equalsIgnoreCase(a.path("art").asText(""));
    }

    private Eingabe lies(JsonNode a) {
        String titel = WerkzeugHilfe.text(a, "titel", EntwicklungsWunschService.MAX_TITEL, true, "den Titel");
        String text = WerkzeugHilfe.text(a, "beschreibung", EntwicklungsWunschService.MAX_TEXT, true, "die Beschreibung");
        String alles = titel + "\n" + text;
        if (EMAIL.matcher(alles).find() || IBAN.matcher(alles).find() || GEHEIM.matcher(alles).find()) {
            throw new KiWerkzeugFehler("Der Text enthält eine E-Mail-Adresse, Bankdaten oder eine Zugangsangabe. Bitte allgemein formulieren, ohne Kundendaten und ohne Passwörter.");
        }
        Long zu = WerkzeugHilfe.nummer(a, "kommentarZuIssue", "die Issue-Nummer");
        if (zu != null && (zu <= 0 || zu > Integer.MAX_VALUE)) {
            throw new KiWerkzeugFehler("Die Issue-Nummer ist ungültig.");
        }
        return new Eingabe(titel, text, istFehler(a), a.path("kontext").asText(""), zu == null ? null : zu.intValue());
    }

    /** Kürzt den Anhang auf den freien Platz, ohne das aufklappbare Stück offen zu lassen. */
    private static String passend(String anhang, int platz) {
        if (anhang.length() <= platz) {
            return anhang;
        }
        String ende = "\n\n… (gekürzt)\n</details>";
        int bis = Math.max(0, platz - ende.length());
        return anhang.substring(0, bis) + ende;
    }

    @Override
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        String kopf;
        if (e.kommentarZu() != null) {
            var vorhanden = wunsch.holeOffenes(e.kommentarZu());
            if (vorhanden == null) {
                throw new KiWerkzeugFehler("Das Issue " + e.kommentarZu() + " gibt es nicht oder es ist nicht mehr offen. Bitte ohne kommentarZuIssue ein neues anlegen oder mit wuensche_suchen neu suchen.");
            }
            kopf = (e.fehler() ? "Fehlerbericht" : "Wunsch") + " als KOMMENTAR unter das vorhandene Issue #" + vorhanden.nummer() + " \"" + vorhanden.titel() + "\" schreiben (es wird kein neues angelegt). Text: " + WerkzeugHilfe.kuerzen(e.beschreibung(), 600);
        } else {
            kopf = (e.fehler() ? "Fehlerbericht" : "Wunsch") + " an die Entwicklung senden (Gitea, NEUES Issue): \"" + e.titel() + "\". Text: " + WerkzeugHilfe.kuerzen(e.beschreibung(), 600);
            var aehnlich = wunsch.sucheAehnliche(e.titel() + " " + e.beschreibung());
            if (!aehnlich.isEmpty()) {
                kopf += "\nACHTUNG, ähnliche offene Issues gibt es schon: " + aehnlich.stream().map(a -> "#" + a.nummer() + " " + a.titel()).collect(java.util.stream.Collectors.joining("; "))
                        + ". Wenn eines davon dasselbe meint, bitte ablehnen und die KI bitten, es als Kommentar dort zu schreiben.";
            }
        }
        if (e.kontext().isBlank()) {
            return kopf;
        }
        // Der ganze Anhang steht in der Vorschau: Was mitgeht, sieht der Administrator vor der Bestätigung
        return kopf + "\n\nDas Programm hängt automatisch an:\n" + e.kontext().replaceAll("</?(details|summary)>", "").replace("**", "").strip();
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        if (kontext == null || !kontext.admin()) {
            throw new KiWerkzeugFehler("Wünsche an die Entwicklung dürfen nur Administratoren senden.");
        }
        Eingabe e = lies(angaben);
        try {
            String fuss = "\n\n---\nAngelegt aus dem Programm über die KI auf Wunsch von " + kontext.benutzer() + " (bestätigt).";
            // Der Anhang bekommt den Platz, den der Text übrig lässt; reicht er für fast nichts, bleibt er weg
            int platz = EntwicklungsWunschService.MAX_TEXT - e.beschreibung().length() - fuss.length() - 2;
            String anhang = e.kontext().isBlank() || platz < 300 ? "" : "\n\n" + passend(e.kontext(), platz);
            // Fehler sind für die automatische Bearbeitung freigegeben: Die Bestätigung des Administrators ist die Freigabe
            if (e.kommentarZu() != null) {
                var k = wunsch.kommentiere(e.kommentarZu(), "**Ergänzung aus dem Programm:** " + e.titel() + "\n\n" + e.beschreibung() + anhang + fuss.replace("Angelegt aus", "Geschrieben aus"));
                return "Der Text wurde als Kommentar unter das Issue #" + e.kommentarZu() + " geschrieben" + (k.adresse().isBlank() ? "." : ": " + k.adresse());
            }
            var r = wunsch.legeAn(e.titel(), e.beschreibung() + anhang + fuss, e.fehler() ? List.of("Fehler", "claude-bearbeiten") : List.of("Wunsch"));
            return "Der Wunsch wurde als Issue #" + r.nummer() + " angelegt" + (r.adresse().isBlank() ? "." : ": " + r.adresse());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new KiWerkzeugFehler(ex.getMessage());
        }
    }
}
