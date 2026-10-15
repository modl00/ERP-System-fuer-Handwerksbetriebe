package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Kante;

/**
 * Die Stundenrechnung der Plantafel (Issue #331, Abschnitt 3), <b>ohne Datenbank</b>, damit sie sich genau prüfen lässt.
 * <p>Ein <b>Arbeitsvorgang</b> hat einen Aufwand in Stunden und braucht Personen; ein <b>Wartevorgang</b> (Material, Fremdleistung) nur Zeit in Arbeitstagen.
 * Geplant wird nach Wichtigkeit: erst feste Termine, dann alle dringenden, normalen und nicht dringenden Vorgänge. Was eine höhere Stufe belegt, steht der niedrigeren
 * nicht mehr zur Verfügung. Eine Person arbeitet nie an zwei Vorgängen zugleich, aber ein Tag darf geteilt werden (Teiltage). Mehrere Personen am selben Vorgang
 * schaffen zusammen die Summe ihrer freien Stunden. Ohne Zuweisung rechnet das Programm mit dem Standardwert (eine Person mit vollem Arbeitstag) und kennzeichnet den Vorgang als geschätzt.</p>
 */
public final class PlanungsStunden {

    /** So viele Tage sucht die Rechnung höchstens nach freier Zeit; danach gilt der Vorgang als nicht planbar. */
    static final int MAX_TAGE = 1100;
    private static final BigDecimal NULL = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("d.M.");

    private PlanungsStunden() {
    }

    /**
     * Ein Vorgang für die Rechnung. {@code stunden} = noch offener Aufwand (Arbeitsvorgang); {@code dauerTage} gilt nur für Wartevorgänge.
     * {@code eingefroren}: erledigte Vorgänge behalten ihre Termine (Start und Ende) und belegen nichts mehr.
     * Bei einem Wartevorgang gilt: {@code bestelltAm} startet die Lieferzeit an dem Tag (auch in der Vergangenheit, Entscheidung H), {@code lieferterminFix} (Auftragsbestätigung des
     * Lieferanten) ist das Ende, egal was die Dauer sagt.
     */
    public record Eingang(Long id, Long projektId, String bezeichnung, String auftrag, String prioritaet, Integer rang, LocalDate zielDatum, boolean wartet, boolean buero,
            BigDecimal stunden, int dauerTage, LocalDate festerStart, LocalDate fruehestensAb, LocalDate auftragsStart, Set<Long> personen, List<Kante> vorgaenger,
            LocalDate eingefrorenStart, LocalDate eingefrorenEnde, int reihenfolge, LocalDate bestelltAm, LocalDate lieferterminFix) {
        public Eingang {
            personen = personen == null ? Set.of() : Set.copyOf(personen);
            vorgaenger = vorgaenger == null ? List.of() : List.copyOf(vorgaenger);
        }

        /** Ohne Lieferdaten (alle Vorgänge außer Wartevorgängen mit Lieferant). */
        public Eingang(Long id, Long projektId, String bezeichnung, String auftrag, String prioritaet, Integer rang, LocalDate zielDatum, boolean wartet, boolean buero,
                BigDecimal stunden, int dauerTage, LocalDate festerStart, LocalDate fruehestensAb, LocalDate auftragsStart, Set<Long> personen, List<Kante> vorgaenger,
                LocalDate eingefrorenStart, LocalDate eingefrorenEnde, int reihenfolge) {
            this(id, projektId, bezeichnung, auftrag, prioritaet, rang, zielDatum, wartet, buero, stunden, dauerTage, festerStart, fruehestensAb, auftragsStart, personen, vorgaenger,
                    eingefrorenStart, eingefrorenEnde, reihenfolge, null, null);
        }
    }

    /** Was die Rechnung über Personen und Tage wissen muss. */
    public interface Kapazitaet {
        /** Stunden, die die Person an dem Tag für diese Art Arbeit hat (0 = keine). {@code buero}: Vorgang der Art Büroarbeit. */
        BigDecimal stunden(Long person, LocalDate tag, boolean buero);

        /** Warum die Person an dem Tag keine Stunden hat (zum Beispiel „Urlaub“); {@code null}, wenn es einfach kein Arbeitstag ist. */
        String grund(Long person, LocalDate tag, boolean buero);

        /** Die Stunden einer noch nicht zugewiesenen Person (Standard-Arbeitstag, 0 an freien Tagen). */
        BigDecimal standard(LocalDate tag);

        String name(Long person);

        boolean arbeitstag(LocalDate tag);
    }

    public record Zuteilung(Long vorgangId, Long person, LocalDate tag, BigDecimal stunden) { }

    /** {@code geschaetzt}: noch niemand zugewiesen, es wurde mit dem Standardwert gerechnet. {@code hinweise}: warum der Vorgang später endet (fehlende Personen). */
    public record Ergebnis(LocalDate start, LocalDate ende, boolean fixiert, boolean konflikt, boolean geschaetzt, boolean planbar, List<String> hinweise) { }

