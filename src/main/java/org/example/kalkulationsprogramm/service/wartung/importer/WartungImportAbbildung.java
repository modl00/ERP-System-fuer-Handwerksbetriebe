package org.example.kalkulationsprogramm.service.wartung.importer;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto;

/**
 * Die Abbildungstabelle der Übernahme aus HandwerkPro: Welches alte Feld wird zu welchem Feld des einheitlichen Katalogs, und welcher alte Wert zu welchem neuen
 * Wert (Konzept: docs/WARTUNG_DATENUEBERNAHME_KONZEPT.md). Reine Funktionen ohne Datenbankzugriff, damit sie sich mit den echten Werten vollständig testen lassen.
 * Es wird nie geraten: Was nicht eindeutig zuzuordnen ist, kommt als {@code NEU_IN_LISTE} oder {@code FREITEXT} in die Vorschau und braucht eine Entscheidung.
 */
public final class WartungImportAbbildung {

    public static final String KEINE_ANGABE = "keine Angabe";

    public static final String F_STANDORT = "Standort / Lage";
    public static final String F_HERSTELLER = "Hersteller";
    public static final String F_ZULASSUNG = "Zulassungsnummer";
    public static final String F_BAUJAHR = "Baujahr";
    public static final String F_ANSCHLAG = "Anschlag";
    public static final String F_WERKSTOFF = "Werkstoff";
    public static final String F_TORART = "Tür- / Torart";
    public static final String F_TYP = "Typ / Modell";
    public static final String F_FEUERWIDERSTAND = "Feuerwiderstand";
    public static final String F_RAUCHSCHUTZ = "Rauchschutz (RS)";
    public static final String F_FSA = "Feststellanlage Hersteller / Typ";
    public static final String F_MELDER = "Rauchmelder Typ";
    public static final String F_MELDER_JAHR = "Rauchmelder Einbaujahr";
    public static final String F_GLASART = "Glasart";

    /** Hersteller der gepflegten Liste (V921); längere Namen zuerst, damit "Wires Bauelemente" vor "Wires" greift. */
    static final List<String> HERSTELLER = List.of("Wires Bauelemente", "Schüco", "Hörmann", "Hoba", "Geze", "Dorma", "Brandis", "Riexinger");

    private static final Set<String> KEINE_ANGABE_VARIANTEN = Set.of("", "-", "–", "--", "keine angabe", "keine angaben", "keine angbae", "keine anagbe", "unbekannt", "n.b.", "nb",
            "k.a.", "ka", "nicht bekannt", "keine", "?", "n/a", "na");

    /** Ein neuer Wert für ein Katalogfeld. {@code aktion} siehe {@link WartungImportDto}. */
    public record Ergebnis(String feld, String wert, String aktion) { }

    private static final Map<String, String> ALTE_FELDER = Map.ofEntries(
            Map.entry("standort / lage", F_STANDORT), Map.entry("standort / lage der tür", F_STANDORT),
            Map.entry("hersteller", F_HERSTELLER), Map.entry("hersteller / fabrikat", F_HERSTELLER), Map.entry("hersteller der tür", F_HERSTELLER),
            Map.entry("zulassungsnummer", F_ZULASSUNG),
            Map.entry("baujahr", F_BAUJAHR),
            Map.entry("anschlag", F_ANSCHLAG),
            Map.entry("feuerwiederstand / rauchschutzklasse", F_FEUERWIDERSTAND), Map.entry("feuerwiderstand / rauchschutzklasse", F_FEUERWIDERSTAND),
            Map.entry("rauchschutzklasse", F_FEUERWIDERSTAND),
            Map.entry("feststellvorrichtung hersteller/typ", F_FSA), Map.entry("feststellanlage / hersteller", F_FSA), Map.entry("festellvorrichtung", F_FSA),
            Map.entry("feststellanlagen-typ", F_FSA),
            Map.entry("rauchmelder hersteller/typ", F_MELDER), Map.entry("branderkennungsteil", F_MELDER),
            Map.entry("rauchmelder einbaudatum", F_MELDER_JAHR), Map.entry("rauchmelder einbaujahr", F_MELDER_JAHR),
            Map.entry("verglasung / glasart", F_GLASART));

    private static final Set<String> FLUEGEL_FELDER = Set.of("anzahl türflügel / torart", "anzahl türflügel");

    private WartungImportAbbildung() { }

