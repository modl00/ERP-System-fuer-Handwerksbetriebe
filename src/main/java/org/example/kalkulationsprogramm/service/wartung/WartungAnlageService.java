package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.AppAnlageAktualisierenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungAnlageDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Die Wartung als Ganzes (Anlage, Tür oder Tor): Detailseite mit Historie der Prüfungen, Zuordnung zu einem Projekt
 * und der Bereich "Wartungen" im Projekt. Verändert nie eine bestehende Prüfung.
 */
@Service
@AllArgsConstructor
public class WartungAnlageService {

    private static final Set<WartungStatus> OFFEN = Set.of(WartungStatus.OFFEN, WartungStatus.IN_BEARBEITUNG);

    private final WartungTuerRepository tuerRepository;
    private final WartungProtokollRepository protokollRepository;
    private final WartungProtokollPruefpunktRepository pruefpunktRepository;
    private final WartungModulRepository modulRepository;
    private final WartungModulUntertypRepository untertypRepository;
    private final ProjektRepository projektRepository;
    private final ProjektDokumentRepository dokumentRepository;

    @Transactional(readOnly = true)
    public WartungAnlageDto laden(Long tuerId) {
        WartungTuer tuer = ladeTuer(tuerId);
        WartungObjekt objekt = tuer.getObjekt();
        Kunde kunde = objekt.getKunde();
        List<WartungProtokoll> protokolle = protokollRepository.findByTuerIdOrderByDatumAscIdAsc(tuerId);
        Map<Long, long[]> zahlen = zaehle(protokolle);
        Map<Long, ProjektDokument> berichte = berichte(protokolle);

        List<WartungAnlageDto.Pruefung> historie = new ArrayList<>();
        int nummer = 0;
        for (WartungProtokoll p : protokolle) {
            long[] z = zahlen.getOrDefault(p.getId(), new long[3]);
            ProjektDokument bericht = p.getBerichtDokumentId() == null ? null : berichte.get(p.getBerichtDokumentId());
            historie.add(new WartungAnlageDto.Pruefung(p.getId(), ++nummer, p.getNummer(), p.getDatum(), p.getStatus(),
                    technikerName(p.getTechniker()), z[0], z[1], z[2], p.getVorlageProtokollId(),
                    bericht == null ? null : bericht.getId(), bericht == null ? null : WartungBerichtService.dokumentUrl(bericht)));
        }
        WartungProtokoll offen = protokolle.stream().filter(p -> OFFEN.contains(p.getStatus()))
                .max(Comparator.comparing(WartungProtokoll::getId)).orElse(null);
        WartungProtokoll letzte = protokolle.stream().filter(p -> p.getAbgeschlossenAm() != null)
                .max(Comparator.comparing(WartungProtokoll::getDatum).thenComparing(WartungProtokoll::getId)).orElse(null);

        Projekt projekt = tuer.getProjekt();
        return new WartungAnlageDto(
                tuer.getId(), tuer.getBezeichnung(), tuer.getNotizen(), tuer.getIntervallMonate(), tuer.getNaechsteFaelligkeit(), tuer.isAktiv(),
                new WartungAnlageDto.Bezug(kunde.getId(), kunde.getName()),
                new WartungAnlageDto.Objekt(objekt.getId(), objekt.getBezeichnung(), objekt.getStrasse(), objekt.getPlz(), objekt.getOrt()),
                projekt == null ? null : new WartungAnlageDto.Bezug(projekt.getId(), WartungMapper.projektName(projekt)),
                module(tuer),
                offen == null ? null : offen.getId(), offen == null ? null : offen.getNummer(),
                letzte == null ? null : letzte.getId(),
                historie);
    }

    /** Aktualisiert die Stammdaten einer Anlage (Bezeichnung, Notizen, Intervall) — für den App-Feldeinsatz. */
    @Transactional
    public WartungAnlageDto aktualisieren(Long tuerId, AppAnlageAktualisierenDto dto) {
        WartungTuer tuer = ladeTuer(tuerId);
        tuer.setBezeichnung(dto.bezeichnung().trim());
        tuer.setNotizen(dto.notizen());
        tuer.setIntervallMonate(dto.intervallMonate());
        tuerRepository.save(tuer);
        return laden(tuerId);
    }

