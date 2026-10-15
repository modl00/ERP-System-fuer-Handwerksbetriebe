package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.planung.PlanArbeitsgangArt;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanArbeitsgangArtRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Stempelung als Fortschritt (Issue #331, Abschnitt 3.10): Die gestempelten Arbeitsstunden eines Auftrags zählen vom Aufwand seiner Vorgänge ab; nur der Rest wird noch verplant.
 * <p>Wie eine Stempelung zu einem Vorgang kommt: über die <b>Abteilung des Arbeitsgangs</b> (die Vorgangsart rechnet ihren Aufwand aus der Abteilung, „Arbeitszeit aus“).
 * Teilen sich mehrere Arten eine Abteilung, legt die Zuordnung „Arbeitsgang → Vorgangsart“ fest, wohin ein Arbeitsgang zählt; ohne Zuordnung zählt er für die erste Art
 * der Abteilung. Hat ein Auftrag mehrere Vorgänge derselben Art, füllen die Stunden sie nacheinander (der Rest landet im letzten).</p>
 */
@Service
@RequiredArgsConstructor
public class PlanungsIst {

    private static final BigDecimal UNBEGRENZT = new BigDecimal("1000000");

    private final ZeitbuchungRepository zeit;
    private final PlanArbeitsgangArtRepository zuordnung;
    private final PlanVorgangstypRepository typen;
    private final ArbeitsgangRepository arbeitsgaenge;

    /** Ein Arbeitsgang mit seiner Abteilung und der Vorgangsart, zu der er zählt ({@code typId} leer = automatisch über die Abteilung). {@code mehrdeutig}: mehrere Arten teilen die Abteilung. */
    public record Zuordnung(Long arbeitsgangId, String arbeitsgang, Long abteilungId, String abteilung, Long typId, boolean mehrdeutig) { }

    /** Gestempelte Stunden je Vorgang (nur Vorgänge mit Stempelung); Wartevorgänge haben keine. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> jeVorgang(Collection<PlanVorgang> vorgaenge) {
        Map<Long, List<PlanVorgang>> nachProjekt = new HashMap<>();
        for (PlanVorgang v : vorgaenge) {
            if (!v.getTyp().isWartet()) {
                nachProjekt.computeIfAbsent(v.getProjekt().getId(), x -> new ArrayList<>()).add(v);
            }
        }
        Map<Long, BigDecimal> ergebnis = new HashMap<>();
        if (nachProjekt.isEmpty()) {
            return ergebnis;
        }
        List<Object[]> zeilen = zeit.arbeitsstundenJeProjektUndArbeitsgang(nachProjekt.keySet());
        if (zeilen.isEmpty()) {
            return ergebnis;
        }
        Map<Long, Long> festgelegt = new HashMap<>();
        zuordnung.findAll().forEach(z -> festgelegt.put(z.getArbeitsgangId(), z.getTypId()));
        Map<Long, Long> artJeAbteilung = artJeAbteilung();
        Set<Long> ids = new HashSet<>();
        zeilen.forEach(z -> ids.add((Long) z[1]));
        Map<Long, Long> abteilungJeArbeitsgang = new HashMap<>();
        for (Arbeitsgang g : arbeitsgaenge.findAllById(ids)) {
            if (g.getAbteilung() != null) {
                abteilungJeArbeitsgang.put(g.getId(), g.getAbteilung().getId());
            }
        }
        Map<Long, Map<Long, BigDecimal>> jeProjektUndArt = new HashMap<>();
        for (Object[] z : zeilen) {
            Long projektId = (Long) z[0];
            Long arbeitsgangId = (Long) z[1];
            BigDecimal stunden = (BigDecimal) z[2];
            Long typ = festgelegt.get(arbeitsgangId);
            if (typ == null) {
                Long abteilung = abteilungJeArbeitsgang.get(arbeitsgangId);
                typ = abteilung == null ? null : artJeAbteilung.get(abteilung);
            }
            if (typ != null && stunden != null && stunden.signum() > 0) {
                jeProjektUndArt.computeIfAbsent(projektId, x -> new HashMap<>()).merge(typ, stunden, BigDecimal::add);
            }
        }
        for (Map.Entry<Long, Map<Long, BigDecimal>> p : jeProjektUndArt.entrySet()) {
            for (Map.Entry<Long, BigDecimal> a : p.getValue().entrySet()) {
                List<PlanVorgang> passende = nachProjekt.getOrDefault(p.getKey(), List.of()).stream().filter(v -> v.getTyp().getId().equals(a.getKey()))
                        .sorted(Comparator.comparingInt(PlanVorgang::getSortierung).thenComparing(PlanVorgang::getId)).toList();
                BigDecimal rest = a.getValue();
                for (int i = 0; i < passende.size() && rest.signum() > 0; i++) {
                    PlanVorgang v = passende.get(i);
                    BigDecimal platz = v.getAufwandStunden() == null ? UNBEGRENZT : v.getAufwandStunden();
                    BigDecimal nimm = i == passende.size() - 1 ? rest : rest.min(platz); // der Rest, der in keinen Vorgang mehr passt, steht beim letzten
                    ergebnis.merge(v.getId(), nimm, BigDecimal::add);
                    rest = rest.subtract(nimm);
                }
            }
        }
        return ergebnis;
    }

    /** Die erste (nach Reihenfolge) Arbeitsvorgangsart je Abteilung. */
    private Map<Long, Long> artJeAbteilung() {
        Map<Long, Long> je = new HashMap<>();
        for (PlanVorgangstyp t : typen.findAllByOrderBySortierungAscNameAsc()) {
            if (t.getAbteilungId() != null && !t.isWartet()) {
                je.putIfAbsent(t.getAbteilungId(), t.getId());
            }
        }
        return je;
    }

    /** Alle Arbeitsgänge mit ihrer Zuordnung, für die Einstellung. Nur Arbeitsgänge einer Abteilung, die eine Vorgangsart als Quelle hat, sind interessant. */
    @Transactional(readOnly = true)
    public List<Zuordnung> zuordnungen() {
        Map<Long, Long> festgelegt = new HashMap<>();
        zuordnung.findAll().forEach(z -> festgelegt.put(z.getArbeitsgangId(), z.getTypId()));
        Map<Long, Integer> artenJeAbteilung = new HashMap<>();
        typen.findAll().forEach(t -> {
            if (t.getAbteilungId() != null && !t.isWartet() && t.isAktiv()) {
                artenJeAbteilung.merge(t.getAbteilungId(), 1, Integer::sum);
            }
        });
        List<Zuordnung> liste = new ArrayList<>();
        for (Arbeitsgang g : arbeitsgaenge.findAll()) {
            if (g.isArchiviert()) {
                continue;
            }
            Long abteilung = g.getAbteilung() == null ? null : g.getAbteilung().getId();
            liste.add(new Zuordnung(g.getId(), g.getBeschreibung(), abteilung, g.getAbteilung() == null ? null : g.getAbteilung().getName(), festgelegt.get(g.getId()),
                    abteilung != null && artenJeAbteilung.getOrDefault(abteilung, 0) > 1));
        }
        liste.sort(Comparator.comparing((Zuordnung z) -> z.abteilung() == null ? "" : z.abteilung(), String.CASE_INSENSITIVE_ORDER).thenComparing(z -> z.arbeitsgang() == null ? "" : z.arbeitsgang(), String.CASE_INSENSITIVE_ORDER));
        return liste;
    }

    /** Legt fest, zu welcher Vorgangsart ein Arbeitsgang zählt ({@code typId} leer = automatisch über die Abteilung). */
    @Transactional
    public void zuordnen(Long arbeitsgangId, Long typId) {
        if (!arbeitsgaenge.existsById(arbeitsgangId)) {
            throw new IllegalArgumentException("Den Arbeitsgang gibt es nicht.");
        }
        if (typId == null) {
            zuordnung.findById(arbeitsgangId).ifPresent(zuordnung::delete);
            return;
        }
        PlanVorgangstyp t = typen.findById(typId).orElseThrow(() -> new IllegalArgumentException("Die Vorgangsart gibt es nicht."));
        if (t.isWartet()) {
            throw new IllegalArgumentException("Auf einen Wartevorgang wird nicht gestempelt.");
        }
        PlanArbeitsgangArt z = zuordnung.findById(arbeitsgangId).orElseGet(PlanArbeitsgangArt::new);
        z.setArbeitsgangId(arbeitsgangId);
        z.setTypId(typId);
        zuordnung.save(z);
    }
}
