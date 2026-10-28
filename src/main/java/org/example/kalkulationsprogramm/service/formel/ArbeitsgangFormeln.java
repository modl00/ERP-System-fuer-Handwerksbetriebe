package org.example.kalkulationsprogramm.service.formel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Arbeitsgang mit Formel (#216): bis zu {@value #MAX_FELDER} Eingabefelder (zum Beispiel Durchmesser, Länge, Vorschub) mit Standardwert und eine Formel für die Bearbeitungszeit
 * je Einheit und optional eine für die Rüstzeit, beides in Minuten. Aus den Werten, die man an der Position der Leistung einträgt, entstehen die Zeiten; alles Weitere
 * (Losgröße, Stundensatz, Maschine) rechnet wie bisher mit diesen Zeiten.
 */
public final class ArbeitsgangFormeln {

    public static final int MAX_FELDER = 8;
    static final BigDecimal MAX_MINUTEN = new BigDecimal("100000");
    private static final BigDecimal MAX_WERT = new BigDecimal("1000000000");
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,19}");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Ein Eingabefeld der Formel: {@code name} wie in der Formel (zum Beispiel V1), {@code bezeichnung} für die Eingabemaske. */
    public record Feld(String name, String bezeichnung, String einheit, BigDecimal standard) {
    }

    public record Formel(List<Feld> felder, String bearbeitung, String ruest) {
    }

    /** Ergebnis in Minuten, auf zwei Stellen gerundet. */
    public record Zeiten(BigDecimal ruestMinuten, BigDecimal bearbeitungMinuten) {
    }

    private ArbeitsgangFormeln() {
    }

    /** Hat der Arbeitsgang eine Formel für die Bearbeitungszeit? */
    public static boolean hat(Arbeitsgang g) {
        return g != null && g.getFormelBearbeitung() != null && !g.getFormelBearbeitung().isBlank();
    }

    public static Formel lese(Arbeitsgang g) {
        if (g == null) {
            return new Formel(List.of(), null, null);
        }
        List<Feld> felder = List.of();
        if (g.getFormelFelderJson() != null && !g.getFormelFelderJson().isBlank()) {
            try {
                felder = JSON.readValue(g.getFormelFelderJson(), new TypeReference<List<Feld>>() {
                });
            } catch (java.io.IOException e) {
                felder = List.of();
            }
        }
        return new Formel(felder, g.getFormelBearbeitung(), g.getFormelRuest());
    }

    /**
     * Prüft die Eingaben und gibt sie bereinigt zurück. Fehler kommen als {@link FormelException} im Klartext. Eine leere Bearbeitungsformel entfernt die Formel
     * samt Feldern.
     */
    public static Formel pruefe(Formel f) {
        String bearbeitung = leerZuNull(f == null ? null : f.bearbeitung());
        String ruest = leerZuNull(f == null ? null : f.ruest());
        if (bearbeitung == null && ruest == null) {
            return new Formel(List.of(), null, null);
        }
        if (bearbeitung == null) {
            throw new FormelException("Bitte eine Formel für die Bearbeitungszeit eintragen (die Rüstzeit-Formel ist nur zusätzlich möglich).");
        }
        Set<String> namen = new LinkedHashSet<>();
        List<Feld> felder = pruefeFelder(f.felder(), namen, Set.of());
        FormelRechner.pruefe(bearbeitung, namen);
        if (ruest != null) {
            FormelRechner.pruefe(ruest, namen);
        }
        return new Formel(felder, bearbeitung, ruest);
    }

    /**
     * Prüft die Eingabefelder (Namen, Länge, Standardwerte, Anzahl) und gibt sie bereinigt zurück; die Namen in Kleinbuchstaben landen in {@code namenOut}.
     * {@code zusaetzlichReserviert} sind weitere Namen, die nicht als Feld erlaubt sind (zum Beispiel eingebaute Werte).
     */
    public static List<Feld> pruefeFelder(List<Feld> eingabe, Set<String> namenOut, Set<String> zusaetzlichReserviert) {
        Set<String> namen = new LinkedHashSet<>();
        List<Feld> felder = new ArrayList<>();
        if (eingabe != null) {
            for (Feld x : eingabe) {
                if (x == null || x.name() == null || x.name().isBlank()) {
                    continue;
                }
                String name = x.name().strip();
                if (!NAME.matcher(name).matches()) {
                    throw new FormelException("Der Feldname „" + name + "“ ist ungültig: Buchstaben und Ziffern, mit einem Buchstaben am Anfang, höchstens 20 Zeichen (zum Beispiel V1 oder Durchmesser).");
                }
                if (name.equalsIgnoreCase("pi") || name.equalsIgnoreCase("min") || name.equalsIgnoreCase("max") || name.equalsIgnoreCase("runden") || name.equalsIgnoreCase("wenn")
                        || zusaetzlichReserviert.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new FormelException("Der Name „" + name + "“ ist für die Formel reserviert. Bitte einen anderen wählen.");
                }
                if (!namen.add(name.toLowerCase(Locale.ROOT))) {
                    throw new FormelException("Der Feldname „" + name + "“ kommt doppelt vor.");
                }
                String bez = x.bezeichnung() == null ? "" : x.bezeichnung().strip();
                String einheit = x.einheit() == null ? "" : x.einheit().strip();
                if (bez.length() > 60 || einheit.length() > 10) {
                    throw new FormelException("Bezeichnung (höchstens 60 Zeichen) oder Einheit (höchstens 10 Zeichen) sind zu lang.");
                }
                BigDecimal standard = x.standard();
                if (standard != null && standard.abs().compareTo(MAX_WERT) > 0) {
                    throw new FormelException("Der Standardwert von „" + name + "“ ist unrealistisch groß.");
                }
                felder.add(new Feld(name, bez.isEmpty() ? name : bez, einheit, standard));
            }
        }
        if (felder.size() > MAX_FELDER) {
            throw new FormelException("Es sind höchstens " + MAX_FELDER + " Eingabefelder möglich.");
        }
        namenOut.addAll(namen);
        return felder;
    }

    /** Das Ergebnis als JSON für die Spalte am Arbeitsgang. */
    public static String felderAlsJson(List<Feld> felder) {
        try {
            return felder == null || felder.isEmpty() ? null : JSON.writeValueAsString(felder);
        } catch (java.io.IOException e) {
            throw new FormelException("Die Eingabefelder konnten nicht gespeichert werden.");
        }
    }

    /**
     * Rechnet die Zeiten. Fehlende Werte nehmen den Standardwert des Feldes; hat ein Feld weder Wert noch Standard, kommt ein Klartext. Das Ergebnis darf nicht
     * negativ sein und höchstens 100.000 Minuten betragen.
     */
    public static Zeiten berechne(Formel f, Map<String, BigDecimal> eingabe) {
        Map<String, BigDecimal> werte = wirksameWerte(f, eingabe);
        for (Feld x : f.felder()) {
            if (werte.get(x.name().toLowerCase(Locale.ROOT)) == null) {
                throw new FormelException("Bitte einen Wert für „" + x.bezeichnung() + "“ eintragen.");
            }
        }
        BigDecimal bearbeitung = minuten(FormelRechner.rechne(f.bearbeitung(), werte), "Die Bearbeitungszeit");
        BigDecimal ruest = f.ruest() == null || f.ruest().isBlank() ? null : minuten(FormelRechner.rechne(f.ruest(), werte), "Die Rüstzeit");
        return new Zeiten(ruest, bearbeitung);
    }

    /** Die Werte, mit denen gerechnet wird: Eingabe, sonst Standardwert (Schlüssel in Kleinbuchstaben). */
    public static Map<String, BigDecimal> wirksameWerte(Formel f, Map<String, BigDecimal> eingabe) {
        Map<String, BigDecimal> klein = new HashMap<>();
        if (eingabe != null) {
            eingabe.forEach((k, v) -> {
                if (k != null && v != null) {
                    klein.put(k.toLowerCase(Locale.ROOT), v);
                }
            });
        }
        Map<String, BigDecimal> ergebnis = new LinkedHashMap<>();
        for (Feld x : f.felder()) {
            String key = x.name().toLowerCase(Locale.ROOT);
            BigDecimal wert = klein.getOrDefault(key, x.standard());
            if (wert != null && wert.abs().compareTo(MAX_WERT) > 0) {
                throw new FormelException("Der Wert für „" + x.bezeichnung() + "“ ist unrealistisch groß.");
            }
            ergebnis.put(key, wert);
        }
        return ergebnis;
    }

    public static String werteAlsJson(Map<String, BigDecimal> werte) {
        try {
            return JSON.writeValueAsString(new LinkedHashMap<>(werte));
        } catch (java.io.IOException e) {
            throw new FormelException("Die Werte konnten nicht gespeichert werden.");
        }
    }

    public static Map<String, BigDecimal> werteAusJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, BigDecimal>>() {
            });
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static BigDecimal minuten(BigDecimal wert, String was) {
        if (wert.signum() < 0) {
            throw new FormelException(was + " wird negativ. Bitte die Werte prüfen.");
        }
        if (wert.compareTo(MAX_MINUTEN) > 0) {
            throw new FormelException(was + " ist größer als 100.000 Minuten. Bitte die Werte prüfen.");
        }
        return wert.setScale(2, RoundingMode.HALF_UP);
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
