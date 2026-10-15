package org.example.kalkulationsprogramm.service.formel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ein kleiner, sicherer Rechner für Formeln in der Kalkulation, zum Beispiel {@code (V2*V3)/(V5*(V4/(3,14*V1/1000)))+V6}.
 *
 * <p>Die Formel wird <b>nie als Programmcode ausgeführt</b>, sondern von diesem Rechner Zeichen für Zeichen gelesen. Erlaubt sind Zahlen (Komma oder Punkt),
 * Namen von Eingabefeldern, {@code + - * / ^} (Potenz nur mit ganzer Hochzahl von -10 bis 10), Klammern, die Vergleiche {@code < > <= >= = <>}, die Konstante
 * {@code pi} und die Funktionen {@code min(a;b)}, {@code max(a;b)}, {@code runden(wert;stellen)} und {@code wenn(bedingung;dann;sonst)}. Mehrere Werte einer Funktion
 * werden mit <b>Semikolon</b> getrennt, weil das Komma das Dezimalkomma ist. Groß- und Kleinschreibung ist egal.</p>
 *
 * <p>Fehler (unbekanntes Feld, Division durch null, zu große Zahlen, Schreibfehler) kommen als {@link FormelException} mit Klartext. Formeln sind auf
 * 500 Zeichen und 12 Klammerebenen begrenzt, damit niemand den Rechner mit absurden Eingaben festsetzt.</p>
 */
public final class FormelRechner {

    public static final int MAX_LAENGE = 500;
    private static final int MAX_TIEFE = 12;
    private static final BigDecimal MAX_BETRAG = new BigDecimal("1000000000000");   // 10^12
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846");

    private final String text;
    private final Map<String, BigDecimal> werte;
    private final Set<String> gelesen = new LinkedHashSet<>();
    private int pos;
    private int tiefe;
    /** Beim reinen Prüfen wird nicht gerechnet, nur gelesen (kein Fehler bei Division durch null). */
    private final boolean nurPruefen;

    private FormelRechner(String text, Map<String, BigDecimal> werte, boolean nurPruefen) {
        this.text = text;
        this.werte = werte;
        this.nurPruefen = nurPruefen;
    }

    /** Berechnet die Formel mit den Werten der Eingabefelder (Schlüssel in Kleinbuchstaben oder beliebiger Schreibweise). */
    public static BigDecimal rechne(String formel, Map<String, BigDecimal> werte) {
        Map<String, BigDecimal> klein = new java.util.HashMap<>();
        werte.forEach((k, v) -> klein.put(k.toLowerCase(Locale.ROOT), v));
        FormelRechner r = new FormelRechner(vorbereite(formel), klein, false);
        BigDecimal ergebnis = r.lies();
        return ergebnis.stripTrailingZeros().scale() < 0 ? ergebnis.setScale(0) : ergebnis.stripTrailingZeros();
    }

    /**
     * Prüft die Schreibweise und dass nur bekannte Felder vorkommen, ohne zu rechnen.
     *
     * @return die Namen der Felder, die in der Formel vorkommen
     */
    public static Set<String> pruefe(String formel, Set<String> bekannteFelder) {
        Map<String, BigDecimal> platzhalter = new java.util.HashMap<>();
        for (String f : bekannteFelder) {
            platzhalter.put(f.toLowerCase(Locale.ROOT), BigDecimal.ONE);
        }
        FormelRechner r = new FormelRechner(vorbereite(formel), platzhalter, true);
        r.lies();
        return r.gelesen;
    }

    private static String vorbereite(String formel) {
        if (formel == null || formel.isBlank()) {
            throw new FormelException("Die Formel ist leer.");
        }
        if (formel.length() > MAX_LAENGE) {
            throw new FormelException("Die Formel ist zu lang (höchstens " + MAX_LAENGE + " Zeichen).");
        }
        return formel.strip();
    }

    // ------------------------------------------------------------------ Lesen

    private BigDecimal lies() {
        BigDecimal wert = vergleich();
        ueberspringeLeer();
        if (pos < text.length()) {
            throw fehlerBei("Hier stimmt etwas nicht: „" + text.charAt(pos) + "“");
        }
        return wert;
    }

