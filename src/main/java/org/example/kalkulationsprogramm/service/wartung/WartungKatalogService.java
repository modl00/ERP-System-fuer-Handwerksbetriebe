package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Liest den Vorlagenkatalog (Module mit Bauarten) für die Auswahl in der Oberfläche. */
@Service
@AllArgsConstructor
public class WartungKatalogService {

    private final WartungModulRepository modulRepository;
    private final WartungModulUntertypRepository untertypRepository;
    private final WartungModulPruefpunktRepository pruefpunktRepository;
    private final WartungMapper mapper;

    /** Alle aktiven Module in Katalogreihenfolge, Basis-Elemente und Zusatzmodule gemischt nach Sortierung. */
    @Transactional(readOnly = true)
    public List<WartungModulDto> aktiveModule() {
        List<WartungModul> module = modulRepository.findByAktivTrueOrderBySortierungAscIdAsc();
        if (module.isEmpty()) {
            return List.of();
        }
        List<Long> ids = module.stream().map(WartungModul::getId).toList();

        Map<Long, List<WartungModulUntertyp>> untertypen = untertypRepository
                .findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulUntertyp::getModulId));
        Map<Long, Long> pruefpunkte = pruefpunktRepository
                .findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulPruefpunkt::getModulId, Collectors.counting()));

        return module.stream()
                .map(m -> mapper.toDto(m, untertypen.getOrDefault(m.getId(), List.of()),
                        pruefpunkte.getOrDefault(m.getId(), 0L).intValue()))
                .toList();
    }
}
