package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulAuswahlDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prüft, ob eine Auswahl von Modulen fachlich zusammenpasst. Dieselbe Regel gilt an der Tür
 * (Vorbelegung) und beim Anlegen eines Protokolls:
 * <ul>
 *   <li>höchstens ein Basis-Element (Brandschutztür <em>oder</em> Rauchschutztür ...),</li>
 *   <li>ein Zusatzmodul nur an ein passendes Basis-Element,</li>
 *   <li>hat ein Modul Bauarten, ist genau eine davon zu wählen, sonst darf keine angegeben sein.</li>
 * </ul>
 */
@Service
@AllArgsConstructor
public class WartungModulAuswahlPruefer {

    private final WartungModulRepository modulRepository;
    private final WartungModulUntertypRepository untertypRepository;

    /** Ein geprüftes Modul samt gewählter Bauart ({@code untertyp} ist {@code null}, wenn es keine gibt). */
    public record GewaehltesModul(WartungModul modul, WartungModulUntertyp untertyp) { }

    /**
     * @param mindestensEins {@code true} beim Anlegen eines Protokolls, {@code false} an der Tür,
     *                       wo die Vorbelegung auch leer bleiben darf
     * @return die geprüfte Auswahl, Basis-Element zuerst, dann die Zusatzmodule in Katalogreihenfolge
     */
    @Transactional(readOnly = true)
    public List<GewaehltesModul> pruefe(List<WartungModulAuswahlDto> auswahl, boolean mindestensEins) {
        List<WartungModulAuswahlDto> liste = auswahl == null ? List.of() : auswahl;
        if (liste.isEmpty()) {
            if (mindestensEins) {
                throw falsch("Bitte mindestens ein Modul wählen.");
            }
            return List.of();
        }

        Set<Long> ids = new HashSet<>();
        for (WartungModulAuswahlDto eintrag : liste) {
            if (!ids.add(eintrag.modulId())) {
                throw falsch("Ein Modul wurde doppelt gewählt.");
            }
        }

        Map<Long, WartungModul> module = modulRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(WartungModul::getId, m -> m));
        for (Long id : ids) {
            WartungModul modul = module.get(id);
            if (modul == null) {
                throw falsch("Ein gewähltes Modul gibt es nicht mehr. Bitte die Auswahl neu treffen.");
            }
            if (!modul.isAktiv()) {
                throw falsch("Das Modul „" + modul.getName() + "“ ist nicht mehr aktiv.");
            }
        }

        List<WartungModul> basis = module.values().stream().filter(WartungModul::istBasis).toList();
        if (basis.size() > 1) {
            String namen = basis.stream().map(WartungModul::getName).sorted().collect(Collectors.joining(", "));
            throw falsch("Es darf nur ein Basis-Element gewählt werden (zum Beispiel Brandschutztür ODER "
                    + "Rauchschutztür). Gewählt wurden: " + namen + ".");
        }
        pruefeAndocken(module.values(), basis);

        Map<Long, List<WartungModulUntertyp>> untertypen = untertypRepository
                .findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulUntertyp::getModulId));

        List<GewaehltesModul> ergebnis = new ArrayList<>();
        for (WartungModulAuswahlDto eintrag : liste) {
            WartungModul modul = module.get(eintrag.modulId());
            ergebnis.add(new GewaehltesModul(modul, waehleUntertyp(modul, eintrag.untertypSchluessel(),
                    untertypen.getOrDefault(modul.getId(), List.of()))));
        }
        ergebnis.sort(Comparator
                .comparing((GewaehltesModul g) -> g.modul().istBasis() ? 0 : 1)
                .thenComparingInt(g -> g.modul().getSortierung())
                .thenComparing(g -> g.modul().getId()));
        return ergebnis;
    }

    private void pruefeAndocken(Iterable<WartungModul> alle, List<WartungModul> basis) {
        Set<Long> basisIds = basis.stream().map(WartungModul::getId).collect(Collectors.toSet());
        for (WartungModul modul : alle) {
            if (modul.istBasis() || modul.getAndockbarAnBasisModulIds().isEmpty()) {
                continue;
            }
            boolean passt = modul.getAndockbarAnBasisModulIds().stream().anyMatch(basisIds::contains);
            if (!passt) {
                String erlaubt = modulRepository.findAllById(modul.getAndockbarAnBasisModulIds()).stream()
                        .map(WartungModul::getName).sorted().collect(Collectors.joining(", "));
                throw falsch("„" + modul.getName() + "“ passt nicht zum gewählten Basis-Element. "
                        + "Es lässt sich nur ergänzen bei: " + erlaubt + ".");
            }
        }
    }

    private WartungModulUntertyp waehleUntertyp(WartungModul modul, String schluessel, List<WartungModulUntertyp> moegliche) {
        boolean leer = schluessel == null || schluessel.isBlank();
        if (moegliche.isEmpty()) {
            if (!leer) {
                throw falsch("Für „" + modul.getName() + "“ gibt es keine Bauart zur Auswahl.");
            }
            return null;
        }
        if (leer) {
            throw falsch("Bitte die Bauart für „" + modul.getName() + "“ wählen.");
        }
        Map<String, WartungModulUntertyp> nachSchluessel = new HashMap<>();
        moegliche.forEach(u -> nachSchluessel.put(u.getSchluessel(), u));
        WartungModulUntertyp gewaehlt = nachSchluessel.get(schluessel.trim());
        if (gewaehlt == null) {
            throw falsch("Die gewählte Bauart gibt es für „" + modul.getName() + "“ nicht.");
        }
        return gewaehlt;
    }

    private static ResponseStatusException falsch(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }
}
