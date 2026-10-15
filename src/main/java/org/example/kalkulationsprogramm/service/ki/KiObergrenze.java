package org.example.kalkulationsprogramm.service.ki;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die zentrale Obergrenze für die KI (Issue #125): <b>wohin gehen Daten</b> und <b>was darf die KI tun</b>. Sie steht über allen KI-Funktionen, über der
 * Datenschutz-Stufe je Funktion und über den Rechten; was sie verbietet, bietet die Oberfläche nicht an, und der Server prüft es bei jedem Aufruf.
 *
 * <p><b>Datenschutz:</b> {@link Datenschutzstufe#LOKAL} nur die lokale KI; {@link Datenschutzstufe#ALLGEMEIN} auch Cloud-Anbieter, aber <b>ohne Daten aus dem
 * Programm</b> (keine Kunden, Stammdaten, Bankdaten, Dokumente: keine Werkzeuge, kein Seitentext), damit es ohne Vertrag mit dem Anbieter auskommt;
 * {@link Datenschutzstufe#MIT_VERTRAG} Programmdaten nur an Anbieter, für die der Administrator einen Vertrag eingetragen hat; {@link Datenschutzstufe#CLOUD}
 * alle eingerichteten Anbieter. Die lokale KI sieht Programmdaten immer (sie bleiben im Haus).</p>
 *
 * <p><b>Eingriff:</b> {@link Eingriff#AUS} keine KI; {@link Eingriff#NUR_LESEN} Auskünfte, nichts ändern; {@link Eingriff#MIT_BESTAETIGUNG} (Standard) Änderungen
 * nur als Vorschlag mit „Ja, ausführen“; {@link Eingriff#ERWEITERT} zusätzlich einzelne vom Administrator freigeschaltete Werkzeuge <b>ohne Rückfrage</b>, nie
 * ein Werkzeug, das löscht.</p>
 *
 * <p>Ohne Eintrag gilt der bisherige Zustand ({@code CLOUD} und {@code MIT_BESTAETIGUNG}), damit bestehende Installationen nichts verlieren; neue Installationen
 * starten mit {@code LOKAL}. Jede Änderung wird im KI-Protokoll festgehalten (wer, wann, von wo nach wo).</p>
 */
@Service
public class KiObergrenze {

    public enum Datenschutzstufe { LOKAL, ALLGEMEIN, MIT_VERTRAG, CLOUD }

    public enum Eingriff { AUS, NUR_LESEN, MIT_BESTAETIGUNG, ERWEITERT }

    static final String DATENSCHUTZ = "ki.obergrenze.datenschutz";
    static final String EINGRIFF = "ki.obergrenze.eingriff";
    static final String VERTRAG = "ki.obergrenze.vertrag.";
    static final String OHNE_RUECKFRAGE = "ki.obergrenze.ohne-rueckfrage";
    static final int MAX_WERKZEUGE = 50;

    /** Eingabe beim Speichern; {@code vertrag} je Anbieter (nur Cloud), {@code ohneRueckfrage} die Namen der freigeschalteten Werkzeuge. */
    public record Eingabe(String datenschutz, String eingriff, Map<String, Boolean> vertrag, Set<String> ohneRueckfrage) { }

    private final SystemSettingsService einstellungen;
    private final KiAktionProtokollRepository protokoll;

    public KiObergrenze(SystemSettingsService einstellungen, KiAktionProtokollRepository protokoll) {
        this.einstellungen = einstellungen;
        this.protokoll = protokoll;
    }

    // ------------------------------------------------------------------ Lesen

    public Datenschutzstufe datenschutz() {
        return aus(Datenschutzstufe.class, einstellungen.get(DATENSCHUTZ, ""), Datenschutzstufe.CLOUD);
    }

    public Eingriff eingriff() {
        return aus(Eingriff.class, einstellungen.get(EINGRIFF, ""), Eingriff.MIT_BESTAETIGUNG);
    }

    /** Hat der Administrator für diesen Cloud-Anbieter einen Vertrag eingetragen (Auftragsverarbeitung, keine Speicherung von Kundendaten)? */
    public boolean hatVertrag(AnbieterTyp anbieter) {
        String wert = einstellungen.get(VERTRAG + anbieter.name(), "false");
        return !anbieter.lokal() && wert != null && "true".equalsIgnoreCase(wert.strip());
    }

    /** Die Werkzeuge, die im Modus „Erweitert“ ohne Rückfrage laufen dürfen (nur wirksam, wenn der Eingriff {@link Eingriff#ERWEITERT} ist). */
    public Set<String> freigeschalteteWerkzeuge() {
        String roh = einstellungen.get(OHNE_RUECKFRAGE, "");
        if (roh == null) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(roh.split(",")).map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Läuft dieses Werkzeug jetzt ohne Rückfrage? Nur im Modus „Erweitert“ und nur, wenn es freigeschaltet ist. */
    public boolean ohneRueckfrage(String werkzeug) {
        return eingriff() == Eingriff.ERWEITERT && freigeschalteteWerkzeuge().contains(werkzeug);
    }

    /** Darf dieser Anbieter überhaupt angesprochen werden? Sonst der Grund in Klartext. */
    public Optional<String> sperrgrund(AnbieterTyp anbieter) {
        if (eingriff() == Eingriff.AUS) {
            return Optional.of("Die KI ist ausgeschaltet. Ein Administrator kann das unter Einstellungen → KI unter „Wie weit darf die KI gehen?“ ändern.");
        }
        if (datenschutz() == Datenschutzstufe.LOKAL && !anbieter.lokal()) {
            return Optional.of("Nach der Einstellung „Wie weit darf die KI gehen?“ (Datenschutz: nur im Haus) sind nur lokale KI-Anbieter erlaubt. "
                    + "Bitte die lokale KI wählen oder die Einstellung unter Einstellungen → KI ändern.");
        }
        return Optional.empty();
    }

    /** Dürfen an diesen Anbieter <b>keine Daten aus dem Programm</b> gehen (Werkzeuge, Seitentext, Dokumente)? Die lokale KI darf sie immer sehen. */
    public boolean sperrtProgrammdaten(AnbieterTyp anbieter) {
        if (anbieter.lokal()) {
            return false;
        }
        return switch (datenschutz()) {
            case LOKAL, ALLGEMEIN -> true;
            case MIT_VERTRAG -> !hatVertrag(anbieter);
            case CLOUD -> false;
        };
    }

    // ------------------------------------------------------------------ Speichern

    /**
     * Speichert die Obergrenze. Ungültige Werte werden mit Klartext abgelehnt. Gespeichert und im KI-Protokoll festgehalten wird nur, was sich ändert.
     *
     * @param erlaubteWerkzeuge die Namen der Werkzeuge, die überhaupt ohne Rückfrage freigeschaltet werden können (ändernd, nicht löschend)
     */
    @Transactional
    public void speichern(Eingabe e, Set<String> erlaubteWerkzeuge, Long profilId, String benutzer) {
        Datenschutzstufe stufe = parse(Datenschutzstufe.class, e.datenschutz(), "Datenschutz");
        Eingriff eingriff = parse(Eingriff.class, e.eingriff(), "Eingriff");
        Set<String> freigabe = new LinkedHashSet<>();
        if (e.ohneRueckfrage() != null) {
            for (String name : e.ohneRueckfrage()) {
                String n = name == null ? "" : name.strip();
                if (n.isEmpty()) {
                    continue;
                }
                if (!erlaubteWerkzeuge.contains(n)) {
                    throw new IllegalArgumentException("Das Werkzeug \"" + n + "\" kann nicht ohne Rückfrage freigeschaltet werden (es ändert nichts, löscht oder gibt es nicht).");
                }
                freigabe.add(n);
            }
        }
        if (freigabe.size() > MAX_WERKZEUGE) {
            throw new IllegalArgumentException("Es können höchstens " + MAX_WERKZEUGE + " Werkzeuge freigeschaltet werden.");
        }
        String vorher = beschreibe(datenschutz(), eingriff(), freigeschalteteWerkzeuge(), vertragsliste());

        einstellungen.save(DATENSCHUTZ, stufe.name(), "KI: Datenschutz-Obergrenze");
        einstellungen.save(EINGRIFF, eingriff.name(), "KI: Eingriffs-Obergrenze");
        for (AnbieterTyp a : AnbieterTyp.values()) {
            if (a.lokal()) {
                continue;
            }
            boolean vertrag = e.vertrag() != null && Boolean.TRUE.equals(e.vertrag().get(a.name()));
            einstellungen.save(VERTRAG + a.name(), String.valueOf(vertrag), "KI: Vertrag mit " + a.anzeige());
        }
        einstellungen.save(OHNE_RUECKFRAGE, String.join(",", freigabe), "KI: Werkzeuge ohne Rückfrage");

        String nachher = beschreibe(stufe, eingriff, freigabe, vertragsliste());
        if (!vorher.equals(nachher)) {
            KiAktionProtokoll p = new KiAktionProtokoll();
            p.setZeit(LocalDateTime.now());
            p.setProfilId(profilId);
            p.setBenutzer(benutzer == null ? null : benutzer.length() > 120 ? benutzer.substring(0, 120) : benutzer);
            p.setFunktion("einstellung");
            p.setAnbieter("-");
            p.setWerkzeug("obergrenze_aendern");
            p.setParameter("{\"vorher\":\"" + vorher.replace("\"", "'") + "\",\"nachher\":\"" + nachher.replace("\"", "'") + "\"}");
            p.setStatus(KiAktionProtokoll.STATUS_AUSGEFUEHRT);
            p.setErgebnis("Vorher: " + vorher + " | Nachher: " + nachher);
            protokoll.save(p);
        }
    }

    private Set<String> vertragsliste() {
        return Arrays.stream(AnbieterTyp.values()).filter(this::hatVertrag).map(AnbieterTyp::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String beschreibe(Datenschutzstufe d, Eingriff e, Set<String> freigabe, Set<String> vertrag) {
        return "Datenschutz " + d + ", Eingriff " + e + ", Vertrag mit " + (vertrag.isEmpty() ? "keinem" : String.join("/", vertrag))
                + ", ohne Rückfrage: " + (freigabe.isEmpty() ? "keines" : String.join("/", freigabe));
    }

    private static <T extends Enum<T>> T parse(Class<T> typ, String wert, String name) {
        try {
            return Enum.valueOf(typ, wert == null ? "" : wert.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unbekannte Stufe für " + name + ": " + wert);
        }
    }

    private static <T extends Enum<T>> T aus(Class<T> typ, String wert, T standard) {
        if (wert == null) {
            return standard;
        }
        try {
            return Enum.valueOf(typ, wert.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return standard;
        }
    }
}
