package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Liefert der Stundenrechnung ({@link PlanungsStunden}) die freie Zeit jeder Person (Issue #331, Abschnitt 3.2):
 * <pre>Kapazität = Arbeitstag der Person − Abwesenheit − feste Termine, und an Bürotagen keine Stunden für Fertigung und Montage.</pre>
 * Der Arbeitstag kommt aus dem Zeitkonto der Person (Sollstunden je Wochentag). Hat eine Person gar kein Zeitkonto, gilt der eingestellte Standard-Arbeitstag.
 * Feiertage, Arbeitswoche und Betriebsurlaub gelten für alle.
 */
@Service
@RequiredArgsConstructor
public class PlanKapazitaetQuelle {

    private final TagesSollService tagesSoll;
    private final AbwesenheitRepository abwesenheiten;
    private final KalenderEintragRepository kalender;
    private final MitarbeiterRepository mitarbeiter;
    private final ZeitbuchungRepository zeitbuchungen;

    /** Alles, was die Rechnung über freie Zeit wissen muss, für einen Zeitraum und einen Kreis von Personen im Voraus geladen. */
    @Transactional(readOnly = true)
    public PlanungsStunden.Kapazitaet laden(Set<Long> personen, LocalDate von, LocalDate bis, Predicate<LocalDate> arbeitstag, PlanungsEinstellungen.Rhythmus rhythmus) {
        LocalDate heute = von;
        Map<Long, String> namen = new HashMap<>();
        for (Mitarbeiter m : mitarbeiter.findAllById(personen)) {
            namen.put(m.getId(), ((m.getVorname() == null ? "" : m.getVorname()) + " " + (m.getNachname() == null ? "" : m.getNachname())).strip());
        }
        Map<Long, Map<LocalDate, BigDecimal>> soll = new HashMap<>();
        for (Long id : personen) {
            Map<LocalDate, BigDecimal> tage = tagesSoll.arbeitsSollJeTag(id, von, bis);
            boolean keinZeitkonto = tage.values().stream().allMatch(h -> h == null || h.signum() <= 0);
            soll.put(id, keinZeitkonto ? null : tage);
        }
        Map<Long, Map<LocalDate, Abwesenheit>> weg = new HashMap<>();
        for (Abwesenheit a : abwesenheiten.findAllByDatumBetween(von, bis)) {
            if (a.getDatum() != null && personen.contains(a.getMitarbeiter().getId())) {
                weg.computeIfAbsent(a.getMitarbeiter().getId(), x -> new HashMap<>()).put(a.getDatum(), a);
            }
        }
        Map<Long, Map<LocalDate, List<Termin>>> termine = new HashMap<>();
        for (KalenderEintrag k : kalender.findeUeberlappende(von, bis)) {
            boolean ganz = k.isGanztaegig() || k.getDatumBis() != null && k.getDatumBis().isAfter(k.getDatum()) || k.getStartZeit() == null;
            BigDecimal stunden = ganz ? null : dauer(k);
            LocalDate ende = k.getDatumBis() == null ? k.getDatum() : k.getDatumBis();
            for (Mitarbeiter m : k.getTeilnehmer()) {
                if (!personen.contains(m.getId())) {
                    continue;
                }
                for (LocalDate tag = k.getDatum().isBefore(von) ? von : k.getDatum(); !tag.isAfter(ende) && !tag.isAfter(bis); tag = tag.plusDays(1)) {
                    termine.computeIfAbsent(m.getId(), x -> new HashMap<>()).computeIfAbsent(tag, x -> new java.util.ArrayList<>()).add(new Termin(k.getTitel(), stunden));
                }
            }
        }
        // Was heute schon gestempelt wurde, ist verbraucht: Der Rest des Tages ist nur noch das, was übrig bleibt
        Map<Long, BigDecimal> verbraucht = new HashMap<>();
        if (!personen.isEmpty() && !heute.isBefore(von) && !heute.isAfter(bis)) {
            for (Object[] z : zeitbuchungen.arbeitsstundenJeMitarbeiter(personen, heute.atStartOfDay(), heute.plusDays(1).atStartOfDay())) {
                verbraucht.put((Long) z[0], (BigDecimal) z[1]);
            }
        }
        return new Quelle(namen, soll, weg, termine, arbeitstag, rhythmus, heute, verbraucht);
    }

    private static BigDecimal dauer(KalenderEintrag k) {
        if (k.getEndeZeit() == null || !k.getEndeZeit().isAfter(k.getStartZeit())) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(Duration.between(k.getStartZeit(), k.getEndeZeit()).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** {@code stunden} leer = ganztägig. */
    private record Termin(String titel, BigDecimal stunden) { }

    private static final class Quelle implements PlanungsStunden.Kapazitaet {
        private final Map<Long, String> namen;
        private final Map<Long, Map<LocalDate, BigDecimal>> soll;
        private final Map<Long, Map<LocalDate, Abwesenheit>> weg;
        private final Map<Long, Map<LocalDate, List<Termin>>> termine;
        private final Predicate<LocalDate> arbeitstag;
        private final PlanungsEinstellungen.Rhythmus rhythmus;
        private final LocalDate heute;
        private final Map<Long, BigDecimal> verbraucht;

        Quelle(Map<Long, String> namen, Map<Long, Map<LocalDate, BigDecimal>> soll, Map<Long, Map<LocalDate, Abwesenheit>> weg, Map<Long, Map<LocalDate, List<Termin>>> termine,
                Predicate<LocalDate> arbeitstag, PlanungsEinstellungen.Rhythmus rhythmus, LocalDate heute, Map<Long, BigDecimal> verbraucht) {
            this.heute = heute;
            this.verbraucht = verbraucht;
            this.namen = namen;
            this.soll = soll;
            this.weg = weg;
            this.termine = termine;
            this.arbeitstag = arbeitstag;
            this.rhythmus = rhythmus;
        }

        @Override
        public boolean arbeitstag(LocalDate tag) {
            return arbeitstag.test(tag);
        }

        @Override
        public BigDecimal standard(LocalDate tag) {
            return arbeitstag(tag) ? rhythmus.arbeitstagStunden() : BigDecimal.ZERO;
        }

        @Override
        public String name(Long person) {
            return namen.getOrDefault(person, "Mitarbeiter " + person);
        }

        private BigDecimal arbeitstagDerPerson(Long person, LocalDate tag) {
            if (!arbeitstag(tag)) {
                return BigDecimal.ZERO;
            }
            Map<LocalDate, BigDecimal> tage = soll.get(person);
            return tage == null ? rhythmus.arbeitstagStunden() : tage.getOrDefault(tag, BigDecimal.ZERO);
        }

        private boolean buerotag(Long person, LocalDate tag, boolean bueroArbeit) {
            Set<DayOfWeek> tageImBuero = rhythmus.buerotage().get(person);
            return !bueroArbeit && tageImBuero != null && tageImBuero.contains(tag.getDayOfWeek());
        }

        @Override
        public BigDecimal stunden(Long person, LocalDate tag, boolean bueroArbeit) {
            BigDecimal basis = arbeitstagDerPerson(person, tag);
            if (basis.signum() <= 0 || buerotag(person, tag, bueroArbeit)) {
                return BigDecimal.ZERO;
            }
            Abwesenheit a = weg.getOrDefault(person, Map.of()).get(tag);
            if (a != null) {
                basis = a.getStunden() == null ? BigDecimal.ZERO : basis.subtract(a.getStunden());
            }
            for (Termin t : termine.getOrDefault(person, Map.of()).getOrDefault(tag, List.of())) {
                basis = t.stunden() == null ? BigDecimal.ZERO : basis.subtract(t.stunden());
            }
            if (tag.equals(heute)) {
                basis = basis.subtract(verbraucht.getOrDefault(person, BigDecimal.ZERO));
            }
            return basis.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public String grund(Long person, LocalDate tag, boolean bueroArbeit) {
            if (arbeitstagDerPerson(person, tag).signum() <= 0) {
                return null; // freier Tag laut Zeitkonto oder kein Arbeitstag: kein Grund zum Melden
            }
            if (buerotag(person, tag, bueroArbeit)) {
                return "Bürotag";
            }
            Abwesenheit a = weg.getOrDefault(person, Map.of()).get(tag);
            if (a != null) {
                return a.getTyp() == null ? "Abwesend" : switch (a.getTyp()) {
                    case URLAUB -> "Urlaub";
                    case KRANKHEIT -> "Abwesend"; // Gesundheitsdaten nicht im Klartext (Issue #331, Abschnitt 8)
                    case FORTBILDUNG -> "Fortbildung";
                    case ZEITAUSGLEICH -> "Zeitausgleich";
                };
            }
            List<Termin> t = termine.getOrDefault(person, Map.of()).get(tag);
            return t == null || t.isEmpty() ? null : "Termin „" + t.get(0).titel() + "“";
        }
    }
}
