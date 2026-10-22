package org.example.kalkulationsprogramm.service.ki;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.KiModellService;
import org.example.kalkulationsprogramm.service.LokalerKiService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion.Datenschutz;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wählt je KI-Funktion den Anbieter (Gemini, lokal, Claude, Mistral) und setzt die <b>Datenschutz-Stufe</b> durch: Steht eine Funktion auf „nur lokal“,
 * wird kein Cloud-Anbieter angesprochen, auch nicht aus Versehen. Schlüssel und Modelle der Anbieter stehen in {@code system_setting}; Schlüssel werden nie
 * zurückgegeben, nur ob einer gesetzt ist.
 */
@Service
public class KiAnbieterService {

    static final String CLAUDE_SCHLUESSEL = "ki.claude.schluessel";
    static final String CLAUDE_MODELL = "ki.claude.modell";
    static final String MISTRAL_SCHLUESSEL = "ki.mistral.schluessel";
    static final String MISTRAL_MODELL = "ki.mistral.modell";
    static final String CLAUDE_STANDARD = "claude-sonnet-5-5";
    static final String MISTRAL_STANDARD = "mistral-small-latest";
    static final String MISTRAL_BASIS = "https://api.mistral.ai/v1";
    private static final Pattern MODELLNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,119}");
    private static final int MAX_SCHLUESSEL = 500;

    /**
     * Einstellung einer Funktion: gewählter Anbieter und Datenschutz-Stufe.
     * {@code benoetigtBild}/{@code benoetigtPdf}: Medientypen, die diese Funktion an den Anbieter schickt (aus {@link KiFunktion}).
     */
    public record FunktionsEinstellung(String funktion, String anzeige, AnbieterTyp anbieter, Datenschutz datenschutz,
            String modell, String standardModell, boolean modellGewaehlt, Map<String, String> auswahl, Map<String, String> standards,
            boolean benoetigtBild, boolean benoetigtPdf) {

        /** Ohne Modellangaben (so lesen die Funktionen selbst ihren Anbieter und Datenschutz). */
        public FunktionsEinstellung(String funktion, String anzeige, AnbieterTyp anbieter, Datenschutz datenschutz) {
            this(funktion, anzeige, anbieter, datenschutz, "", "", false, Map.of(), Map.of(), false, false);
        }
    }

    /**
     * Stand eines Anbieters. Der Schlüssel selbst wird nie gezeigt.
     * {@code kannBild}/{@code kannPdf}: Fähigkeiten des Anbieters, abgeleitet aus {@link AnbieterTyp}.
     */
    public record AnbieterStand(AnbieterTyp typ, String anzeige, boolean lokal, boolean eingerichtet, String modell, boolean schluesselGesetzt,
            boolean kannBild, boolean kannPdf) { }

    public record Uebersicht(List<FunktionsEinstellung> funktionen, List<AnbieterStand> anbieter, List<String> nochGemini) { }

    /** Eingabe für Claude/Mistral: {@code schluessel} null = unverändert, leer = entfernen; {@code modell} leer = Standard. */
    public record AnbieterEingabe(String modell, String schluessel) { }

    /** {@code modell}: null = unverändert, leer = Standard des Anbieters, sonst das gewählte Modell für diese Funktion bei diesem Anbieter. */
    public record FunktionsEingabe(String anbieter, String datenschutz, String modell) {

        public FunktionsEingabe(String anbieter, String datenschutz) {
            this(anbieter, datenschutz, null);
        }
    }

    /** Ergebnis eines Anbieter-Tests. {@code art}: OK, NICHT_EINGERICHTET, FEHLER. */
    public record Testergebnis(boolean ok, String art, String meldung) { }

    private final SystemSettingsService einstellungen;
    private final LokalerKiService lokaleKi;
    private final KiModellService modelle;
    private final KiObergrenze obergrenze;

    // für Tests überschreibbar
    String geminiBasis = GeminiAnbieter.BASIS;
    String claudeBasis = ClaudeAnbieter.BASIS;
    String mistralBasis = MISTRAL_BASIS;

    public KiAnbieterService(SystemSettingsService einstellungen, LokalerKiService lokaleKi, KiModellService modelle, KiObergrenze obergrenze) {
        this.einstellungen = einstellungen;
        this.lokaleKi = lokaleKi;
        this.modelle = modelle;
        this.obergrenze = obergrenze;
    }

    // ------------------------------------------------------------------ Lesen

    public FunktionsEinstellung einstellung(KiFunktion f) {
        AnbieterTyp anbieter = AnbieterTyp.ausName(wert(f.anbieterSchluessel())).orElse(f.standardAnbieter());
        Datenschutz datenschutz = datenschutzAusText(wert(f.datenschutzSchluessel())).orElse(f.standardDatenschutz());
        return new FunktionsEinstellung(f.schluessel(), f.anzeige(), anbieter, datenschutz);
    }

    @Transactional(readOnly = true)
    public Uebersicht uebersicht() {
        List<FunktionsEinstellung> funktionen = new ArrayList<>();
        for (KiFunktion f : KiFunktion.values()) {
            funktionen.add(mitModell(einstellung(f), f));
        }
        List<AnbieterStand> anbieter = new ArrayList<>();
        for (AnbieterTyp t : AnbieterTyp.values()) {
            anbieter.add(stand(t));
        }
        return new Uebersicht(funktionen, anbieter,
                List.of("Spracheingabe (Aufnahmen)", "Kostenkonto-Vorschläge für Belege", "Suche in Wissen und Programm (Einbettungen)"));
    }

    /** Ist irgendein KI-Anbieter benutzbar (Gemini, Claude, Mistral oder die lokale KI)? Davon hängt ab, ob die KI-Funktionen in der Oberfläche erscheinen. */
    public boolean irgendeinAnbieterEingerichtet() {
        for (AnbieterTyp t : AnbieterTyp.values()) {
            if (stand(t).eingerichtet()) {
                return true;
            }
        }
        return false;
    }

    public AnbieterStand stand(AnbieterTyp t) {
        return switch (t) {
            case GEMINI -> new AnbieterStand(t, t.anzeige(), false, !strip(geminiSchluessel()).isEmpty(), modelle.modell(KiModellService.Funktion.HILFE, null),
                    !strip(geminiSchluessel()).isEmpty(), t.kannBild(), t.kannPdf());
            case LOKAL -> {
                var k = lokaleKi.konfiguration();
                yield new AnbieterStand(t, t.anzeige(), true, lokaleKi.istKonfiguriert(), k.modell(), k.schluesselGesetzt(), t.kannBild(), t.kannPdf());
            }
            case CLAUDE -> new AnbieterStand(t, t.anzeige(), false, !strip(einstellungen.get(CLAUDE_SCHLUESSEL, "")).isEmpty(), claudeModell(),
                    !strip(einstellungen.get(CLAUDE_SCHLUESSEL, "")).isEmpty(), t.kannBild(), t.kannPdf());
            case MISTRAL -> new AnbieterStand(t, t.anzeige(), false, !strip(einstellungen.get(MISTRAL_SCHLUESSEL, "")).isEmpty(), mistralModell(),
                    !strip(einstellungen.get(MISTRAL_SCHLUESSEL, "")).isEmpty(), t.kannBild(), t.kannPdf());
        };
    }

    // ------------------------------------------------------------------ Wählen mit Datenschutz

    /**
     * Der Anbieter für eine Funktion. Steht sie auf „nur lokal“ und ist ein Cloud-Anbieter gewählt, gibt es einen Klartext-Fehler statt eines Aufrufs.
     * Ist der gewählte Anbieter nicht eingerichtet, ebenso.
     */
    public KiAnbieter waehle(KiFunktion f) throws KiAnbieterFehler {
        pruefeDatenschutz(f);
        AnbieterTyp typ = einstellung(f).anbieter();
        return bauen(typ, modellFuer(f, typ));
    }

    /** Die Einstellung samt dem Modell, das für diese Funktion beim gewählten Anbieter gilt. */
    private FunktionsEinstellung mitModell(FunktionsEinstellung e, KiFunktion f) {
        // Je Anbieter: die gewählte Modell (leer = keine Wahl) und der Standard. So zeigt die Oberfläche nach einem Anbieterwechsel die richtige Auswahl.
        Map<String, String> auswahl = new java.util.LinkedHashMap<>();
        Map<String, String> standards = new java.util.LinkedHashMap<>();
        for (AnbieterTyp t : AnbieterTyp.values()) {
            auswahl.put(t.name(), modellGewaehlt(f, t) ? modellFuer(f, t) : "");
            String standard = standardModellFuer(f, t);
            standards.put(t.name(), standard == null ? "" : standard);
        }
        return new FunktionsEinstellung(e.funktion(), e.anzeige(), e.anbieter(), e.datenschutz(),
                modellFuer(f, e.anbieter()), standardModellFuer(f, e.anbieter()), modellGewaehlt(f, e.anbieter()), auswahl, standards,
                f.benoetigtBild(), f.benoetigtPdf());
    }

    /** Das Modell dieser Funktion beim Anbieter: die Auswahl, sonst der Standard des Anbieters (bei Gemini der Standard der Funktion). */
    String modellFuer(KiFunktion f, AnbieterTyp typ) {
        if (typ == AnbieterTyp.GEMINI) {
            return modelle.modell(f.geminiFunktion(), standardModellFuer(f, typ));
        }
        String gewaehlt = wert(f.modellSchluessel(typ));
        return gewaehlt.isEmpty() ? standardModellFuer(f, typ) : gewaehlt;
    }

    private String standardModellFuer(KiFunktion f, AnbieterTyp typ) {
        return switch (typ) {
            // Der Assistent fragte bisher mit dem Modell der KI-Hilfe; ohne eigene Wahl bleibt das so
            case GEMINI -> f == KiFunktion.ASSISTENT ? modelle.modell(KiModellService.Funktion.HILFE, null) : modelle.standard(f.geminiFunktion());
            case LOKAL -> lokaleKi.konfiguration().modell();
            case CLAUDE -> claudeModell();
            case MISTRAL -> mistralModell();
        };
    }

    private boolean modellGewaehlt(KiFunktion f, AnbieterTyp typ) {
        return typ == AnbieterTyp.GEMINI ? modelle.istGewaehlt(f.geminiFunktion()) : !wert(f.modellSchluessel(typ)).isEmpty();
    }

    /** Wirft einen Klartext-Fehler, wenn die Funktion auf „nur lokal“ steht, aber ein Cloud-Anbieter gewählt ist. */
    public void pruefeDatenschutz(KiFunktion f) throws KiAnbieterFehler {
        FunktionsEinstellung e = einstellung(f);
        // Die zentrale Obergrenze („Wie weit darf die KI gehen?“) steht über der Einstellung der Funktion
        var sperre = obergrenze.sperrgrund(e.anbieter());
        if (sperre.isPresent()) {
            throw new KiAnbieterFehler(sperre.get());
        }
        if (e.datenschutz() == Datenschutz.NUR_LOKAL && !e.anbieter().lokal()) {
            throw new KiAnbieterFehler("Für „" + f.anzeige() + "“ sind nur lokale KI-Anbieter erlaubt (Datenschutz). Bitte unter Einstellungen → KI-Funktionen die lokale KI wählen "
                    + "oder die Datenschutz-Stufe ändern.");
        }
    }

    /** Dürfen an diesen Anbieter keine Daten aus dem Programm gehen (Werkzeuge, Seitentext)? Siehe {@link KiObergrenze}. */
    public boolean sperrtProgrammdaten(AnbieterTyp anbieter) {
        return obergrenze.sperrtProgrammdaten(anbieter);
    }

    /** Wie weit die KI eingreifen darf (zentrale Obergrenze). */
    public KiObergrenze.Eingriff eingriff() {
        return obergrenze.eingriff();
    }

    /** Läuft dieses Werkzeug ohne Rückfrage (Modus „Erweitert“, vom Administrator freigeschaltet)? */
    public boolean ohneRueckfrage(String werkzeug) {
        return obergrenze.ohneRueckfrage(werkzeug);
    }

    /** Baut den Anbieter ohne Datenschutz-Prüfung, für Tests der Einstellungen. */
    KiAnbieter bauen(AnbieterTyp typ) throws KiAnbieterFehler {
        return bauen(typ, null);
    }

    /** Wie {@link #bauen(AnbieterTyp)}, aber mit einem bestimmten Modell ({@code null} = das des Anbieters). */
    KiAnbieter bauen(AnbieterTyp typ, String modell) throws KiAnbieterFehler {
        String m = modell == null || modell.isBlank() ? null : modell.strip();
        return switch (typ) {
            case LOKAL -> {
                if (!lokaleKi.istKonfiguriert()) {
                    throw new KiAnbieterFehler("Die lokale KI ist noch nicht eingerichtet (Einstellungen → KI-Funktionen → Lokale KI).");
                }
                var k = lokaleKi.konfiguration();
                yield new OpenAiKompatiblerAnbieter(typ, LokalerKiService.basis(k.adresse()), lokaleKi.schluessel(), m != null ? m : k.modell(), null, Duration.ofSeconds(180));
            }
            case GEMINI -> {
                String key = strip(geminiSchluessel());
                if (key.isEmpty()) {
                    throw new KiAnbieterFehler("Es ist noch kein Gemini API Key hinterlegt (Einstellungen → KI).");
                }
                yield new GeminiAnbieter(geminiBasis, key, m != null ? m : modelle.modell(KiModellService.Funktion.HILFE, null), null, null);
            }
            case CLAUDE -> {
                String key = strip(einstellungen.get(CLAUDE_SCHLUESSEL, ""));
                if (key.isEmpty()) {
                    throw new KiAnbieterFehler("Es ist noch kein Claude-Schlüssel hinterlegt (Einstellungen → KI-Funktionen → KI-Anbieter).");
                }
                yield new ClaudeAnbieter(claudeBasis, key, m != null ? m : claudeModell(), null, null);
            }
            case MISTRAL -> {
                String key = strip(einstellungen.get(MISTRAL_SCHLUESSEL, ""));
                if (key.isEmpty()) {
                    throw new KiAnbieterFehler("Es ist noch kein Mistral-Schlüssel hinterlegt (Einstellungen → KI-Funktionen → KI-Anbieter).");
                }
                yield new OpenAiKompatiblerAnbieter(typ, mistralBasis, key, m != null ? m : mistralModell(), null, Duration.ofSeconds(120));
            }
        };
    }

    // ------------------------------------------------------------------ Speichern

    /**
     * Speichert Anbieter und Datenschutz je Funktion und die Zugangsdaten von Claude und Mistral. Unstimmige Kombinationen („nur lokal“ mit einem Cloud-Anbieter)
     * werden abgelehnt, damit eine Funktion nicht stillschweigend unbenutzbar wird.
     */
    @Transactional
    public Uebersicht speichern(Map<String, FunktionsEingabe> funktionen, AnbieterEingabe claude, AnbieterEingabe mistral) {
        if (funktionen != null) {
            for (Map.Entry<String, FunktionsEingabe> e : funktionen.entrySet()) {
                KiFunktion f = KiFunktion.ausSchluessel(e.getKey()).orElseThrow(() -> fehler("Diese KI-Funktion gibt es nicht."));
                FunktionsEingabe in = e.getValue();
                if (in == null) {
                    throw fehler("Für " + f.anzeige() + " fehlen die Angaben.");
                }
                AnbieterTyp a = AnbieterTyp.ausName(in.anbieter()).orElseThrow(() -> fehler("Diesen Anbieter gibt es nicht."));
                Datenschutz d = datenschutzAusText(in.datenschutz()).orElseThrow(() -> fehler("Diese Datenschutz-Stufe gibt es nicht."));
                if (d == Datenschutz.NUR_LOKAL && !a.lokal()) {
                    throw fehler("Für „" + f.anzeige() + "“ ist „nur lokal“ gewählt, dann muss auch die lokale KI der Anbieter sein.");
                }
                einstellungen.save(f.anbieterSchluessel(), a.name(), "Anbieter der KI-Funktion " + f.anzeige());
                einstellungen.save(f.datenschutzSchluessel(), d.name(), "Datenschutz-Stufe der KI-Funktion " + f.anzeige());
                if (in.modell() != null) {
                    speichereFunktionsModell(f, a, in.modell());
                }
            }
        }
        speichereAnbieter(CLAUDE_SCHLUESSEL, CLAUDE_MODELL, claude, "Claude");
        speichereAnbieter(MISTRAL_SCHLUESSEL, MISTRAL_MODELL, mistral, "Mistral");
        return uebersicht();
    }

    /** Modell einer Funktion bei einem Anbieter; leer = Standard. Gemini nutzt die bestehende Modellwahl je Funktion. */
    private void speichereFunktionsModell(KiFunktion f, AnbieterTyp a, String modell) {
        String m = modell.strip();
        if (!m.isEmpty() && !MODELLNAME.matcher(m).matches()) {
            throw fehler("Der Modellname für „" + f.anzeige() + "“ ist ungültig (erlaubt sind Buchstaben, Ziffern und . : / @ - _).");
        }
        if (a == AnbieterTyp.GEMINI) {
            modelle.speichern(Map.of(f.geminiFunktion().schluessel(), m), null);
        } else {
            einstellungen.save(f.modellSchluessel(a), m, "Modell der KI-Funktion " + f.anzeige() + " bei " + a.anzeige());
        }
    }

    private void speichereAnbieter(String schluesselKey, String modellKey, AnbieterEingabe in, String name) {
        if (in == null) {
            return;
        }
        String m = in.modell() == null ? "" : in.modell().strip();
        if (!m.isEmpty() && !MODELLNAME.matcher(m).matches()) {
            throw fehler("Der Modellname für " + name + " ist ungültig (erlaubt sind Buchstaben, Ziffern und . : / @ - _).");
        }
        if (in.schluessel() != null && in.schluessel().strip().length() > MAX_SCHLUESSEL) {
            throw fehler("Der Schlüssel für " + name + " ist zu lang.");
        }
        einstellungen.save(modellKey, m, "Modell für " + name);
        if (in.schluessel() != null) {
            einstellungen.save(schluesselKey, in.schluessel().strip(), "Schlüssel für " + name);
        }
    }

    // ------------------------------------------------------------------ Modelle

    /** Ein Modell, das der Anbieter zur Auswahl anbietet. */
    public record Modell(String name, String anzeigename) { }

    /**
     * Fragt den Anbieter, welche Modelle er anbietet (für die Auswahlfelder). Gemini und die lokale KI nutzen ihre bisherigen Abfragen, Claude und Mistral
     * fragen mit dem gespeicherten Schlüssel. Fehler kommen als {@link KiAnbieterFehler} in Klartext.
     */
    public List<Modell> verfuegbareModelle(AnbieterTyp typ) throws KiAnbieterFehler {
        try {
            return switch (typ) {
                case GEMINI -> modelle.verfuegbare().stream().map(m -> new Modell(m.name(), m.anzeigename())).toList();
                case LOKAL -> lokaleKi.modelle().stream().map(m -> new Modell(m, m)).toList();
                case CLAUDE -> {
                    String key = strip(einstellungen.get(CLAUDE_SCHLUESSEL, ""));
                    if (key.isEmpty()) {
                        throw new KiAnbieterFehler("Es ist noch kein Claude-Schlüssel hinterlegt. Bitte zuerst den Schlüssel speichern.");
                    }
                    yield modelleAusAntwort(HttpHilfe.hole(HttpHilfe.STANDARD, claudeBasis + "/models?limit=100",
                            Map.of("x-api-key", key, "anthropic-version", "2023-06-01"), Duration.ofSeconds(20), "Claude", ""), "display_name");
                }
                case MISTRAL -> {
                    String key = strip(einstellungen.get(MISTRAL_SCHLUESSEL, ""));
                    if (key.isEmpty()) {
                        throw new KiAnbieterFehler("Es ist noch kein Mistral-Schlüssel hinterlegt. Bitte zuerst den Schlüssel speichern.");
                    }
                    yield modelleAusAntwort(HttpHilfe.hole(HttpHilfe.STANDARD, mistralBasis + "/models",
                            Map.of("Authorization", "Bearer " + key), Duration.ofSeconds(20), "Mistral", ""), "name");
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KiAnbieterFehler("Die Abfrage wurde unterbrochen.");
        }
    }

    /** Liest {@code data[].id} (Anzeigename aus {@code anzeigeFeld}, sonst die Kennung), sortiert, ohne Doppelte. */
    private static List<Modell> modelleAusAntwort(com.fasterxml.jackson.databind.JsonNode root, String anzeigeFeld) {
        java.util.TreeMap<String, Modell> sortiert = new java.util.TreeMap<>();
        for (var m : root.path("data")) {
            String id = m.path("id").asText("").strip();
            if (!id.isEmpty()) {
                String anzeige = m.path(anzeigeFeld).asText("").strip();
                sortiert.putIfAbsent(id, new Modell(id, anzeige.isEmpty() ? id : anzeige));
            }
        }
        return List.copyOf(sortiert.values());
    }

    // ------------------------------------------------------------------ Testen

    /** Schickt eine Mini-Anfrage an den Anbieter und erklärt das Ergebnis. Prüft nicht den Datenschutz einer Funktion, es gehen keine Firmendaten raus. */
    public Testergebnis teste(AnbieterTyp typ) {
        return teste(typ, null);
    }

    /** Wie {@link #teste(AnbieterTyp)}, aber mit einem bestimmten Modell ({@code null} = das des Anbieters). */
    public Testergebnis teste(AnbieterTyp typ, String modell) {
        if (modell != null && !modell.isBlank() && !MODELLNAME.matcher(modell.strip()).matches()) {
            return new Testergebnis(false, "FEHLER", "Bitte einen gültigen Modellnamen angeben.");
        }
        KiAnbieter a;
        try {
            a = bauen(typ, modell);
        } catch (KiAnbieterFehler e) {
            return new Testergebnis(false, "NICHT_EINGERICHTET", e.getMessage());
        }
        try {
            var antwort = a.chat(new KiAnfrage(null, List.of(new Nutzer("Antworte nur mit dem Wort OK.")), List.of(), 0.0, 32));
            String text = antwort.text().replaceAll("\\s+", " ").strip();
            return new Testergebnis(true, "OK", a.typ().anzeige() + " antwortet (Modell " + a.modell() + "): " + (text.length() > 60 ? text.substring(0, 60) + "…" : text));
        } catch (KiAnbieterFehler e) {
            return new Testergebnis(false, "FEHLER", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Testergebnis(false, "FEHLER", "Der Test wurde unterbrochen.");
        }
    }

    // ------------------------------------------------------------------ intern

    private String geminiSchluessel() {
        return einstellungen.getGeminiApiKey();
    }

    private String claudeModell() {
        String m = strip(einstellungen.get(CLAUDE_MODELL, ""));
        return m.isEmpty() ? CLAUDE_STANDARD : m;
    }

    private String mistralModell() {
        String m = strip(einstellungen.get(MISTRAL_MODELL, ""));
        return m.isEmpty() ? MISTRAL_STANDARD : m;
    }

    /** Wert einer Einstellung, ohne Leerzeichen am Rand. */
    private String wert(String einstellung) {
        return strip(einstellungen.get(einstellung, ""));
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static java.util.Optional<Datenschutz> datenschutzAusText(String s) {
        if (s == null) {
            return java.util.Optional.empty();
        }
        for (Datenschutz d : Datenschutz.values()) {
            if (d.name().equalsIgnoreCase(s.strip())) {
                return java.util.Optional.of(d);
            }
        }
        return java.util.Optional.empty();
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
