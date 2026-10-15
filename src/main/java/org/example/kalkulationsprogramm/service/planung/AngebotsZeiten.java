package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.LeistungMengen;
import org.example.kalkulationsprogramm.service.LeistungKalkulationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Die Arbeitszeit eines Auftrags nach <b>Abteilung</b> (#184): Aus den Leistungen im Angebot oder in der Auftragsbestätigung und ihrer Kalkulation (Rüst- und
 * Bearbeitungszeiten der Arbeitsgänge, auch in Baugruppen). Rechnet genau wie die Kalkulation der Leistung: {@link LeistungKalkulationService#fuerAngebotsmenge}
 * verteilt die Rüstzeit auf die Angebotsmenge; die Stunden je Einheit mal Menge ergeben die Gesamtstunden. Ändert nichts.
 */
@Service
@RequiredArgsConstructor
public class AngebotsZeiten {

    static final int MAX_TIEFE = 5;

    private final AusgangsGeschaeftsDokumentService dokumente;
    private final LeistungKalkulationService kalkulation;
    private final ArbeitsgangRepository arbeitsgaenge;

    /**
     * Eine Fremdleistung im Auftrag (Verzinken, Pulvern …): das Angebot des Veredlers aus der Kalkulation der Leistung. {@code einheit} ist die Abrechnungseinheit
     * des Veredlers ("kg", "m²" …), {@code menge} die Menge für den ganzen Auftrag in dieser Einheit.
     */
    public record Fremd(Long fremdleistungId, String bezeichnung, String veredler, String einheit, BigDecimal menge) { }

    /** {@code stundenJeAbteilung}: Abteilung (Id) → Stunden; {@code hinweise}: Was sich nicht berechnen ließ; {@code fremdleistungen}: die Fremdleistungen des Auftrags. */
    public record Zeiten(String quelle, Map<Long, BigDecimal> stundenJeAbteilung, List<String> hinweise, List<Fremd> fremdleistungen) {
        public Zeiten(String quelle, Map<Long, BigDecimal> stundenJeAbteilung, List<String> hinweise) {
            this(quelle, stundenJeAbteilung, hinweise, List.of());
        }
    }

    @Transactional(readOnly = true)
    public Zeiten fuerProjekt(Long projektId) {
        LeistungMengen lm = dokumente.leistungMengenFuerProjekt(projektId);
        Map<Long, BigDecimal> proArbeitsgang = new LinkedHashMap<>();
        Map<Long, Fremd> fremd = new LinkedHashMap<>();
        List<String> hinweise = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : lm.mengen().entrySet()) {
            if (e.getValue() == null || e.getValue().signum() <= 0) {
                continue;
            }
            sammle(e.getKey(), e.getValue(), 0, new HashSet<>(), proArbeitsgang, hinweise, fremd);
        }
        Map<Long, BigDecimal> proAbteilung = new HashMap<>();
        if (!proArbeitsgang.isEmpty()) {
            for (Arbeitsgang g : arbeitsgaenge.findAllById(proArbeitsgang.keySet())) {
                if (g.getAbteilung() != null) {
                    proAbteilung.merge(g.getAbteilung().getId(), proArbeitsgang.get(g.getId()), BigDecimal::add);
                }
            }
        }
        proAbteilung.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        return new Zeiten(lm.quelle(), proAbteilung, hinweise, new ArrayList<>(fremd.values()));
    }

    /** Stunden je Arbeitsgang einer Leistung für die Menge; Baugruppen werden aufgelöst (höchstens {@value #MAX_TIEFE} Ebenen, ohne Kreis). */
    private void sammle(Long leistungId, BigDecimal menge, int tiefe, Set<Long> weg, Map<Long, BigDecimal> summe, List<String> hinweise, Map<Long, Fremd> fremd) {
        if (tiefe > MAX_TIEFE || !weg.add(leistungId)) {
            hinweise.add("Eine Baugruppe ist zu tief verschachtelt oder verweist auf sich selbst; ihre Arbeitszeit fehlt.");
            return;
        }
        try {
            LeistungKalkulationDto dto = kalkulation.fuerAngebotsmenge(leistungId, menge);
            for (LeistungKalkulationDto.Position p : dto.positionen()) {
                if ("ARBEIT".equals(p.typ()) && p.arbeitsgangId() != null && p.menge() != null) {
                    // menge = Stunden je Einheit der Leistung (Rüstzeit auf die Angebotsmenge verteilt)
                    summe.merge(p.arbeitsgangId(), p.menge().multiply(menge), BigDecimal::add);
                } else if ("BAUGRUPPE".equals(p.typ()) && p.baugruppeId() != null && p.menge() != null && p.menge().signum() > 0) {
                    sammle(p.baugruppeId(), p.menge().multiply(menge), tiefe + 1, weg, summe, hinweise, fremd);
                } else if ("FREMDLEISTUNG".equals(p.typ()) && p.fremdleistungId() != null && p.menge() != null) {
                    // menge = Menge in der Abrechnungseinheit des Veredlers je Einheit der Leistung; mal Auftragsmenge = Menge des Auftrags
                    BigDecimal gesamt = p.menge().multiply(menge);
                    fremd.merge(p.fremdleistungId(), new Fremd(p.fremdleistungId(), p.bezeichnung(), p.veredler(), p.einheit(), gesamt),
                            (a, b) -> new Fremd(a.fremdleistungId(), a.bezeichnung(), a.veredler(), a.einheit(), a.menge().add(b.menge())));
                }
            }
        } catch (RuntimeException e) {
            hinweise.add("Die Arbeitszeit einer Leistung (Nr. " + leistungId + ") ließ sich nicht berechnen: " + (e.getMessage() == null ? "unvollständige Kalkulation" : e.getMessage()));
        } finally {
            weg.remove(leistungId);
        }
    }
}
