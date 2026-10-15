package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Das Wissen der KI-Hilfe über das Programm, unabhängig vom Quellcode: die Datei {@code ki/wissen.md} (erzeugt von {@code tools/ki-wissen.mjs} aus den Seiten der Oberfläche und
 * ausgewählten Anleitungen, liegt im Jar) und {@code ki/grenzen.md} (von Hand gepflegt: was das Programm bewusst nicht kann).
 *
 * <p>Zur Frage werden die <b>passenden Abschnitte</b> mit einer einfachen Stichwortsuche gewählt (Gewicht nach Seltenheit des Worts, Treffer im Titel zählen mehr), damit auch kleine
 * Modelle mit kurzem Gedächtnis nur Relevantes bekommen. Ohne Treffer kommt nichts; die KI soll dann ehrlich „finde ich nicht“ sagen.</p>
 */
@Service
public class KiWissenService {

    private static final Logger log = LoggerFactory.getLogger(KiWissenService.class);

    static final int STAMM_LAENGE = 5;
    private static final Pattern NICHT_BUCHSTABE = Pattern.compile("[^\\p{L}]+");
    private static final Set<String> FUELLWOERTER = new HashSet<>(java.util.List.of("wie", "wo", "was", "wer", "wann", "warum", "kann", "kannst", "man", "ich", "mir", "mich", "wir", "der", "die", "das", "den", "dem", "des",
            "ein", "eine", "einen", "einem", "einer", "und", "oder", "aber", "ist", "sind", "wird", "werden", "gibt", "gibts", "es", "sich", "auch", "noch", "nur", "nicht", "kein", "keine", "zu", "zum",
            "zur", "in", "im", "ins", "auf", "fur", "für", "mit", "von", "vom", "bei", "aus", "nach", "an", "am", "als", "wenn", "dass", "mein", "meine", "meinen", "bitte", "kannst", "mochte", "möchte",
            "will", "soll", "muss", "finde", "finden", "habe", "haben", "hat", "machen", "mache", "geht", "gehen", "programm", "hier", "dort", "diese", "dieser", "dieses", "alle", "mehr", "sehr", "schon"));

    /** Ein Abschnitt der Wissensbasis (eine Seite, eine Anleitung, die Menüübersicht). */
    record Abschnitt(String titel, String text, Map<String, Integer> woerter, Set<String> titelWoerter) { }

    private volatile List<Abschnitt> abschnitte = List.of();
    private volatile Map<String, Double> idf = Map.of();
    private volatile String grenzen = "";

    @PostConstruct
    void laden() {
        String wissen = lies("ki/wissen.md");
        grenzen = lies("ki/grenzen.md").strip();
        init(wissen);
        log.info("KI-Wissensbasis geladen: {} Abschnitte, {} Zeichen, Grenzen {} Zeichen", abschnitte.size(), wissen.length(), grenzen.length());
    }

    /** Für Tests: lädt die Wissensbasis aus Text. */
    void init(String wissen) {
        List<Abschnitt> liste = new ArrayList<>();
        for (String teil : wissen.split("\n(?=## )")) {
            if (!teil.startsWith("## ")) {
                continue;
            }
            int zeilenende = teil.indexOf('\n');
            String titel = (zeilenende < 0 ? teil : teil.substring(0, zeilenende)).substring(3).strip();
            // Der Menüweg gehört zum Titel: Wer nach "Lager" fragt, soll die Seite Lager finden
            String kopf = teil.lines().skip(1).filter(z -> z.startsWith("Menüweg:") || z.startsWith("Adresse:")).reduce("", (a, b) -> a + " " + b);
            Map<String, Integer> woerter = new HashMap<>();
            for (String w : zerlege(teil)) {
                woerter.merge(w, 1, Integer::sum);
            }
            Set<String> titelWoerter = new HashSet<>(zerlege(titel + " " + kopf));
            liste.add(new Abschnitt(titel, teil.strip(), woerter, titelWoerter));
        }
        Map<String, Integer> haeufigkeit = new HashMap<>();
        for (Abschnitt a : liste) {
            for (String w : a.woerter().keySet()) {
                haeufigkeit.merge(w, 1, Integer::sum);
            }
        }
        Map<String, Double> gewicht = new HashMap<>();
        int n = Math.max(1, liste.size());
        haeufigkeit.forEach((w, df) -> gewicht.put(w, Math.log(1.0 + (double) n / df)));
        this.abschnitte = List.copyOf(liste);
        this.idf = gewicht;
    }

    /** Was das Programm nicht kann, von Hand gepflegt. Leer, wenn die Datei fehlt. */
    public String grenzen() {
        return grenzen;
    }

    public int anzahlAbschnitte() {
        return abschnitte.size();
    }

    /**
     * Die zur Frage passenden Abschnitte, bestes zuerst, zusammen höchstens {@code maxZeichen} lang (ein Abschnitt wird bei Bedarf gekürzt). Ohne Treffer ein leerer Text.
     */
    public String relevantes(String frage, int maxZeichen) {
        List<String> woerter = zerlege(frage == null ? "" : frage);
        if (woerter.isEmpty() || maxZeichen <= 0) {
            return "";
        }
        Set<String> gefragt = new HashSet<>(woerter);
        record Treffer(Abschnitt abschnitt, double punkte) { }
        List<Treffer> treffer = new ArrayList<>();
        for (Abschnitt a : abschnitte) {
            double punkte = 0;
            for (String w : gefragt) {
                Integer anzahl = a.woerter().get(w);
                if (anzahl == null) {
                    continue;
                }
                double gewicht = idf.getOrDefault(w, 1.0);
                punkte += gewicht * (1 + Math.log(anzahl)) * (a.titelWoerter().contains(w) ? 3 : 1);
            }
            if (punkte > 0) {
                treffer.add(new Treffer(a, punkte));
            }
        }
        treffer.sort(Comparator.comparingDouble(Treffer::punkte).reversed());
        StringBuilder sb = new StringBuilder();
        for (Treffer t : treffer) {
            String text = t.abschnitt().text();
            int platz = maxZeichen - sb.length() - 2;
            if (platz < 200) {
                break;
            }
            if (text.length() > platz) {
                text = text.substring(0, platz) + " …";
            }
            sb.append(text).append("\n\n");
        }
        return sb.toString().strip();
    }

    /** Wörter einer Frage oder eines Textes: klein, ohne Umlaut-Unterschiede, ohne Füllwörter, auf die ersten Buchstaben gekürzt (so passen "Nummer" und "Nummern"). */
    static List<String> zerlege(String text) {
        List<String> ergebnis = new ArrayList<>();
        String klein = Normalizer.normalize(text.toLowerCase(Locale.GERMAN), Normalizer.Form.NFC);
        for (String w : NICHT_BUCHSTABE.split(klein)) {
            if (w.length() < 3 || FUELLWOERTER.contains(w)) {
                continue;
            }
            ergebnis.add(stamm(w));
        }
        return ergebnis;
    }

    private static String stamm(String w) {
        String s = w.replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss");
        return s.length() > STAMM_LAENGE ? s.substring(0, STAMM_LAENGE) : s;
    }

    private static String lies(String ressource) {
        try (InputStream in = new ClassPathResource(ressource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Ressource {} nicht lesbar: {}", ressource, e.getMessage());
            return "";
        }
    }
}
