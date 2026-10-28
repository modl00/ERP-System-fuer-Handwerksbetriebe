package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.norm;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.KundeInfo;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.Katalog;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.ModulInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * Übernahme von Wartungsprotokollen aus HandwerkPro, Schritt 1: die <b>Vorschau</b>. Sie liest nur (Katalog, Kunden, Objekte) und schreibt nichts. Erst wenn die
 * Vorschau geprüft und entschieden ist, folgt der eigentliche Import.
 */
@Service
@RequiredArgsConstructor
public class WartungImportService {

    private static final int MAX_WARTUNGEN = 5000;

    private final WartungModulRepository module;
    private final WartungModulUntertypRepository untertypen;
    private final WartungModulPruefpunktRepository pruefpunkte;
    private final WartungGlobalesDatenfeldRepository globaleFelder;
    private final WartungModulDatenfeldRepository modulFelder;
    private final KundeRepository kunden;
    private final WartungObjektRepository objekte;
    private final WartungTuerRepository tueren;

    @Transactional(readOnly = true)
    public Vorschau vorschau(JsonNode export) {
        pruefeExport(export);
        return WartungImportAnalyse.analysiere(export, katalogLaden());
    }

    /** Prüft, dass es eine Exportdatei aus HandwerkPro ist und nicht zu groß. */
    void pruefeExport(JsonNode export) {
        if (export == null || !"HandwerkPro".equals(export.path("quelle").asText()) || !export.path("wartungen").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das ist keine Exportdatei aus HandwerkPro (tools/wartung-export-handwerkpro.mjs).");
        }
        if (export.path("wartungen").size() > MAX_WARTUNGEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Datei enthält mehr als " + MAX_WARTUNGEN + " Wartungen.");
        }
    }

    Katalog katalogLaden() {
        List<WartungModul> alle = module.findAllByOrderBySortierungAscIdAsc();
        List<Long> ids = alle.stream().map(WartungModul::getId).toList();
        Map<Long, Map<String, String>> ut = new LinkedHashMap<>();
        for (WartungModulUntertyp u : untertypen.findByModulIdInOrderBySortierungAscIdAsc(ids)) {
            ut.computeIfAbsent(u.getModulId(), k -> new LinkedHashMap<>()).put(u.getSchluessel(), u.getLabel());
        }
        Map<Long, Set<String>> pp = new LinkedHashMap<>();
        for (WartungModulPruefpunkt p : pruefpunkte.findByModulIdInOrderBySortierungAscIdAsc(ids)) {
            pp.computeIfAbsent(p.getModulId(), k -> new HashSet<>()).add(norm(p.getLabel()));
        }
        Map<String, ModulInfo> infos = new LinkedHashMap<>();
        for (WartungModul m : alle) {
            infos.put(m.getModulKey(), new ModulInfo(m.getModulKey(), m.getName(), m.getKategorie() == WartungModulKategorie.BASIS,
                    ut.getOrDefault(m.getId(), Map.of()), pp.getOrDefault(m.getId(), Set.of())));
        }
        Map<String, Set<String>> optionen = new LinkedHashMap<>();
        Set<String> felder = new HashSet<>();
        for (var g : globaleFelder.findAllByOrderBySortierungAscIdAsc()) {
            felder.add(norm(g.getLabel()));
            sammle(optionen, g.getLabel(), g.getOptionen());
        }
        for (var f : modulFelder.findByModulIdInOrderBySortierungAscIdAsc(ids)) {
            felder.add(norm(f.getLabel()));
            sammle(optionen, f.getLabel(), f.getOptionen());
        }
        List<KundeInfo> kundeInfos = kunden.findAll().stream().map(k -> new KundeInfo(k.getId(), k.getName() == null ? "" : k.getName(), k.getKundennummer())).toList();
        Set<String> objektSchluessel = new HashSet<>();
        Set<String> tuerSchluessel = new HashSet<>();
        for (KundeInfo k : kundeInfos) {
            for (var o : objekte.findByKundeIdOrderByBezeichnungAscIdAsc(k.id())) {
                objektSchluessel.add(WartungImportAnalyse.objektSchluessel(k.id(), o.getBezeichnung()));
                for (var t : tueren.findByObjektIdOrderByBezeichnungAscIdAsc(o.getId())) {
                    tuerSchluessel.add(WartungImportAnalyse.tuerSchluessel(k.id(), o.getBezeichnung(), t.getBezeichnung()));
                }
            }
        }
        return new Katalog(infos, optionen, felder, kundeInfos, objektSchluessel, tuerSchluessel);
    }

    /** Merkt die Auswahlmöglichkeiten eines Felds ohne "keine Angabe" und "Sonstiges" (die sind keine Werte, die ein alter Wert treffen soll). */
    private static void sammle(Map<String, Set<String>> optionen, String label, String roh) {
        if (roh == null || roh.isBlank()) {
            return;
        }
        Set<String> s = optionen.computeIfAbsent(label, k -> new java.util.LinkedHashSet<>());
        for (String o : roh.split("\n")) {
            String t = o.strip();
            if (!t.isEmpty() && !t.equalsIgnoreCase(WartungImportAbbildung.KEINE_ANGABE) && !t.equalsIgnoreCase("Sonstiges")) {
                s.add(t);
            }
        }
    }
}
