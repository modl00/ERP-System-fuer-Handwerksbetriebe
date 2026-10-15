package org.example.kalkulationsprogramm.service.planung;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Die Terminrechnung der Plantafel (Vorwärtsterminierung), <b>ohne Datenbank</b>, damit sie sich genau prüfen lässt.
 * <p>Jeder Vorgang beginnt am <b>nächsten Arbeitstag</b> nach dem Ende seiner Vorgänger (plus ein einstellbarer Puffer, zum Beispiel für Trocknung oder Transport) und
 * dauert so viele <b>Arbeitstage</b> wie angegeben. Samstag, Sonntag und Feiertage zählen nicht. Das Ende des letzten Vorgangs ist die Lieferzeit des Auftrags.
 * Ein Vorgang mit {@code festerStart} beginnt nicht früher; kommen seine Vorgänger später fertig, wird er <b>nicht verschoben</b>, sondern als Konflikt gemeldet.</p>
 */
public final class PlanungsRechner {

    /** Sicherung gegen Endlosschleifen bei absurden Eingaben (zum Beispiel Dauer 100000 Tage). */
    static final int MAX_SCHRITTE = 20_000;

    private PlanungsRechner() {
    }

    /** Ein Vorgänger-Bezug: {@code versatzTage} sind zusätzliche Arbeitstage Puffer (0 oder mehr). */
    public record Kante(Long vorgaengerId, int versatzTage) { }

    /** Ein Vorgang. {@code dauerTage} = Arbeitstage, 0 = Meilenstein (ein Tag, keine Dauer). */
    public record Knoten(Long id, int dauerTage, LocalDate festerStart, List<Kante> vorgaenger) {
        public Knoten {
            vorgaenger = vorgaenger == null ? List.of() : List.copyOf(vorgaenger);
        }
    }

    /** Das Ergebnis je Vorgang; {@code konflikt}: der feste Start liegt vor dem Ende der Vorgänger. */
    public record Zeitraum(LocalDate start, LocalDate ende, boolean fixiert, boolean konflikt) { }

    /** Ein Arbeitstag ist Montag bis Freitag und kein Feiertag. */
    public static Predicate<LocalDate> arbeitstag(Set<LocalDate> feiertage) {
        return d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !feiertage.contains(d);
    }

    /** Das Datum selbst, wenn es ein Arbeitstag ist, sonst der nächste. */
    public static LocalDate ab(LocalDate datum, Predicate<LocalDate> arbeitstag) {
        LocalDate d = datum;
        for (int i = 0; i < MAX_SCHRITTE && !arbeitstag.test(d); i++) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** {@code n} Arbeitstage nach dem Datum (n = 0: das Datum selbst, falls Arbeitstag, sonst der nächste). */
    public static LocalDate plusArbeitstage(LocalDate datum, int n, Predicate<LocalDate> arbeitstag) {
        LocalDate d = ab(datum, arbeitstag);
        int rest = n;
        for (int i = 0; i < MAX_SCHRITTE && rest > 0; i++) {
            d = d.plusDays(1);
            if (arbeitstag.test(d)) {
                rest--;
            }
        }
        return d;
    }

    /**
     * Rechnet Start und Ende aller Vorgänge.
     *
     * @param fruehestens frühester Beginn des Auftrags (zum Beispiel heute oder das Startdatum)
     * @throws IllegalArgumentException bei einem Kreis in den Abhängigkeiten oder einem unbekannten Vorgänger (mit Klartext)
     */
    public static Map<Long, Zeitraum> berechne(List<Knoten> knoten, LocalDate fruehestens, Predicate<LocalDate> arbeitstag) {
        Map<Long, Knoten> nachId = new LinkedHashMap<>();
        for (Knoten k : knoten) {
            if (k.dauerTage() < 0) {
                throw new IllegalArgumentException("Die Dauer darf nicht negativ sein.");
            }
            nachId.put(k.id(), k);
        }
        Map<Long, Integer> offen = new HashMap<>();
        Map<Long, List<Long>> nachfolger = new HashMap<>();
        for (Knoten k : knoten) {
            offen.put(k.id(), k.vorgaenger().size());
            for (Kante kante : k.vorgaenger()) {
                if (!nachId.containsKey(kante.vorgaengerId())) {
                    throw new IllegalArgumentException("Ein Vorgänger gehört nicht zu diesem Auftrag.");
                }
                if (kante.versatzTage() < 0) {
                    throw new IllegalArgumentException("Der Puffer darf nicht negativ sein.");
                }
                nachfolger.computeIfAbsent(kante.vorgaengerId(), x -> new ArrayList<>()).add(k.id());
            }
        }
        ArrayDeque<Long> bereit = new ArrayDeque<>();
        for (Knoten k : knoten) {
            if (offen.get(k.id()) == 0) {
                bereit.add(k.id());
            }
        }
        Map<Long, Zeitraum> ergebnis = new LinkedHashMap<>();
        while (!bereit.isEmpty()) {
            Knoten k = nachId.get(bereit.poll());
            LocalDate start = ab(fruehestens, arbeitstag);
            for (Kante kante : k.vorgaenger()) {
                Zeitraum v = ergebnis.get(kante.vorgaengerId());
                start = max(start, plusArbeitstage(v.ende(), 1 + kante.versatzTage(), arbeitstag));
            }
            boolean konflikt = false;
            boolean fixiert = k.festerStart() != null;
            if (fixiert) {
                LocalDate fest = ab(k.festerStart(), arbeitstag);
                konflikt = start.isAfter(fest);
                start = fest;
            }
            LocalDate ende = k.dauerTage() <= 1 ? start : plusArbeitstage(start, k.dauerTage() - 1, arbeitstag);
            ergebnis.put(k.id(), new Zeitraum(start, ende, fixiert, konflikt));
            for (Long folge : nachfolger.getOrDefault(k.id(), List.of())) {
                if (offen.merge(folge, -1, Integer::sum) == 0) {
                    bereit.add(folge);
                }
            }
        }
        if (ergebnis.size() != knoten.size()) {
            throw new IllegalArgumentException("Die Vorgänge hängen im Kreis voneinander ab. Bitte eine Abhängigkeit entfernen.");
        }
        return ergebnis;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
