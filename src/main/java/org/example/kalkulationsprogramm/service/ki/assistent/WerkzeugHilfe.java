package org.example.kalkulationsprogramm.service.ki.assistent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Kleine Hilfen für Werkzeuge: Schema bauen, Angaben lesen und begrenzen. */
final class WerkzeugHilfe {

    static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_SUCHBEGRIFF = 100;

    private WerkzeugHilfe() {
    }

    /** Schema mit genau einer Pflicht-Angabe {@code suche} (Text). */
    static JsonNode sucheSchema(String beschreibung) {
        var o = JSON.createObjectNode().put("type", "object");
        o.putObject("properties").putObject("suche").put("type", "string").put("description", beschreibung);
        o.putArray("required").add("suche");
        return o;
    }

    /** Der Suchbegriff aus den Angaben: nicht leer, höchstens 100 Zeichen, ohne Steuerzeichen. */
    static String suchbegriff(JsonNode angaben) {
        String s = angaben == null ? "" : angaben.path("suche").asText("").replaceAll("\\p{Cntrl}", " ").strip();
        if (s.isEmpty()) {
            throw new KiWerkzeugFehler("Bitte einen Suchbegriff angeben.");
        }
        return s.length() > MAX_SUCHBEGRIFF ? s.substring(0, MAX_SUCHBEGRIFF) : s;
    }

    static String kuerzen(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max) + " …";
    }

    /** Ein Text aus den Angaben: ohne Steuerzeichen, gekürzt auf höchstens {@code max}; bei {@code pflicht} nicht leer, sonst {@code null} wenn leer. */
    static String text(JsonNode angaben, String feld, int max, boolean pflicht, String anzeigeName) {
        JsonNode n = angaben == null ? null : angaben.path(feld);
        String t = n == null || n.isMissingNode() || n.isNull() ? "" : n.asText("").replaceAll("\\p{Cntrl}", " ").strip();
        if (t.isEmpty()) {
            if (pflicht) {
                throw new KiWerkzeugFehler("Bitte " + anzeigeName + " angeben.");
            }
            return null;
        }
        if (t.length() > max) {
            throw new KiWerkzeugFehler(anzeigeName.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + anzeigeName.substring(1) + " darf höchstens " + max + " Zeichen lang sein.");
        }
        return t;
    }

    /** Eine Zahl aus den Angaben (Zahl oder Text wie "12,5"); {@code null}, wenn nicht angegeben; Fehler, wenn angegeben, aber keine Zahl. */
    static java.math.BigDecimal zahl(JsonNode angaben, String feld, String anzeigeName) {
        JsonNode n = angaben == null ? null : angaben.path(feld);
        if (n == null || n.isMissingNode() || n.isNull() || (n.isTextual() && n.asText().isBlank())) {
            return null;
        }
        try {
            return n.isNumber() ? n.decimalValue() : new java.math.BigDecimal(n.asText().strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new KiWerkzeugFehler(anzeigeName.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + anzeigeName.substring(1) + " ist keine Zahl.");
        }
    }

    /** Eine ganze Zahl (zum Beispiel eine Nummer); {@code null}, wenn nicht angegeben. */
    static Long nummer(JsonNode angaben, String feld, String anzeigeName) {
        java.math.BigDecimal z = zahl(angaben, feld, anzeigeName);
        if (z == null) {
            return null;
        }
        try {
            return z.longValueExact();
        } catch (ArithmeticException e) {
            throw new KiWerkzeugFehler(anzeigeName.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + anzeigeName.substring(1) + " muss eine ganze Zahl sein.");
        }
    }

    /** Menge für die Anzeige: ohne überflüssige Nachkommastellen, deutsches Komma. */
    static String mengeText(java.math.BigDecimal m) {
        return m.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
