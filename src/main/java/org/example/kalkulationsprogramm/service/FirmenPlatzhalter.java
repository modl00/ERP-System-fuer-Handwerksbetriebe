package org.example.kalkulationsprogramm.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Firmeninformation;

/**
 * Platzhalter für die eigenen Firmendaten ({@code {{FIRMENNAME}}} usw.). Die Werte kommen aus den Firmeninformationen
 * (Einstellungen), so erscheinen Änderungen sofort in allen Dokumenten, die eine Vorlage mit diesen Platzhaltern benutzen.
 *
 * <p>Zusammengesetzte Platzhalter lassen leere Angaben weg: Fehlt die Telefonnummer, steht dort keine Zeile "Tel.: ".</p>
 */
public final class FirmenPlatzhalter {

    private FirmenPlatzhalter() {
    }

    /** Alle Firmen-Platzhalter mit ihrem Wert. Ohne Firmendaten sind alle Werte leer. */
    public static Map<String, String> werte(Firmeninformation f) {
        Map<String, String> m = new LinkedHashMap<>();
        String name = text(f == null ? null : f.getFirmenname());
        String strasse = text(f == null ? null : f.getStrasse());
        String plzOrt = zeile(f == null ? null : f.getPlz(), f == null ? null : f.getOrt());

        m.put("FIRMENNAME", name);
        m.put("FIRMENADRESSE", mehrzeilig(strasse, plzOrt));
        // Kleine Absenderzeile über dem Empfängerfeld (für Fensterumschläge)
        m.put("ABSENDERZEILE", Stream.of(name, strasse, plzOrt).filter(s -> !s.isEmpty()).collect(Collectors.joining(" | ")));
        m.put("FIRMENKONTAKT", mehrzeilig(
                beschriftet("Tel.: ", f == null ? null : f.getTelefon()),
                beschriftet("Fax: ", f == null ? null : f.getFax()),
                text(f == null ? null : f.getEmail()),
                text(f == null ? null : f.getWebsite())));
        m.put("FIRMENTELEFON", text(f == null ? null : f.getTelefon()));
        m.put("FIRMENFAX", text(f == null ? null : f.getFax()));
        m.put("FIRMENEMAIL", text(f == null ? null : f.getEmail()));
        m.put("FIRMENWEBSITE", text(f == null ? null : f.getWebsite()));
        m.put("FIRMENBANK", mehrzeilig(
                text(f == null ? null : f.getBankName()),
                beschriftet("IBAN: ", f == null ? null : f.getIban()),
                beschriftet("BIC: ", f == null ? null : f.getBic())));
        m.put("FIRMENIBAN", text(f == null ? null : f.getIban()));
        m.put("FIRMENBIC", text(f == null ? null : f.getBic()));
        m.put("FIRMENSTEUER", mehrzeilig(
                beschriftet("Steuernr.: ", f == null ? null : f.getSteuernummer()),
                beschriftet("USt-IdNr.: ", f == null ? null : f.getUstIdNr())));
        m.put("FIRMENREGISTER", mehrzeilig(
                zeile(f == null ? null : f.getHandelsregister(), f == null ? null : f.getHandelsregisterNummer()),
                beschriftet("Geschäftsführer: ", f == null ? null : f.getGeschaeftsfuehrer())));
        m.put("GESCHAEFTSFUEHRER", text(f == null ? null : f.getGeschaeftsfuehrer()));
        // Dreizeilige Fußzeile, mittig: Anschrift und Geschäftsführung, Register und Steuer, Bank
        m.put("FIRMENFUSS", mehrzeilig(
                verbunden(name, strasse, plzOrt, beschriftet("Geschäftsführer: ", f == null ? null : f.getGeschaeftsfuehrer())),
                verbunden(zeile(f == null ? null : f.getHandelsregister(), f == null ? null : f.getHandelsregisterNummer()),
                        beschriftet("USt-IdNr.: ", f == null ? null : f.getUstIdNr()),
                        text(f == null ? null : f.getUstIdNr()).isEmpty() ? beschriftet("Steuernr.: ", f == null ? null : f.getSteuernummer()) : ""),
                verbunden(text(f == null ? null : f.getBankName()),
                        beschriftet("IBAN: ", f == null ? null : f.getIban()),
                        beschriftet("BIC: ", f == null ? null : f.getBic()))));
        m.put("FUSSZEILE", text(f == null ? null : f.getFusszeileText()));
        return m;
    }

    /**
     * Besteht der Text nur aus Firmen-Platzhaltern (und Trennzeichen wie "·" oder "|")? Das sind die Blöcke von Briefkopf und Fußzeile:
     * Enthält das Briefpapier beides schon, lässt das PDF sie weg.
     */
    public static boolean istNurFirmenangaben(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String rest = text;
        boolean gefunden = false;
        for (String name : namen()) {
            String marke = "{{" + name + "}}";
            if (rest.contains(marke)) {
                gefunden = true;
                rest = rest.replace(marke, "");
            }
        }
        return gefunden && rest.replace("·", "").replace("|", "").isBlank();
    }

    /** Die Namen der Platzhalter (ohne Klammern), zum Beispiel für die Auswahl im Editor. */
    public static List<String> namen() {
        return List.copyOf(werte(null).keySet());
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static String beschriftet(String beschriftung, String wert) {
        String w = text(wert);
        return w.isEmpty() ? "" : beschriftung + w;
    }

    /** Zwei Angaben mit Leerzeichen verbunden, leere werden weggelassen ("97070 Würzburg"). */
    private static String zeile(String a, String b) {
        return Stream.of(text(a), text(b)).filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
    }

    /** Angaben mit senkrechtem Strich verbunden, leere werden weggelassen. */
    private static String verbunden(String... teile) {
        return Stream.of(teile).filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(" | "));
    }

    private static String mehrzeilig(String... zeilen) {
        return Stream.of(zeilen).filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining("\n"));
    }
}