    public record Plan(Map<Long, Ergebnis> vorgaenge, List<Zuteilung> zuteilungen) { }

    /** Wer belegt eine Person an einem Tag (für den Hinweis „anderswo: …“). */
    private record Belegt(String text, BigDecimal stunden) { }

    /**
     * Rechnet alle Vorgänge aller Aufträge.
     *
     * @throws IllegalArgumentException bei einem Kreis in den Abhängigkeiten oder einem unbekannten Vorgänger (mit Klartext)
     */
    public static Plan berechne(List<Eingang> eingaenge, LocalDate heute, Kapazitaet kap) {
        Map<Long, Eingang> nachId = new LinkedHashMap<>();
        for (Eingang e : eingaenge) {
            if (e.dauerTage() < 0) {
                throw new IllegalArgumentException("Die Dauer darf nicht negativ sein.");
            }
            nachId.put(e.id(), e);
        }
        Map<Long, List<Long>> nachfolger = new HashMap<>();
        for (Eingang e : eingaenge) {
            for (Kante k : e.vorgaenger()) {
                if (!nachId.containsKey(k.vorgaengerId())) {
                    throw new IllegalArgumentException("Ein Vorgänger gehört nicht zu diesem Auftrag.");
                }
                if (k.versatzTage() < 0) {
                    throw new IllegalArgumentException("Der Puffer darf nicht negativ sein.");
                }
                nachfolger.computeIfAbsent(k.vorgaengerId(), x -> new ArrayList<>()).add(e.id());
            }
        }
        pruefeKreis(nachId, nachfolger);

        Map<Long, Integer> wichtig = wichtigkeit(nachId, nachfolger);
        Comparator<Eingang> reihenfolge = Comparator
                .comparing((Eingang e) -> wichtig.get(e.id()))
                .thenComparing(e -> e.rang() == null ? Integer.MAX_VALUE : e.rang())
                .thenComparing(e -> e.zielDatum() == null ? LocalDate.MAX : e.zielDatum())
                .thenComparing(Eingang::projektId)
                .thenComparingInt(Eingang::reihenfolge)
                .thenComparing(Eingang::id);

        Rechnung r = new Rechnung(kap, heute);
        Predicate<LocalDate> arbeitstag = kap::arbeitstag;

        // 1. erledigte Vorgänge behalten ihre Termine
        for (Eingang e : eingaenge) {
            if (e.eingefrorenStart() != null && e.eingefrorenEnde() != null) {
                r.ergebnis.put(e.id(), new Ergebnis(e.eingefrorenStart(), e.eingefrorenEnde(), e.festerStart() != null, false, false, true, List.of()));
            }
        }
        // 2. feste Termine zuerst: Sie bewegen sich nie und nehmen anderen die Zeit weg
        List<Eingang> fest = eingaenge.stream().filter(e -> !r.ergebnis.containsKey(e.id()) && e.festerStart() != null).sorted(reihenfolge).toList();
        for (Eingang e : fest) {
            r.plane(e, PlanungsRechner.ab(max(e.festerStart(), heute), arbeitstag), e.festerStart());
        }
        // 3. alle anderen nach Wichtigkeit, sobald ihre Vorgänger fertig gerechnet sind
        Set<Long> offen = new LinkedHashSet<>();
        for (Eingang e : eingaenge) {
            if (!r.ergebnis.containsKey(e.id())) {
                offen.add(e.id());
            }
        }
        while (!offen.isEmpty()) {
            Eingang naechster = null;
            for (Long id : offen) {
                Eingang e = nachId.get(id);
                if (e.vorgaenger().stream().allMatch(k -> r.ergebnis.containsKey(k.vorgaengerId())) && (naechster == null || reihenfolge.compare(e, naechster) < 0)) {
                    naechster = e;
                }
            }
            if (naechster == null) {
                throw new IllegalArgumentException("Die Vorgänge hängen im Kreis voneinander ab. Bitte eine Abhängigkeit entfernen.");
            }
            offen.remove(naechster.id());
            // Eine Lieferung läuft ab der Bestellung, auch wenn die schon vor heute war
            LocalDate start = PlanungsRechner.ab(naechster.wartet() && naechster.bestelltAm() != null ? naechster.bestelltAm() : heute, arbeitstag);
            if (naechster.auftragsStart() != null) {
                start = max(start, PlanungsRechner.ab(naechster.auftragsStart(), arbeitstag));
            }
            if (naechster.fruehestensAb() != null) {
                start = max(start, PlanungsRechner.ab(naechster.fruehestensAb(), arbeitstag));
            }
            for (Kante k : naechster.vorgaenger()) {
                start = max(start, PlanungsRechner.plusArbeitstage(r.ergebnis.get(k.vorgaengerId()).ende(), 1 + k.versatzTage(), arbeitstag));
            }
            r.plane(naechster, start, null);
        }
        // 4. Konflikt: Ein fester Start liegt vor dem Ende der Vorgänger
        for (Eingang e : fest) {
            LocalDate frueh = PlanungsRechner.ab(heute, arbeitstag);
            for (Kante k : e.vorgaenger()) {
                frueh = max(frueh, PlanungsRechner.plusArbeitstage(r.ergebnis.get(k.vorgaengerId()).ende(), 1 + k.versatzTage(), arbeitstag));
            }
            Ergebnis alt = r.ergebnis.get(e.id());
            boolean konflikt = frueh.isAfter(PlanungsRechner.ab(e.festerStart(), arbeitstag)) && e.festerStart().isAfter(heute.minusDays(1));
            if (konflikt) {
                r.ergebnis.put(e.id(), new Ergebnis(alt.start(), alt.ende(), true, true, alt.geschaetzt(), alt.planbar(), alt.hinweise()));
            }
        }
        return new Plan(r.ergebnis, r.zuteilungen);
    }

