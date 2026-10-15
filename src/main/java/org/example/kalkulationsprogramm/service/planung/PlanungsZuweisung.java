package org.example.kalkulationsprogramm.service.planung;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.planung.PlanRessource;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanRessourceRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangRepository;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#323), Stufe 1: Wer macht einen Vorgang (Mitarbeiter und/oder Ressource) und wie wichtig ist er. Die Zuweisung ändert keine Termine; sie ist die Grundlage
 * für die Ansicht "Mitarbeiter und Ressourcen". Das Programm weist nie von selbst zu.
 */
@Service
@RequiredArgsConstructor
public class PlanungsZuweisung {

    static final Set<String> ARTEN = Set.of("ORT", "EXTERN", "FAHRZEUG", "MASCHINE", "SONSTIGE");
    static final Set<String> PRIORITAETEN = Set.of(PlanVorgang.PRIO_DRINGEND, PlanVorgang.PRIO_NORMAL, PlanVorgang.PRIO_NICHT_DRINGEND);
    private static final int MAX_MITARBEITER = 50;

    private final PlanRessourceRepository ressourcen;
    private final PlanVorgangRepository vorgaenge;
    private final MitarbeiterRepository mitarbeiter;
    private final PlanungsService planung;
    private final PlanungsHistorie historie;

    /** Nur Nummer und Name, ohne Lohn oder Anschrift: für die Auswahl in der Plantafel. */
    public record PersonDto(Long id, String name) { }

    public record RessourceDto(Long id, String name, String art, int sortierung, boolean aktiv) { }

    /** Leere Liste = niemand; {@code ressourceId} leer = keine Ressource; {@code prioritaet} leer = unverändert. */
    public record Zuweisung(List<Long> mitarbeiterIds, Long ressourceId, String prioritaet, Integer stand) {
        public Zuweisung(List<Long> mitarbeiterIds, Long ressourceId, String prioritaet) {
            this(mitarbeiterIds, ressourceId, prioritaet, null);
        }
    }

    @Transactional(readOnly = true)
    public List<PersonDto> personen() {
        return mitarbeiter.findAll().stream().filter(m -> !Boolean.FALSE.equals(m.getAktiv()))
                .map(m -> new PersonDto(m.getId(), ((m.getVorname() == null ? "" : m.getVorname()) + " " + (m.getNachname() == null ? "" : m.getNachname())).strip()))
                .sorted(java.util.Comparator.comparing(PersonDto::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public List<RessourceDto> ressourcen() {
        return ressourcen.findAllByOrderBySortierungAscNameAsc().stream().map(PlanungsZuweisung::dto).toList();
    }

    @Transactional
    public RessourceDto ressourceSpeichern(Long id, String name, String art, Integer sortierung, Boolean aktiv) {
        PlanRessource r = id == null ? new PlanRessource() : ressourcen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Ressource gibt es nicht."));
        if (id == null || name != null) {
            String n = name == null ? "" : name.strip();
            if (n.isEmpty()) {
                throw new IllegalArgumentException("Bitte einen Namen eingeben.");
            }
            if (n.length() > 100) {
                throw new IllegalArgumentException("Der Name ist zu lang (höchstens 100 Zeichen).");
            }
            if (ressourcen.findAll().stream().anyMatch(x -> x.getName().equalsIgnoreCase(n) && !x.getId().equals(r.getId()))) {
                throw new IllegalArgumentException("Eine Ressource mit dem Namen „" + n + "“ gibt es schon.");
            }
            r.setName(n);
        }
        if (art != null) {
            String a = art.strip().toUpperCase(Locale.ROOT);
            if (!ARTEN.contains(a)) {
                throw new IllegalArgumentException("Die Art muss ORT, EXTERN, FAHRZEUG, MASCHINE oder SONSTIGE sein.");
            }
            r.setArt(a);
        }
        if (sortierung != null) {
            r.setSortierung(sortierung);
        }
        if (aktiv != null) {
            r.setAktiv(aktiv);
        }
        return dto(ressourcen.save(r));
    }

    @Transactional
    public AuftragDto zuweisen(Long vorgangId, Zuweisung z) {
        PlanVorgang v = vorgaenge.findById(vorgangId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Vorgang gibt es nicht."));
        if (z == null) {
            throw new IllegalArgumentException("Es fehlen die Angaben.");
        }
        planung.pruefeStand(v, z.stand());
        java.util.Map<String, Object> vorher = historie.zustand(v);
        Set<Long> ids = new HashSet<>(z.mitarbeiterIds() == null ? List.of() : z.mitarbeiterIds());
        if (ids.size() > MAX_MITARBEITER) {
            throw new IllegalArgumentException("Es können höchstens " + MAX_MITARBEITER + " Mitarbeiter zugewiesen werden.");
        }
        for (Long id : ids) {
            if (id == null || !mitarbeiter.existsById(id)) {
                throw new IllegalArgumentException("Einen der Mitarbeiter gibt es nicht.");
            }
        }
        if (z.ressourceId() != null && !z.ressourceId().equals(v.getRessourceId())) {
            PlanRessource r = ressourcen.findById(z.ressourceId()).orElseThrow(() -> new IllegalArgumentException("Die Ressource gibt es nicht."));
            if (!r.isAktiv()) {
                throw new IllegalArgumentException("Die Ressource „" + r.getName() + "“ ist ausgeblendet.");
            }
        }
        if (z.prioritaet() != null && !z.prioritaet().isBlank()) {
            String p = z.prioritaet().strip().toUpperCase(Locale.ROOT);
            if (!PRIORITAETEN.contains(p)) {
                throw new IllegalArgumentException("Die Priorität muss DRINGEND, NORMAL oder NICHT_DRINGEND sein.");
            }
            v.setPrioritaet(p);
        }
        v.setRessourceId(z.ressourceId());
        v.getMitarbeiterIds().clear();
        v.getMitarbeiterIds().addAll(ids);
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.saveAndFlush(v);
        historie.protokolliere(v, vorher);
        planung.neuBerechnen(v.getProjekt().getId(), java.time.LocalDate.now()); // wer den Vorgang macht, bestimmt, wann er fertig wird
        return planung.auftrag(v.getProjekt().getId());
    }

    private static RessourceDto dto(PlanRessource r) {
        return new RessourceDto(r.getId(), r.getName(), r.getArt(), r.getSortierung(), r.isAktiv());
    }
}