    /** Kleinschreibung, Leerzeichen zusammengefasst, Schreibunterschiede der Bezeichnung (doppelte Leerzeichen am Ende) egal. */
    public static String norm(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.GERMAN);
    }

    public static boolean istKeineAngabe(String wert) {
        return KEINE_ANGABE_VARIANTEN.contains(norm(wert));
    }

    /** Das Katalogfeld zu einer alten Bezeichnung; leer, wenn das alte Feld keine Entsprechung hat (dann bleibt es ein eigenes Feld des Protokolls). */
    public static String katalogFeld(String altLabel) {
        return ALTE_FELDER.get(norm(altLabel));
    }

    public static boolean istFluegelFeld(String altLabel) {
        return FLUEGEL_FELDER.contains(norm(altLabel));
    }

    // ── Werte ───────────────────────────────────────────────────────────────

    /**
     * Bildet einen alten Wert eines alten Feldes auf einen oder mehrere neue Werte ab. {@code altLabel} muss {@link #katalogFeld(String)} kennen (sonst leere Liste),
     * die Flügel-Felder laufen über {@link #fluegel(String)}.
     */
    public static List<Ergebnis> abbilden(String altLabel, String altWert, Set<String> listeHersteller) {
        String feld = katalogFeld(altLabel);
        if (feld == null) {
            return List.of();
        }
        String w = altWert == null ? "" : altWert.strip();
        switch (feld) {
            case F_STANDORT, F_ZULASSUNG -> {
                return List.of(new Ergebnis(feld, istKeineAngabe(w) ? "" : w, istKeineAngabe(w) ? WartungImportDto.LEER : WartungImportDto.FREITEXT));
            }
            case F_BAUJAHR, F_MELDER_JAHR -> {
                return List.of(jahr(feld, w));
            }
            case F_ANSCHLAG -> {
                return List.of(anschlag(w));
            }
            case F_HERSTELLER -> {
                return hersteller(w, listeHersteller);
            }
            case F_FEUERWIDERSTAND -> {
                return feuerwiderstand(w);
            }
            default -> {
                // Auswahlfelder ohne feste Regel (Feststellanlage, Melder, Glasart): keine Angabe oder der Wert selbst, der bei Bedarf in die Liste aufgenommen wird
                return List.of(auswahl(feld, w));
            }
        }
    }

    private static Ergebnis auswahl(String feld, String w) {
        return istKeineAngabe(w) ? new Ergebnis(feld, KEINE_ANGABE, WartungImportDto.LEER) : new Ergebnis(feld, w, WartungImportDto.NEU_IN_LISTE);
    }

    private static final Pattern VIERSTELLIG = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

    static Ergebnis jahr(String feld, String w) {
        if (istKeineAngabe(w)) {
            return new Ergebnis(feld, "", WartungImportDto.LEER);
        }
        Matcher m = VIERSTELLIG.matcher(w);
        int max = Year.now().getValue() + 1;
        if (m.find()) {
            int j = Integer.parseInt(m.group(1));
            if (j >= 1900 && j <= max) {
                return new Ergebnis(feld, String.valueOf(j), WartungImportDto.ZUGEORDNET);
            }
        }
        return new Ergebnis(feld, "", WartungImportDto.UNGUELTIG);
    }

    static Ergebnis anschlag(String w) {
        String k = norm(w).replace(" ", "");
        if (istKeineAngabe(w) || k.equals("din")) {
            return new Ergebnis(F_ANSCHLAG, KEINE_ANGABE, WartungImportDto.LEER);
        }
        return switch (k) {
            case "dinl", "dinlinks", "links", "l" -> new Ergebnis(F_ANSCHLAG, "DIN links", WartungImportDto.ZUGEORDNET);
            case "dinr", "dinrechts", "rechts", "r" -> new Ergebnis(F_ANSCHLAG, "DIN rechts", WartungImportDto.ZUGEORDNET);
            default -> k.contains("schiebe") ? new Ergebnis(F_ANSCHLAG, "Schiebetor", WartungImportDto.ZUGEORDNET) : new Ergebnis(F_ANSCHLAG, w, WartungImportDto.NEU_IN_LISTE);
        };
    }

    /** Hersteller aus der Liste; "Hörmann H6" wird Hersteller "Hörmann" und Typ "H6". Unbekannte Hersteller kommen als NEU_IN_LISTE. */
    static List<Ergebnis> hersteller(String w, Set<String> liste) {
        if (istKeineAngabe(w)) {
            return List.of(new Ergebnis(F_HERSTELLER, KEINE_ANGABE, WartungImportDto.LEER));
        }
        String k = norm(w);
        for (String h : HERSTELLER) {
            String hk = norm(h);
            if (k.equals(hk) || k.startsWith(hk + " ")) {
                List<Ergebnis> r = new ArrayList<>();
                r.add(new Ergebnis(F_HERSTELLER, h, WartungImportDto.ZUGEORDNET));
                String rest = w.strip().substring(h.length()).strip();
                if (!rest.isEmpty()) {
                    r.add(new Ergebnis(F_TYP, rest, WartungImportDto.FREITEXT));
                }
                return r;
            }
        }
        if (liste != null) {
            for (String h : liste) {
                if (norm(h).equals(k)) {
                    return List.of(new Ergebnis(F_HERSTELLER, h, WartungImportDto.ZUGEORDNET));
                }
            }
        }
        return List.of(new Ergebnis(F_HERSTELLER, w.strip(), WartungImportDto.NEU_IN_LISTE));
    }

    private static final Pattern KLASSE = Pattern.compile("(?<![A-Za-z])([TF])\\s*-?\\s*(30|60|90)(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RS = Pattern.compile("(?<![A-Za-z])(RS|RST|RS-Tür)(?![A-Za-z])", Pattern.CASE_INSENSITIVE);

    /** "T30 RS" wird Feuerwiderstand "T30" plus Rauchschutz "Ja"; "keine Angabe" bleibt "keine Angabe" (Rauchschutz leer); Unbekanntes wie "DST" kommt als NEU_IN_LISTE. */
    static List<Ergebnis> feuerwiderstand(String w) {
        if (istKeineAngabe(w)) {
            return List.of(new Ergebnis(F_FEUERWIDERSTAND, KEINE_ANGABE, WartungImportDto.LEER));
        }
        List<Ergebnis> r = new ArrayList<>();
        Matcher m = KLASSE.matcher(w);
        boolean rs = RS.matcher(w).find() || norm(w).contains("rauchschutz");
        if (m.find()) {
            r.add(new Ergebnis(F_FEUERWIDERSTAND, m.group(1).toUpperCase(Locale.ROOT) + m.group(2), WartungImportDto.ZUGEORDNET));
            if (rs) {
                r.add(new Ergebnis(F_RAUCHSCHUTZ, "Ja", WartungImportDto.ZUGEORDNET));
            }
        } else if (rs) {
            r.add(new Ergebnis(F_FEUERWIDERSTAND, KEINE_ANGABE, WartungImportDto.LEER));
            r.add(new Ergebnis(F_RAUCHSCHUTZ, "Ja", WartungImportDto.ZUGEORDNET));
        } else {
            r.add(new Ergebnis(F_FEUERWIDERSTAND, w.strip(), WartungImportDto.NEU_IN_LISTE));
        }
        return r;
    }

    // ── Flügelzahl / Torart ─────────────────────────────────────────────────

    /** Was aus dem alten Feld "Anzahl Türflügel / Torart" herausgelöst wird: Bauart-Schlüssel (einflg, zweiflg, schiebe) und die Katalogfelder Werkstoff, Torart, Anschlag. */
    public record Fluegel(String bauart, List<Ergebnis> felder) { }

    public static Fluegel fluegel(String altWert) {
        String w = altWert == null ? "" : altWert.strip();
        List<Ergebnis> r = new ArrayList<>();
        if (istKeineAngabe(w)) {
            return new Fluegel(null, r);
        }
        String k = norm(w);
        String bauart = null;
        Matcher zahl = Pattern.compile("^([12])(?!\\d)").matcher(k);
        if (k.contains("schiebe")) {
            bauart = "schiebe";
        } else if (zahl.find()) {
            bauart = zahl.group(1).equals("2") ? "zweiflg" : "einflg";
        } else if (k.contains("zweiflüg") || k.contains("2-flüg") || k.contains("2 flüg")) {
            bauart = "zweiflg";
        } else if (k.contains("einflüg") || k.contains("1-flüg") || k.contains("1 flüg")) {
            bauart = "einflg";
        }
        if (k.contains("stahl")) {
            r.add(new Ergebnis(F_WERKSTOFF, "Stahl", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("alu")) {
            r.add(new Ergebnis(F_WERKSTOFF, "Aluminium", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("holz") && k.contains("glas")) {
            r.add(new Ergebnis(F_WERKSTOFF, "Holz / Glas", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("holz")) {
            r.add(new Ergebnis(F_WERKSTOFF, "Holz", WartungImportDto.ZUGEORDNET));
        }
        if (k.contains("brandschutztor")) {
            r.add(new Ergebnis(F_TORART, "Brandschutztor", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("schiebetor")) {
            r.add(new Ergebnis(F_TORART, "Schiebetor", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("schiebetür") || k.contains("schiebetuer")) {
            r.add(new Ergebnis(F_TORART, "Schiebetür", WartungImportDto.ZUGEORDNET));
        } else if (k.contains("drehtür") || k.contains("drehtuer")) {
            r.add(new Ergebnis(F_TORART, "Drehtür", WartungImportDto.ZUGEORDNET));
        }
        String nurAnschlag = k.replace(" ", "");
        if (nurAnschlag.contains("dinl")) {
            r.add(new Ergebnis(F_ANSCHLAG, "DIN links", WartungImportDto.ZUGEORDNET));
        } else if (nurAnschlag.contains("dinr")) {
            r.add(new Ergebnis(F_ANSCHLAG, "DIN rechts", WartungImportDto.ZUGEORDNET));
        }
        return new Fluegel(bauart, r);
    }

    // ── Protokollart ────────────────────────────────────────────────────────

    /** Alte Protokollarten sind schon die neuen Namen; alles andere bleibt unverändert (Vorschau weist darauf hin). */
    public static String protokollart(String alt) {
        String k = norm(alt);
        return switch (k) {
            case "wartungsprotokoll" -> "Wartungsprotokoll";
            case "instandhaltungsprotokoll" -> "Instandhaltungsprotokoll";
            case "prüfprotokoll", "pruefprotokoll" -> "Prüfprotokoll";
            case "inspektionsprotokoll" -> "Inspektionsprotokoll";
            default -> null;
        };
    }
}