    /** Dringend = 0, normal = 1, nicht dringend = 2. Ein Vorgang ist mindestens so wichtig wie jeder, der von ihm abhängt (sonst bliebe der dringende Auftrag hinter dem Material hängen). */
    private static Map<Long, Integer> wichtigkeit(Map<Long, Eingang> nachId, Map<Long, List<Long>> nachfolger) {
        Map<Long, Integer> ergebnis = new HashMap<>();
        for (Eingang e : nachId.values()) {
            wichtigkeit(e.id(), nachId, nachfolger, ergebnis);
        }
        return ergebnis;
    }

    private static int wichtigkeit(Long id, Map<Long, Eingang> nachId, Map<Long, List<Long>> nachfolger, Map<Long, Integer> gemerkt) {
        Integer bekannt = gemerkt.get(id);
        if (bekannt != null) {
            return bekannt;
        }
        int w = stufe(nachId.get(id).prioritaet());
        for (Long folge : nachfolger.getOrDefault(id, List.of())) {
            w = Math.min(w, wichtigkeit(folge, nachId, nachfolger, gemerkt));
        }
        gemerkt.put(id, w);
        return w;
    }

    private static int stufe(String prioritaet) {
        if ("DRINGEND".equals(prioritaet)) {
            return 0;
        }
        return "NICHT_DRINGEND".equals(prioritaet) ? 2 : 1;
    }

    /** Ein Kreis würde die Rekursion oben endlos laufen lassen; deshalb vorher prüfen (Kahn-Verfahren). */
    private static void pruefeKreis(Map<Long, Eingang> nachId, Map<Long, List<Long>> nachfolger) {
        Map<Long, Integer> eingehend = new HashMap<>();
        nachId.values().forEach(e -> eingehend.put(e.id(), e.vorgaenger().size()));
        java.util.ArrayDeque<Long> bereit = new java.util.ArrayDeque<>();
        eingehend.forEach((id, n) -> {
            if (n == 0) {
                bereit.add(id);
            }
        });
        int gesehen = 0;
        while (!bereit.isEmpty()) {
            Long id = bereit.poll();
            gesehen++;
            for (Long folge : nachfolger.getOrDefault(id, List.of())) {
                if (eingehend.merge(folge, -1, Integer::sum) == 0) {
                    bereit.add(folge);
                }
            }
        }
        if (gesehen != nachId.size()) {
            throw new IllegalArgumentException("Die Vorgänge hängen im Kreis voneinander ab. Bitte eine Abhängigkeit entfernen.");
        }
    }

    /** Der laufende Zustand der Rechnung: wer ist an welchem Tag schon wie lange belegt. */
    private static final class Rechnung {
        final Kapazitaet kap;
        final LocalDate heute;
        final Map<Long, Ergebnis> ergebnis = new LinkedHashMap<>();
        final List<Zuteilung> zuteilungen = new ArrayList<>();
        final Map<Long, Map<LocalDate, BigDecimal>> benutzt = new HashMap<>();
        final Map<Long, Map<LocalDate, List<Belegt>>> belegtVon = new HashMap<>();

        Rechnung(Kapazitaet kap, LocalDate heute) {
            this.kap = kap;
            this.heute = heute;
        }