    /**
     * Ordnet die Wartung einem Projekt zu, ändert die Zuordnung oder trennt sie ({@code projektId = null}). Das ist nur
     * eine Zuordnung: Es entsteht keine zweite Wartung, und bestehende Prüfungen behalten ihr eigenes Projekt, damit
     * ein Wechsel nie rückwirkend Berichte in ein anderes Projekt schiebt.
     */
    @Transactional
    public WartungAnlageDto projektZuordnen(Long tuerId, Long projektId) {
        WartungTuer tuer = ladeTuer(tuerId);
        if (projektId == null) {
            tuer.setProjekt(null);
        } else {
            Projekt projekt = projektRepository.findById(projektId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Projekt wurde nicht gefunden."));
            Kunde projektKunde = projekt.getKundenId();
            if (projektKunde != null && !projektKunde.getId().equals(tuer.getObjekt().getKunde().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Projekt gehört zu einem anderen Kunden.");
            }
            tuer.setProjekt(projekt);
        }
        tuerRepository.save(tuer);
        return laden(tuerId);
    }

    /** Die aktiven Wartungen eines Projekts mit den dort abgelegten Berichten: nur Wartungen dieses Projekts. */
    @Transactional(readOnly = true)
    public List<WartungProjektWartungDto> projektWartungen(Long projektId) {
        if (!projektRepository.existsById(projektId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Projekt wurde nicht gefunden.");
        }
        List<WartungTuer> tueren = tuerRepository.findByProjektIdAndAktivTrueOrderByBezeichnungAscIdAsc(projektId);
        if (tueren.isEmpty()) {
            return List.of();
        }
        Map<Long, List<WartungProtokoll>> nachTuer = protokollRepository
                .findByTuerIdInOrderByDatumAscIdAsc(tueren.stream().map(WartungTuer::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getTuer().getId()));
        Map<Long, ProjektDokument> berichte = berichte(nachTuer.values().stream().flatMap(Collection::stream).toList());

        List<WartungProjektWartungDto> ergebnis = new ArrayList<>();
        for (WartungTuer tuer : tueren) {
            List<WartungProtokoll> protokolle = nachTuer.getOrDefault(tuer.getId(), List.of());
            WartungProtokoll offen = protokolle.stream().filter(p -> OFFEN.contains(p.getStatus()))
                    .max(Comparator.comparing(WartungProtokoll::getId)).orElse(null);
            WartungProtokoll letzte = protokolle.stream().filter(p -> p.getAbgeschlossenAm() != null)
                    .max(Comparator.comparing(WartungProtokoll::getDatum).thenComparing(WartungProtokoll::getId)).orElse(null);
            List<WartungProjektWartungDto.Bericht> liste = protokolle.stream()
                    .filter(p -> p.getBerichtDokumentId() != null && berichte.containsKey(p.getBerichtDokumentId()))
                    .sorted(Comparator.comparing(WartungProtokoll::getDatum).thenComparing(WartungProtokoll::getId).reversed())
                    .map(p -> {
                        ProjektDokument d = berichte.get(p.getBerichtDokumentId());
                        return new WartungProjektWartungDto.Bericht(p.getId(), p.getNummer(), p.getDatum(),
                                WartungBerichtService.dokumentUrl(d), d.getOriginalDateiname());
                    }).toList();
            ergebnis.add(new WartungProjektWartungDto(
                    tuer.getId(), tuer.getObjekt().getId(), tuer.getBezeichnung(), tuer.getObjekt().getBezeichnung(), tuer.getObjekt().getKunde().getName(),
                    module(tuer).stream().map(m -> m.bauart() == null ? m.name() : m.name() + " (" + m.bauart() + ")").collect(Collectors.joining(", ")),
                    tuer.getNaechsteFaelligkeit(), letzte == null ? null : letzte.getDatum(), protokolle.size(),
                    offen == null ? null : offen.getId(), offen == null ? null : offen.getNummer(), liste));
        }
        return ergebnis;
    }

    /** Die Projekte eines Kunden für die Auswahl bei der Zuordnung, die neuesten zuerst. Projekte anderer Kunden kommen nie vor. */
    @Transactional(readOnly = true)
    public List<WartungAnlageDto.Bezug> projekteDesKunden(Long kundeId) {
        return projektRepository.findByKundenId_Id(kundeId).stream()
                .sorted(Comparator.comparing(Projekt::getAnlegedatum, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Projekt::getId, Comparator.reverseOrder()))
                .map(p -> new WartungAnlageDto.Bezug(p.getId(), WartungMapper.projektName(p)))
                .toList();
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    private WartungTuer ladeTuer(Long id) {
        return tuerRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Wartung wurde nicht gefunden."));
    }

    /** {gesamt, bewertet, Mängel} je Protokoll, in einer Abfrage. */
    private Map<Long, long[]> zaehle(List<WartungProtokoll> protokolle) {
        Map<Long, long[]> ergebnis = new HashMap<>();
        if (protokolle.isEmpty()) {
            return ergebnis;
        }
        for (Object[] zeile : pruefpunktRepository.statistikJeProtokoll(protokolle.stream().map(WartungProtokoll::getId).toList())) {
            ergebnis.put((Long) zeile[0], new long[] {((Number) zeile[1]).longValue(), ((Number) zeile[2]).longValue(), ((Number) zeile[3]).longValue()});
        }
        return ergebnis;
    }

    private Map<Long, ProjektDokument> berichte(Collection<WartungProtokoll> protokolle) {
        List<Long> ids = protokolle.stream().map(WartungProtokoll::getBerichtDokumentId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return dokumentRepository.findAllById(ids).stream().collect(Collectors.toMap(ProjektDokument::getId, Function.identity()));
    }

    private List<WartungAnlageDto.Modul> module(WartungTuer tuer) {
        List<Long> ids = tuer.getModule().stream().map(WartungModulAuswahl::getModulId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, WartungModul> module = modulRepository.findAllById(ids).stream().collect(Collectors.toMap(WartungModul::getId, Function.identity()));
        Map<String, String> bauarten = untertypRepository.findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.toMap(u -> u.getModulId() + "/" + u.getSchluessel(), WartungModulUntertyp::getLabel, (a, b) -> a));
        return tuer.getModule().stream()
                .sorted(Comparator.comparing(WartungModulAuswahl::getModulId))
                .map(m -> new WartungAnlageDto.Modul(m.getModulId(),
                        module.containsKey(m.getModulId()) ? module.get(m.getModulId()).getName() : "Modul " + m.getModulId(),
                        m.getUntertypSchluessel() == null ? null : bauarten.getOrDefault(m.getModulId() + "/" + m.getUntertypSchluessel(), m.getUntertypSchluessel()),
                        m.getUntertypSchluessel()))
                .toList();
    }

    private static String technikerName(Mitarbeiter m) {
        return m == null ? null : ((m.getVorname() == null ? "" : m.getVorname().trim()) + " " + (m.getNachname() == null ? "" : m.getNachname().trim())).trim();
    }
}