    private BigDecimal vergleich() {
        BigDecimal links = summe();
        ueberspringeLeer();
        String op = null;
        if (pos < text.length()) {
            if (text.startsWith("<=", pos) || text.startsWith(">=", pos) || text.startsWith("<>", pos)) {
                op = text.substring(pos, pos + 2);
            } else if ("<>=".indexOf(text.charAt(pos)) >= 0) {
                op = String.valueOf(text.charAt(pos));
            }
        }
        if (op == null) {
            return links;
        }
        pos += op.length();
        BigDecimal rechts = summe();
        int c = links.compareTo(rechts);
        boolean ok = switch (op) {
            case "<" -> c < 0;
            case ">" -> c > 0;
            case "<=" -> c <= 0;
            case ">=" -> c >= 0;
            case "=" -> c == 0;
            default -> c != 0;
        };
        return ok ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private BigDecimal summe() {
        BigDecimal wert = produkt();
        while (true) {
            ueberspringeLeer();
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                char op = text.charAt(pos++);
                BigDecimal rechts = produkt();
                wert = begrenze(op == '+' ? wert.add(rechts, MC) : wert.subtract(rechts, MC));
            } else {
                return wert;
            }
        }
    }

    private BigDecimal produkt() {
        BigDecimal wert = potenz();
        while (true) {
            ueberspringeLeer();
            if (pos < text.length() && (text.charAt(pos) == '*' || text.charAt(pos) == '/' || text.charAt(pos) == '×' || text.charAt(pos) == '·')) {
                char op = text.charAt(pos++);
                BigDecimal rechts = potenz();
                if (op == '/') {
                    if (!nurPruefen && rechts.signum() == 0) {
                        throw new FormelException("Division durch null: Ein Eingabefeld im Nenner ist 0. Bitte einen Wert größer als 0 eintragen.");
                    }
                    wert = nurPruefen && rechts.signum() == 0 ? wert : begrenze(wert.divide(rechts, MC));
                } else {
                    wert = begrenze(wert.multiply(rechts, MC));
                }
            } else {
                return wert;
            }
        }
    }

    private BigDecimal potenz() {
        BigDecimal basis = vorzeichen();
        ueberspringeLeer();
        if (pos < text.length() && text.charAt(pos) == '^') {
            pos++;
            BigDecimal hoch = vorzeichen();
            if (hoch.stripTrailingZeros().scale() > 0 || hoch.abs().compareTo(BigDecimal.TEN) > 0) {
                throw new FormelException("Die Hochzahl muss eine ganze Zahl von -10 bis 10 sein.");
            }
            int n = hoch.intValueExact();
            if (n >= 0) {
                return begrenze(basis.pow(n, MC));
            }
            if (basis.signum() == 0 && !nurPruefen) {
                throw new FormelException("Division durch null in einer Potenz.");
            }
            return basis.signum() == 0 ? BigDecimal.ONE : begrenze(BigDecimal.ONE.divide(basis.pow(-n, MC), MC));
        }
        return basis;
    }

    private BigDecimal vorzeichen() {
        ueberspringeLeer();
        if (pos < text.length() && text.charAt(pos) == '-') {
            pos++;
            return vorzeichen().negate();
        }
        if (pos < text.length() && text.charAt(pos) == '+') {
            pos++;
            return vorzeichen();
        }
        return grundwert();
    }

    private BigDecimal grundwert() {
        ueberspringeLeer();
        if (pos >= text.length()) {
            throw fehlerBei("Die Formel ist nicht zu Ende: Es fehlt noch eine Zahl oder ein Feld");
        }
        char c = text.charAt(pos);
        if (c == '(') {
            pos++;
            betrete();
            BigDecimal wert = vergleich();
            ueberspringeLeer();
            erwarte(')', "Es fehlt eine schließende Klammer");
            tiefe--;
            return wert;
        }
        if (Character.isDigit(c) || c == '.' || c == ',') {
            return zahl();
        }
        if (Character.isLetter(c) || c == '_') {
            return name();
        }
        throw fehlerBei("Hier stimmt etwas nicht: „" + c + "“");
    }