        void plane(Eingang e, LocalDate fruehestens, LocalDate angezeigterStart) {
            Predicate<LocalDate> arbeitstag = kap::arbeitstag;
            boolean fixiert = e.festerStart() != null;
            if (e.wartet()) {
                LocalDate ende = e.dauerTage() <= 1 ? fruehestens : PlanungsRechner.plusArbeitstage(fruehestens, e.dauerTage() - 1, arbeitstag);
                if (e.lieferterminFix() != null) { // Der Lieferant hat den Termin bestätigt: Das ist das Ende, nicht früher als der Beginn
                    LocalDate fix = PlanungsRechner.ab(e.lieferterminFix(), arbeitstag);
                    ende = fix.isBefore(fruehestens) ? fruehestens : fix;
                }
                ergebnis.put(e.id(), new Ergebnis(angezeigterStart != null ? angezeigterStart : fruehestens, ende, fixiert, false, false, true, List.of()));
                return;
            }
            BigDecimal rest = e.stunden() == null ? NULL : e.stunden().setScale(2, RoundingMode.HALF_UP);
            if (rest.signum() <= 0) { // Meilenstein
                ergebnis.put(e.id(), new Ergebnis(angezeigterStart != null ? angezeigterStart : fruehestens, fruehestens, fixiert, false, false, true, List.of()));
                return;
            }
            boolean ohneZuweisung = e.personen().isEmpty();
            List<Long> personen = ohneZuweisung ? List.of(-e.id()) : e.personen().stream().sorted().toList();
            LocalDate erster = null;
            LocalDate letzter = null;
            Map<String, TreeSet<LocalDate>> verluste = new LinkedHashMap<>();
            LocalDate tag = fruehestens;
            int schritte = 0;
            while (rest.signum() > 0 && schritte++ < MAX_TAGE) {
                if (!kap.arbeitstag(tag)) {
                    tag = tag.plusDays(1);
                    continue;
                }
                for (Long p : personen) {
                    if (rest.signum() <= 0) {
                        break;
                    }
                    BigDecimal kapazitaet = p < 0 ? kap.standard(tag) : kap.stunden(p, tag, e.buero());
                    BigDecimal schon = benutzt.getOrDefault(p, Map.of()).getOrDefault(tag, NULL);
                    BigDecimal frei = kapazitaet.subtract(schon).max(NULL);
                    if (kapazitaet.signum() <= 0) {
                        String grund = p < 0 ? null : kap.grund(p, tag, e.buero());
                        if (grund != null) {
                            verluste.computeIfAbsent(kap.name(p) + ": " + grund, x -> new TreeSet<>()).add(tag);
                        }
                        continue;
                    }
                    if (frei.compareTo(kapazitaet) < 0) { // andere Vorgänge haben schon Zeit weggenommen
                        List<Belegt> wer = belegtVon.getOrDefault(p, Map.of()).getOrDefault(tag, List.of());
                        String text = wer.isEmpty() ? "anderswo eingeplant" : wer.stream().map(Belegt::text).distinct().reduce((a, b) -> a + ", " + b).orElse("");
                        verluste.computeIfAbsent(kap.name(p) + ": " + text, x -> new TreeSet<>()).add(tag);
                    }
                    BigDecimal nimm = frei.min(rest);
                    if (nimm.signum() > 0) {
                        rest = rest.subtract(nimm);
                        benutzt.computeIfAbsent(p, x -> new HashMap<>()).merge(tag, nimm, BigDecimal::add);
                        belegtVon.computeIfAbsent(p, x -> new HashMap<>()).computeIfAbsent(tag, x -> new ArrayList<>())
                                .add(new Belegt(e.auftrag() + (stufe(e.prioritaet()) == 0 ? " (dringend)" : ""), nimm));
                        zuteilungen.add(new Zuteilung(e.id(), p < 0 ? null : p, tag, nimm));
                        if (erster == null) {
                            erster = tag;
                        }
                        letzter = tag;
                    }
                }
                if (rest.signum() > 0) {
                    tag = tag.plusDays(1);
                }
            }
            boolean planbar = rest.signum() <= 0;
            if (!planbar) {
                letzter = tag;
            }
            if (erster == null) {
                erster = fruehestens;
                letzter = letzter == null ? fruehestens : letzter;
            }
            List<String> hinweise = new ArrayList<>();
            if (!planbar) {
                hinweise.add("Keine freie Zeit in den nächsten " + (MAX_TAGE / 365) + " Jahren gefunden.");
            }
            LocalDate ende = letzter;
            verluste.forEach((was, tage) -> {
                List<LocalDate> imVorgang = tage.stream().filter(t -> !t.isAfter(ende)).toList();
                if (!imVorgang.isEmpty() && hinweise.size() < 4) {
                    String daten = imVorgang.stream().limit(3).map(t -> t.format(TAG)).reduce((a, b) -> a + ", " + b).orElse("");
                    hinweise.add(was + " am " + daten + (imVorgang.size() > 3 ? " …" : ""));
                }
            });
            ergebnis.put(e.id(), new Ergebnis(angezeigterStart != null ? angezeigterStart : erster, letzter, fixiert, false, ohneZuweisung, planbar, hinweise));
        }
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
