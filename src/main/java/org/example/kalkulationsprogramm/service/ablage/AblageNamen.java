package org.example.kalkulationsprogramm.service.ablage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Macht aus Namen der Anwendung (Projektnummer, Bauvorhaben, Originaldateiname ...) Ordner- und Dateinamen, die auf jedem Dateisystem
 * (Linux, Windows, Synology/SMB) gültig und gefahrlos sind: keine Pfadtrenner, keine Sonderzeichen, keine reservierten Windows-Namen,
 * begrenzte Länge, nie leer, nie ".." oder ".".
 */
final class AblageNamen {

    static final int MAX_ORDNER = 80;
    static final int MAX_DATEI = 120;

    private static final Set<String> RESERVIERT = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private AblageNamen() {
    }

    /** Ein Ordnername. Leer wird zu {@code ersatz}. */
    static String ordner(String text, String ersatz) {
        return begrenze(bereinige(text, ersatz), MAX_ORDNER, "");
    }

    /** Ein Dateiname mit Endung. Leer wird zu {@code ersatz} (ohne Endung, die kommt aus {@code endungVon}). */
    static String datei(String text, String ersatz, String endungVon) {
        String endung = endung(text);
        if (endung.isEmpty()) {
            endung = endung(endungVon);
        }
        String stamm = text == null ? "" : text;
        if (!endung.isEmpty() && stamm.toLowerCase(Locale.ROOT).endsWith(endung.toLowerCase(Locale.ROOT))) {
            stamm = stamm.substring(0, stamm.length() - endung.length());
        }
        return begrenze(bereinige(stamm, ersatz), MAX_DATEI - endung.length(), "") + endung;
    }

    /** Die Endung samt Punkt (".pdf"), nur Buchstaben und Ziffern bis 8 Zeichen, sonst leer. */
    static String endung(String name) {
        if (name == null) {
            return "";
        }
        int punkt = name.lastIndexOf('.');
        if (punkt <= 0 || punkt == name.length() - 1) {
            return "";
        }
        String e = name.substring(punkt + 1);
        return e.length() <= 8 && e.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 128) ? "." + e : "";
    }

    /**
     * Macht gleiche Namen im selben Ordner eindeutig, deterministisch nach der Reihenfolge der Eingabe: "Plan.pdf", "Plan (2).pdf", "Plan (3).pdf".
     * Groß- und Kleinschreibung zählt nicht (SMB und Windows unterscheiden sie nicht).
     *
     * @param pfade relative Pfade mit "/" als Trenner, in fester Reihenfolge
     */
    static List<String> eindeutig(List<String> pfade) {
        Map<String, Integer> gesehen = new HashMap<>();
        List<String> ergebnis = new ArrayList<>(pfade.size());
        for (String pfad : pfade) {
            String schluessel = pfad.toLowerCase(Locale.ROOT);
            int n = gesehen.merge(schluessel, 1, Integer::sum);
            if (n == 1) {
                ergebnis.add(pfad);
                continue;
            }
            int schraeg = pfad.lastIndexOf('/');
            String ordner = schraeg < 0 ? "" : pfad.substring(0, schraeg + 1);
            String name = pfad.substring(schraeg + 1);
            String endung = endung(name);
            String stamm = name.substring(0, name.length() - endung.length());
            String neu = ordner + stamm + " (" + n + ")" + endung;
            // Der neue Name könnte selbst schon vergeben sein ("Plan (2).pdf" existiert wirklich)
            while (gesehen.containsKey(neu.toLowerCase(Locale.ROOT))) {
                n = gesehen.merge(schluessel, 1, Integer::sum);
                neu = ordner + stamm + " (" + n + ")" + endung;
            }
            gesehen.put(neu.toLowerCase(Locale.ROOT), 1);
            ergebnis.add(neu);
        }
        return ergebnis;
    }

    private static String bereinige(String text, String ersatz) {
        String s = text == null ? "" : text;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '\\') {
                sb.append('-');   // Projektnummern wie 2026/10/00014 bleiben lesbar: 2026-10-00014
            } else {
                boolean verboten = c < 32 || c == 127 || ":*?\"<>|".indexOf(c) >= 0;
                sb.append(verboten ? ' ' : c);
            }
        }
        // Mehrfach-Leerzeichen zusammenfassen, Ränder säubern (Windows verträgt weder Leerzeichen noch Punkt am Ende)
        String t = sb.toString().replaceAll(" {2,}", " ").strip();
        t = t.replaceAll("^[. \\-]+|[. \\-]+$", "");
        if (t.isEmpty()) {
            t = ersatz;
        }
        String ohneEndung = t.contains(".") ? t.substring(0, t.indexOf('.')) : t;
        if (RESERVIERT.contains(ohneEndung.toUpperCase(Locale.ROOT))) {
            t = "_" + t;
        }
        return t;
    }

    private static String begrenze(String s, int max, String ende) {
        if (s.length() <= max) {
            return s + ende;
        }
        return s.substring(0, Math.max(1, max)).stripTrailing() + ende;
    }
}
