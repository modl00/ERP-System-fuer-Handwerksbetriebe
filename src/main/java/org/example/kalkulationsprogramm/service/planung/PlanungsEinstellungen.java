package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Einstellungen der Plantafel, die zur Firma gehören und deshalb nicht fest im Code stehen (Issue #331, Abschnitt 9): Arbeitswoche,
 * Betriebsurlaub und ab wie vielen Arbeitstagen Luft zum Zieltermin die Ampel „Knapp“ zeigt. Ohne Eintrag gelten die Werte, mit denen
 * die Plantafel bisher gerechnet hat (Montag bis Freitag, zwei Arbeitstage), damit sich nach dem Update nichts ändert.
 */
@Service
@RequiredArgsConstructor
public class PlanungsEinstellungen {

    static final String ARBEITSTAGE = "planung.arbeitstage";
    static final String AMPEL_SCHWELLE = "planung.ampelSchwelleTage";
    static final String BETRIEBSURLAUB = "planung.betriebsurlaub";
    static final String ARBEITSTAG_STUNDEN = "planung.arbeitstagStunden";
    static final String BUEROTAGE = "planung.buerotage";

    /** Stunden eines Arbeitstags, wenn für eine Person kein Zeitkonto da ist, und für Vorgänge ohne Zuweisung. */
    static final BigDecimal STANDARD_ARBEITSTAG = new BigDecimal("8");
    static final BigDecimal MAX_ARBEITSTAG = new BigDecimal("24");

    static final int MAX_SCHWELLE = 30;
    static final int MAX_URLAUBE = 100;
    /** Ein Betriebsurlaub über mehr als ein Jahr ist ein Tippfehler; außerdem hält das die Rechnung kurz. */
    static final int MAX_URLAUB_TAGE = 366;

    private static final Set<DayOfWeek> MO_BIS_FR = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private final SystemSettingRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Ein Zeitraum, in dem der ganze Betrieb nicht arbeitet. */
    public record Urlaub(LocalDate von, LocalDate bis, String bezeichnung) { }

    /** So steht ein Zeitraum in der Datenbank: Daten als Text (ISO), damit es kein Zusatzmodul für Datumstypen braucht. */
    record UrlaubJson(String von, String bis, String bezeichnung) { }

    public record Werte(Set<DayOfWeek> arbeitstage, int ampelSchwelleTage, List<Urlaub> betriebsurlaub) {

        /** Arbeitet der Betrieb an diesem Tag? Feiertage kommen von außen, weil sie aus der Datenbank stammen. */
        public boolean istArbeitstag(LocalDate tag, Set<LocalDate> feiertage) {
            return arbeitstage.contains(tag.getDayOfWeek()) && !feiertage.contains(tag) && !imBetriebsurlaub(tag);
        }

        public boolean imBetriebsurlaub(LocalDate tag) {
            return betriebsurlaub.stream().anyMatch(u -> !tag.isBefore(u.von()) && !tag.isAfter(u.bis()));
        }
    }

    public static Werte standard() {
        return new Werte(MO_BIS_FR, 2, List.of());
    }

    @Transactional(readOnly = true)
    public Werte lesen() {
        Set<DayOfWeek> tage = arbeitstageLesen();
        int schwelle = wert(AMPEL_SCHWELLE).map(PlanungsEinstellungen::alsZahl).filter(n -> n >= 0 && n <= MAX_SCHWELLE).orElse(2);
        return new Werte(tage, schwelle, urlaubLesen());
    }

    /**
     * Speichert alle Werte auf einmal.
     *
     * @throws IllegalArgumentException mit einer Meldung für den Anwender, wenn etwas nicht stimmt
     */
    @Transactional
    public Werte speichern(Set<DayOfWeek> arbeitstage, int ampelSchwelleTage, List<Urlaub> betriebsurlaub) {
        if (arbeitstage == null || arbeitstage.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Arbeitstag in der Woche ist nötig.");
        }
        if (ampelSchwelleTage < 0 || ampelSchwelleTage > MAX_SCHWELLE) {
            throw new IllegalArgumentException("Die Schwelle für „Knapp“ liegt zwischen 0 und " + MAX_SCHWELLE + " Arbeitstagen.");
        }
        List<Urlaub> urlaub = bereinigt(betriebsurlaub == null ? List.of() : betriebsurlaub);
        schreibe(ARBEITSTAGE, arbeitstage.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(",")));
        schreibe(AMPEL_SCHWELLE, String.valueOf(ampelSchwelleTage));
        try {
            schreibe(BETRIEBSURLAUB, mapper.writeValueAsString(urlaub.stream().map(u -> new UrlaubJson(u.von().toString(), u.bis().toString(), u.bezeichnung())).toList()));
        } catch (JacksonException e) {
            throw new IllegalStateException("Der Betriebsurlaub konnte nicht gespeichert werden.", e);
        }
        return lesen();
    }

    private List<Urlaub> bereinigt(List<Urlaub> eingabe) {
        if (eingabe.size() > MAX_URLAUBE) {
            throw new IllegalArgumentException("Mehr als " + MAX_URLAUBE + " Zeiträume für den Betriebsurlaub sind nicht möglich.");
        }
        List<Urlaub> ergebnis = new ArrayList<>();
        for (Urlaub u : eingabe) {
            if (u == null || u.von() == null || u.bis() == null) {
                throw new IllegalArgumentException("Beim Betriebsurlaub fehlt ein Datum.");
            }
            if (u.bis().isBefore(u.von())) {
                throw new IllegalArgumentException("Beim Betriebsurlaub liegt das Ende vor dem Beginn (" + u.von() + ").");
            }
            if (u.von().plusDays(MAX_URLAUB_TAGE).isBefore(u.bis())) {
                throw new IllegalArgumentException("Ein Betriebsurlaub darf höchstens ein Jahr dauern (" + u.von() + ").");
            }
            String name = u.bezeichnung() == null ? "" : u.bezeichnung().trim();
            ergebnis.add(new Urlaub(u.von(), u.bis(), name.length() > 80 ? name.substring(0, 80) : name));
        }
        ergebnis.sort(Comparator.comparing(Urlaub::von));
        return ergebnis;
    }

    private Set<DayOfWeek> arbeitstageLesen() {
        Set<DayOfWeek> tage = EnumSet.noneOf(DayOfWeek.class);
        for (String teil : wert(ARBEITSTAGE).orElse("").split(",")) {
            try {
                tage.add(DayOfWeek.valueOf(teil.trim()));
            } catch (IllegalArgumentException ignoriert) {
                // unbekannter Eintrag: wird übergangen
            }
        }
        return tage.isEmpty() ? MO_BIS_FR : tage;
    }

    private List<Urlaub> urlaubLesen() {
        try {
            return wert(BETRIEBSURLAUB).map(json -> {
                try {
                    return mapper.readValue(json, new TypeReference<List<UrlaubJson>>() { }).stream()
                            .map(u -> new Urlaub(LocalDate.parse(u.von()), LocalDate.parse(u.bis()), u.bezeichnung() == null ? "" : u.bezeichnung())).toList();
                } catch (JacksonException | java.time.format.DateTimeParseException e) {
                    return List.<Urlaub>of();
                }
            }).orElse(List.of());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private java.util.Optional<String> wert(String schluessel) {
        return repository.findById(schluessel).map(SystemSetting::getValue).filter(w -> w != null && !w.isBlank());
    }

    private static int alsZahl(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void schreibe(String schluessel, String wert) {
        SystemSetting s = repository.findById(schluessel).orElseGet(SystemSetting::new);
        s.setKey(schluessel);
        s.setValue(wert);
        repository.save(s);
    }

    // ───────────────────────────── Arbeitstag und Wochenrhythmus ─────────────────────────────

    /** Wochenrhythmus: an welchen Wochentagen eine Person im Büro ist (dann hat sie keine Stunden für Fertigung und Montage). */
    public record Rhythmus(BigDecimal arbeitstagStunden, java.util.Map<Long, Set<DayOfWeek>> buerotage) { }

    @Transactional(readOnly = true)
    public Rhythmus rhythmusLesen() {
        BigDecimal stunden = wert(ARBEITSTAG_STUNDEN).map(t -> {
            try {
                return new BigDecimal(t.trim());
            } catch (NumberFormatException e) {
                return STANDARD_ARBEITSTAG;
            }
        }).filter(h -> h.signum() > 0 && h.compareTo(MAX_ARBEITSTAG) <= 0).orElse(STANDARD_ARBEITSTAG);
        java.util.Map<Long, Set<DayOfWeek>> tage = new java.util.LinkedHashMap<>();
        try {
            wert(BUEROTAGE).ifPresent(json -> {
                try {
                    mapper.readValue(json, new TypeReference<java.util.Map<String, List<Integer>>>() { }).forEach((id, liste) -> {
                        Set<DayOfWeek> menge = EnumSet.noneOf(DayOfWeek.class);
                        liste.stream().filter(n -> n != null && n >= 1 && n <= 7).forEach(n -> menge.add(DayOfWeek.of(n)));
                        if (!menge.isEmpty()) {
                            tage.put(Long.valueOf(id), menge);
                        }
                    });
                } catch (JacksonException | NumberFormatException e) {
                    // kaputter Eintrag: es gilt, als gäbe es keine Bürotage
                }
            });
        } catch (RuntimeException e) {
            return new Rhythmus(stunden, java.util.Map.of());
        }
        return new Rhythmus(stunden, tage);
    }

    @Transactional
    public Rhythmus rhythmusSpeichern(BigDecimal arbeitstagStunden, java.util.Map<Long, Set<DayOfWeek>> buerotage) {
        if (arbeitstagStunden == null || arbeitstagStunden.signum() <= 0 || arbeitstagStunden.compareTo(MAX_ARBEITSTAG) > 0) {
            throw new IllegalArgumentException("Der Arbeitstag muss zwischen mehr als 0 und " + MAX_ARBEITSTAG.intValue() + " Stunden lang sein.");
        }
        java.util.Map<String, List<Integer>> json = new java.util.TreeMap<>();
        for (java.util.Map.Entry<Long, Set<DayOfWeek>> e : (buerotage == null ? java.util.Map.<Long, Set<DayOfWeek>>of() : buerotage).entrySet()) {
            if (e.getKey() == null || e.getKey() <= 0) {
                throw new IllegalArgumentException("Unbekannter Mitarbeiter bei den Bürotagen.");
            }
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                if (e.getValue().size() >= 7) {
                    throw new IllegalArgumentException("Eine Person kann nicht an allen sieben Tagen im Büro sein.");
                }
                json.put(String.valueOf(e.getKey()), e.getValue().stream().map(DayOfWeek::getValue).sorted().toList());
            }
        }
        if (json.size() > 500) {
            throw new IllegalArgumentException("Zu viele Einträge bei den Bürotagen.");
        }
        schreibe(ARBEITSTAG_STUNDEN, arbeitstagStunden.stripTrailingZeros().toPlainString());
        try {
            schreibe(BUEROTAGE, mapper.writeValueAsString(json));
        } catch (JacksonException e) {
            throw new IllegalStateException("Die Bürotage konnten nicht gespeichert werden.", e);
        }
        return rhythmusLesen();
    }
}