    private BigDecimal zahl() {
        int start = pos;
        boolean trenner = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if ((c == ',' || c == '.') && !trenner) {
                trenner = true;
                pos++;
            } else {
                break;
            }
        }
        String s = text.substring(start, pos).replace(',', '.');
        if (s.equals(".") || s.isEmpty()) {
            throw fehlerBei("Eine Zahl wird erwartet");
        }
        return begrenze(new BigDecimal(s));
    }

    private BigDecimal name() {
        int start = pos;
        while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
            pos++;
        }
        String name = text.substring(start, pos).toLowerCase(Locale.ROOT);
        ueberspringeLeer();
        if (pos < text.length() && text.charAt(pos) == '(') {
            pos++;
            return funktion(name);
        }
        if (name.equals("pi")) {
            return PI;
        }
        if (!werte.containsKey(name)) {
            throw new FormelException("Das Feld „" + text.substring(start, start + name.length()) + "“ gibt es nicht. Erlaubt sind nur die angelegten Eingabefelder.");
        }
        gelesen.add(name);
        BigDecimal w = werte.get(name);
        if (w == null) {
            throw new FormelException("Für das Feld „" + name.toUpperCase(Locale.ROOT) + "“ fehlt ein Wert.");
        }
        return w;
    }

    private BigDecimal funktion(String name) {
        betrete();
        List<BigDecimal> args = new ArrayList<>();
        ueberspringeLeer();
        if (pos < text.length() && text.charAt(pos) == ')') {
            throw fehlerBei("Der Funktion „" + name + "“ fehlen die Werte");
        }
        while (true) {
            args.add(vergleich());
            ueberspringeLeer();
            if (pos < text.length() && (text.charAt(pos) == ';')) {
                pos++;
                continue;
            }
            break;
        }
        erwarte(')', "Es fehlt eine schließende Klammer bei „" + name + "“");
        tiefe--;
        return switch (name) {
            case "min" -> genau(name, args, 2).get(0).min(args.get(1));
            case "max" -> genau(name, args, 2).get(0).max(args.get(1));
            case "runden" -> {
                genau(name, args, 2);
                BigDecimal st = args.get(1);
                if (st.stripTrailingZeros().scale() > 0 || st.signum() < 0 || st.compareTo(new BigDecimal("6")) > 0) {
                    throw new FormelException("Bei „runden“ muss die Zahl der Stellen eine ganze Zahl von 0 bis 6 sein.");
                }
                yield args.get(0).setScale(st.intValueExact(), RoundingMode.HALF_UP);
            }
            case "wenn" -> {
                genau(name, args, 3);
                yield args.get(0).signum() != 0 ? args.get(1) : args.get(2);
            }
            default -> throw new FormelException("Die Funktion „" + name + "“ gibt es nicht. Erlaubt: min, max, runden, wenn (Argumente mit Semikolon trennen).");
        };
    }

    private static List<BigDecimal> genau(String name, List<BigDecimal> args, int n) {
        if (args.size() != n) {
            throw new FormelException("„" + name + "“ braucht genau " + n + " Werte, getrennt mit Semikolon.");
        }
        return args;
    }

    // ------------------------------------------------------------------ Hilfen

    private void betrete() {
        if (++tiefe > MAX_TIEFE) {
            throw new FormelException("Zu viele Klammern ineinander (höchstens " + MAX_TIEFE + ").");
        }
    }

    private void erwarte(char c, String meldung) {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw fehlerBei(meldung);
        }
        pos++;
    }

    private void ueberspringeLeer() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private static BigDecimal begrenze(BigDecimal w) {
        if (w.abs().compareTo(MAX_BETRAG) > 0) {
            throw new FormelException("Das Ergebnis wird unrealistisch groß. Bitte die Werte prüfen.");
        }
        return w;
    }

    private FormelException fehlerBei(String meldung) {
        return new FormelException(meldung + " (an Stelle " + (pos + 1) + ").");
    }
}
