package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungGlobalesDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.dto.wartung.WartungGlobalesDatenfeldDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulUebersichtDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Modul-Editor: Pflege des Vorlagenkatalogs (Module, Bauarten, Abschnitte, Prüfpunkte, Datenfelder, globale Felder).
 * <p>
 * Der Katalog wird beim Anlegen eines Protokolls <b>kopiert</b>. Änderungen hier wirken deshalb nur auf neue
 * Protokolle, nie auf bestehende. Geschützt werden dagegen die Verweise von Türen: Eine Bauart oder ein Modul,
 * das eine Tür noch verwendet, lässt sich nicht entfernen, und die Art eines benutzten Moduls nicht ändern.
 * Gespeichert wird ein Modul als Ganzes. Vorhandene Einträge werden über ihre {@code id} fortgeführt (die Nummern
 * bleiben stabil), neue angelegt, fehlende gelöscht.
 */
@Service
@AllArgsConstructor
public class WartungModulAdminService {

    static final int MAX_PRUEFPUNKTE_JE_MODUL = 400;
    static final int MAX_DATENFELDER_JE_MODUL = 150;
    static final int MAX_GLOBALE_FELDER = 50;

    private final WartungModulRepository modulRepository;
    private final WartungModulUntertypRepository untertypRepository;
    private final WartungModulAbschnittRepository abschnittRepository;
    private final WartungModulPruefpunktRepository pruefpunktRepository;
    private final WartungModulDatenfeldRepository datenfeldRepository;
    private final WartungGlobalesDatenfeldRepository globalesFeldRepository;
    private final WartungTuerRepository tuerRepository;
    private final WartungProtokollRepository protokollRepository;

    // ── Lesen ───────────────────────────────────────────────────────────────

    /** Alle Module, auch deaktivierte, mit Zählern. */
    @Transactional(readOnly = true)
    public List<WartungModulUebersichtDto> alleModule() {
        List<WartungModul> module = modulRepository.findAllByOrderBySortierungAscIdAsc();
        if (module.isEmpty()) {
            return List.of();
        }
        List<Long> ids = module.stream().map(WartungModul::getId).toList();
        Map<Long, Long> untertypen = untertypRepository.findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulUntertyp::getModulId, Collectors.counting()));
        Map<Long, Long> pruefpunkte = pruefpunktRepository.findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulPruefpunkt::getModulId, Collectors.counting()));
        Map<Long, Long> datenfelder = datenfeldRepository.findByModulIdInOrderBySortierungAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(WartungModulDatenfeld::getModulId, Collectors.counting()));
        Map<Long, Long> tueren = zaehlen(tuerRepository.zaehleTuerenJeModul());
        Map<Long, Long> protokolle = zaehlen(protokollRepository.zaehleProtokolleJeModul());

        return module.stream().map(m -> new WartungModulUebersichtDto(
                m.getId(), m.getModulKey(), m.getName(), m.getBeschreibung(), m.getIcon(), m.getKategorie(),
                m.isSystemModul(), m.isAktiv(), m.getStandardIntervallMonate(),
                untertypen.getOrDefault(m.getId(), 0L).intValue(),
                pruefpunkte.getOrDefault(m.getId(), 0L).intValue(),
                datenfelder.getOrDefault(m.getId(), 0L).intValue(),
                tueren.getOrDefault(m.getId(), 0L).intValue(),
                protokolle.getOrDefault(m.getId(), 0L).intValue())).toList();
    }

    @Transactional(readOnly = true)
    public WartungModulBearbeitenDto laden(Long id) {
        return alsDto(ladeModul(id));
    }

    @Transactional(readOnly = true)
    public List<WartungGlobalesDatenfeldDto> globaleFelder() {
        return globalesFeldRepository.findAllByOrderBySortierungAscIdAsc().stream().map(WartungModulAdminService::alsDto).toList();
    }

    // ── Modul anlegen und speichern ─────────────────────────────────────────

    @Transactional
    public WartungModulBearbeitenDto anlegen(WartungModulBearbeitenDto dto) {
        WartungModul modul = new WartungModul();
        modul.setModulKey(neuerModulKey(dto.name()));
        modul.setSystemModul(false);
        modul.setSortierung(modulRepository.findAllByOrderBySortierungAscIdAsc().stream()
                .mapToInt(WartungModul::getSortierung).max().orElse(0) + 1);
        uebernehmeKopf(modul, dto);
        modul = modulRepository.saveAndFlush(modul);
        schreibeInhalt(modul, dto, List.of(), List.of(), List.of(), List.of());
        return alsDto(modul);
    }

    @Transactional
    public WartungModulBearbeitenDto speichern(Long id, WartungModulBearbeitenDto dto) {
        WartungModul modul = ladeModul(id);
        if (!Objects.equals(modul.getVersion(), dto.version())) {
            throw konflikt("Jemand anders hat dieses Modul gerade geändert. Bitte neu laden und die Änderung noch einmal machen.");
        }
        List<WartungModulUntertyp> untertypen = untertypRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        List<WartungModulAbschnitt> abschnitte = abschnittRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        List<WartungModulPruefpunkt> pruefpunkte = pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        List<WartungModulDatenfeld> datenfelder = datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(id);

        pruefeKategorieWechsel(modul, dto.kategorie());
        uebernehmeKopf(modul, dto);
        schreibeInhalt(modul, dto, untertypen, abschnitte, pruefpunkte, datenfelder);
        return alsDto(modulRepository.saveAndFlush(modul));
    }

    @Transactional
    public WartungModulBearbeitenDto aktivSetzen(Long id, boolean aktiv) {
        WartungModul modul = ladeModul(id);
        modul.setAktiv(aktiv);
        return alsDto(modulRepository.saveAndFlush(modul));
    }

    /** Löscht ein Modul, das nirgends benutzt wird. Benutzte und mitgelieferte Module lassen sich nur deaktivieren. */
    @Transactional
    public void loeschen(Long id) {
        WartungModul modul = ladeModul(id);
        if (modul.isSystemModul()) {
            throw konflikt("Dieses mitgelieferte Modul lässt sich nicht löschen. Sie können es deaktivieren.");
        }
        long tueren = zaehlen(tuerRepository.zaehleTuerenJeModul()).getOrDefault(id, 0L);
        long protokolle = zaehlen(protokollRepository.zaehleProtokolleJeModul()).getOrDefault(id, 0L);
        if (tueren > 0 || protokolle > 0) {
            throw konflikt("Dieses Modul wird noch benutzt (" + tueren + " Türen, " + protokolle
                    + " Protokolle). Bitte deaktivieren statt löschen, dann bleibt der Verlauf erhalten.");
        }
        // Andere Module, die an dieses andocken, verlieren den Verweis
        for (WartungModul anderes : modulRepository.findAll()) {
            if (anderes.getAndockbarAnBasisModulIds().remove(id)) {
                modulRepository.save(anderes);
            }
        }
        pruefpunktRepository.deleteAll(pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(id));
        datenfeldRepository.deleteAll(datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(id));
        abschnittRepository.deleteAll(abschnittRepository.findByModulIdOrderBySortierungAscIdAsc(id));
        untertypRepository.deleteAll(untertypRepository.findByModulIdOrderBySortierungAscIdAsc(id));
        modulRepository.delete(modul);
    }

    /** Setzt die Reihenfolge der Module; verlangt genau die vorhandenen Module, keines mehr oder weniger. */
    @Transactional
    public List<WartungModulUebersichtDto> reihenfolgeSetzen(List<Long> ids) {
        List<WartungModul> alle = modulRepository.findAllByOrderBySortierungAscIdAsc();
        Set<Long> vorhanden = alle.stream().map(WartungModul::getId).collect(Collectors.toSet());
        if (ids.size() != new HashSet<>(ids).size() || !vorhanden.equals(new HashSet<>(ids))) {
            throw falsch("Die Reihenfolge passt nicht zu den vorhandenen Modulen. Bitte neu laden.");
        }
        Map<Long, WartungModul> nachId = alle.stream().collect(Collectors.toMap(WartungModul::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            nachId.get(ids.get(i)).setSortierung(i + 1);
        }
        modulRepository.saveAll(alle);
        return alleModule();
    }

    // ── Globale Datenfelder ─────────────────────────────────────────────────

    @Transactional
    public List<WartungGlobalesDatenfeldDto> globaleFelderSpeichern(List<WartungGlobalesDatenfeldDto> felder) {
        if (felder.size() > MAX_GLOBALE_FELDER) {
            throw falsch("Es sind höchstens " + MAX_GLOBALE_FELDER + " globale Datenfelder möglich.");
        }
        List<WartungGlobalesDatenfeld> vorhanden = globalesFeldRepository.findAllByOrderBySortierungAscIdAsc();
        Map<Long, WartungGlobalesDatenfeld> nachId = vorhanden.stream()
                .collect(Collectors.toMap(WartungGlobalesDatenfeld::getId, Function.identity()));
        Set<Long> behalten = new HashSet<>();
        List<WartungGlobalesDatenfeld> ergebnis = new ArrayList<>();
        int sortierung = 1;
        for (WartungGlobalesDatenfeldDto dto : felder) {
            if (dto == null || dto.label() == null || dto.label().isBlank() || dto.feldtyp() == null) {
                throw falsch("Bitte jedes Datenfeld benennen und die Art wählen.");
            }
            if (dto.label().trim().length() > 200) {
                throw falsch("Die Bezeichnung eines Datenfelds ist zu lang (höchstens 200 Zeichen).");
            }
            WartungGlobalesDatenfeld feld;
            if (dto.id() == null) {
                feld = new WartungGlobalesDatenfeld();
            } else {
                feld = nachId.get(dto.id());
                if (feld == null) {
                    throw falsch("Ein Datenfeld wurde inzwischen gelöscht. Bitte neu laden.");
                }
                behalten.add(dto.id());
            }
            feld.setLabel(dto.label().trim());
            feld.setFeldtyp(dto.feldtyp());
            feld.setOptionen(optionenText(dto.feldtyp(), dto.optionen(), dto.label()));
            feld.setSortierung(sortierung++);
            ergebnis.add(feld);
        }
        pruefeDoppelteBezeichnungen(felder.stream().map(WartungGlobalesDatenfeldDto::label).toList(), "Datenfeld");
        pruefeEindeutigeNummern(felder.stream().map(WartungGlobalesDatenfeldDto::id).toList(), "Datenfeld");
        globalesFeldRepository.deleteAll(vorhanden.stream().filter(f -> !behalten.contains(f.getId())).toList());
        globalesFeldRepository.saveAll(ergebnis);
        return globaleFelder();
    }

    // ── Inhalt schreiben ────────────────────────────────────────────────────

    private void uebernehmeKopf(WartungModul modul, WartungModulBearbeitenDto dto) {
        modul.setName(dto.name().trim());
        modul.setBeschreibung(leerZuNull(dto.beschreibung()));
        modul.setIcon(leerZuNull(dto.icon()));
        modul.setKategorie(dto.kategorie());
        modul.setAktiv(Boolean.TRUE.equals(dto.aktiv()));
        modul.setStandardIntervallMonate(dto.standardIntervallMonate());
        modul.setAndockbarAnBasisModulIds(pruefeAndockbar(modul, dto));
    }

    /** Zusatzmodule dürfen an Basis-Elemente andocken; Basis-Elemente haben keine Liste. */
    private Set<Long> pruefeAndockbar(WartungModul modul, WartungModulBearbeitenDto dto) {
        List<Long> gewaehlt = dto.andockbarAn();
        if (gewaehlt.isEmpty()) {
            return new HashSet<>();
        }
        if (dto.kategorie() != WartungModulKategorie.ZUSATZ) {
            throw falsch("Nur ein Zusatzmodul kann an ein Basis-Element andocken.");
        }
        Set<Long> ergebnis = new LinkedHashSet<>();
        for (Long basisId : gewaehlt) {
            if (basisId == null || basisId.equals(modul.getId())) {
                throw falsch("Ein Modul kann nicht an sich selbst andocken.");
            }
            WartungModul basis = modulRepository.findById(basisId)
                    .orElseThrow(() -> falsch("Ein gewähltes Basis-Element gibt es nicht mehr. Bitte neu laden."));
            if (basis.getKategorie() != WartungModulKategorie.BASIS) {
                throw falsch("Andocken ist nur an Basis-Elemente möglich, nicht an \"" + basis.getName() + "\".");
            }
            ergebnis.add(basisId);
        }
        return ergebnis;
    }

    /** Ein benutzes Modul behält seine Art: Aus einem Basis-Element eine Zusatz-Art zu machen bräche vorhandene Türen. */
    private void pruefeKategorieWechsel(WartungModul modul, WartungModulKategorie neu) {
        if (modul.getKategorie() == neu) {
            return;
        }
        long tueren = zaehlen(tuerRepository.zaehleTuerenJeModul()).getOrDefault(modul.getId(), 0L);
        if (tueren > 0) {
            throw konflikt("Die Art dieses Moduls lässt sich nicht ändern, weil " + tueren + " Türen es verwenden.");
        }
        boolean angedockt = modulRepository.findAll().stream()
                .anyMatch(m -> m.getAndockbarAnBasisModulIds().contains(modul.getId()));
        if (neu == WartungModulKategorie.ZUSATZ && angedockt) {
            throw konflikt("Andere Module docken an dieses Basis-Element an. Bitte zuerst dort entfernen.");
        }
    }

    private void schreibeInhalt(WartungModul modul, WartungModulBearbeitenDto dto,
                                List<WartungModulUntertyp> untertypen, List<WartungModulAbschnitt> abschnitte,
                                List<WartungModulPruefpunkt> pruefpunkte, List<WartungModulDatenfeld> datenfelder) {
        Long modulId = modul.getId();
        List<WartungModulBearbeitenDto.Untertyp> neueUntertypen = dto.untertypen();
        List<WartungModulBearbeitenDto.Abschnitt> neueAbschnitte = dto.abschnitte();
        pruefeEindeutigeNummern(neueUntertypen.stream().map(WartungModulBearbeitenDto.Untertyp::id).toList(), "Bauart");
        pruefeEindeutigeNummern(neueAbschnitte.stream().map(WartungModulBearbeitenDto.Abschnitt::id).toList(), "Abschnitt");
        pruefeEindeutigeNummern(neueAbschnitte.stream().flatMap(a -> a.pruefpunkte() == null ? java.util.stream.Stream.<WartungModulBearbeitenDto.Pruefpunkt>empty() : a.pruefpunkte().stream()).map(WartungModulBearbeitenDto.Pruefpunkt::id).toList(), "Prüfpunkt");
        pruefeEindeutigeNummern(neueAbschnitte.stream().flatMap(a -> a.datenfelder() == null ? java.util.stream.Stream.<WartungModulBearbeitenDto.Datenfeld>empty() : a.datenfelder().stream()).map(WartungModulBearbeitenDto.Datenfeld::id).toList(), "Datenfeld");

        // ── Prüfungen vor dem Schreiben, damit bei einem Fehler nichts halb gespeichert wird (die
        // Transaktion würde zurückgerollt, aber klare Meldungen kommen so zuerst)
        int punkte = 0;
        int felder = 0;
        for (WartungModulBearbeitenDto.Abschnitt a : neueAbschnitte) {
            List<WartungModulBearbeitenDto.Pruefpunkt> pp = a.pruefpunkte() == null ? List.of() : a.pruefpunkte();
            List<WartungModulBearbeitenDto.Datenfeld> df = a.datenfelder() == null ? List.of() : a.datenfelder();
            if (a.bereich() == WartungBereich.PRUEFPUNKTE && !df.isEmpty()) {
                throw falsch("Im Abschnitt \"" + a.label() + "\" sind Prüfpunkte vorgesehen, aber keine Datenfelder.");
            }
            if (a.bereich() == WartungBereich.DATENFELDER && !pp.isEmpty()) {
                throw falsch("Im Abschnitt \"" + a.label() + "\" sind Datenfelder vorgesehen, aber keine Prüfpunkte.");
            }
            punkte += pp.size();
            felder += df.size();
        }
        if (punkte > MAX_PRUEFPUNKTE_JE_MODUL) {
            throw falsch("Ein Modul kann höchstens " + MAX_PRUEFPUNKTE_JE_MODUL + " Prüfpunkte haben.");
        }
        if (felder > MAX_DATENFELDER_JE_MODUL) {
            throw falsch("Ein Modul kann höchstens " + MAX_DATENFELDER_JE_MODUL + " Datenfelder haben.");
        }
        pruefeDoppelteBezeichnungen(neueUntertypen.stream().map(WartungModulBearbeitenDto.Untertyp::label).toList(), "Bauart");

        // ── Bauarten
        Map<Long, WartungModulUntertyp> untertypNachId = untertypen.stream()
                .collect(Collectors.toMap(WartungModulUntertyp::getId, Function.identity()));
        Set<String> belegteSchluessel = untertypen.stream().map(WartungModulUntertyp::getSchluessel).collect(Collectors.toCollection(HashSet::new));
        List<WartungModulUntertyp> zuSpeichern = new ArrayList<>();
        Set<Long> behalteneUntertypen = new HashSet<>();
        int sortierung = 1;
        for (WartungModulBearbeitenDto.Untertyp u : neueUntertypen) {
            WartungModulUntertyp ziel;
            if (u.id() == null) {
                ziel = new WartungModulUntertyp();
                ziel.setModulId(modulId);
                String schluessel = leerZuNull(u.schluessel()) != null ? u.schluessel() : schluesselAus(u.label(), belegteSchluessel);
                if (belegteSchluessel.contains(schluessel)) {
                    throw falsch("Der Schlüssel \"" + schluessel + "\" wird schon verwendet.");
                }
                belegteSchluessel.add(schluessel);
                ziel.setSchluessel(schluessel);
            } else {
                ziel = untertypNachId.get(u.id());
                if (ziel == null) {
                    throw falsch("Eine Bauart wurde inzwischen gelöscht. Bitte neu laden.");
                }
                if (leerZuNull(u.schluessel()) != null && !u.schluessel().equals(ziel.getSchluessel())) {
                    throw falsch("Der Schlüssel einer vorhandenen Bauart lässt sich nicht ändern.");
                }
                behalteneUntertypen.add(u.id());
            }
            ziel.setLabel(u.label().trim());
            ziel.setSortierung(sortierung++);
            zuSpeichern.add(ziel);
        }
        Set<String> endgueltigeSchluessel = zuSpeichern.stream().map(WartungModulUntertyp::getSchluessel).collect(Collectors.toSet());

        for (WartungModulUntertyp entfernt : untertypen) {
            if (!behalteneUntertypen.contains(entfernt.getId())) {
                long benutzt = tuerRepository.zaehleMitBauart(modulId, entfernt.getSchluessel());
                if (benutzt > 0) {
                    throw konflikt("Die Bauart \"" + entfernt.getLabel() + "\" wird noch von " + benutzt
                            + (benutzt == 1 ? " Tür" : " Türen") + " verwendet und lässt sich nicht entfernen.");
                }
            }
        }

        // ── Abschnitte, Prüfpunkte und Datenfelder
        Map<Long, WartungModulAbschnitt> abschnittNachId = abschnitte.stream()
                .collect(Collectors.toMap(WartungModulAbschnitt::getId, Function.identity()));
        Map<Long, WartungModulPruefpunkt> punktNachId = pruefpunkte.stream()
                .collect(Collectors.toMap(WartungModulPruefpunkt::getId, Function.identity()));
        Map<Long, WartungModulDatenfeld> feldNachId = datenfelder.stream()
                .collect(Collectors.toMap(WartungModulDatenfeld::getId, Function.identity()));

        // Bauarten zuerst: neue Bauarten müssen existieren, bevor Prüfpunkte auf sie verweisen
        Set<Long> entfernteUntertypIds = untertypen.stream().map(WartungModulUntertyp::getId)
                .filter(i -> !behalteneUntertypen.contains(i)).collect(Collectors.toSet());
        untertypRepository.deleteAll(untertypen.stream().filter(u -> entfernteUntertypIds.contains(u.getId())).toList());
        untertypRepository.flush();
        untertypRepository.saveAll(zuSpeichern);

        Set<Long> behalteneAbschnitte = new HashSet<>();
        Set<Long> behaltenePunkte = new HashSet<>();
        Set<Long> behalteneFelder = new HashSet<>();
        List<WartungModulAbschnitt> abschnittNeu = new ArrayList<>();
        int abschnittSortierung = 1;
        int punktSortierung = 1;
        int feldSortierung = 1;
        List<WartungModulPruefpunkt> punkteSpeichern = new ArrayList<>();
        List<WartungModulDatenfeld> felderSpeichern = new ArrayList<>();
        List<PunktImAbschnitt> punktZuordnung = new ArrayList<>();
        List<FeldImAbschnitt> feldZuordnung = new ArrayList<>();

        for (WartungModulBearbeitenDto.Abschnitt a : neueAbschnitte) {
            WartungModulAbschnitt abschnitt;
            if (a.id() == null) {
                abschnitt = new WartungModulAbschnitt();
                abschnitt.setModulId(modulId);
            } else {
                abschnitt = abschnittNachId.get(a.id());
                if (abschnitt == null) {
                    throw falsch("Ein Abschnitt wurde inzwischen gelöscht. Bitte neu laden.");
                }
                behalteneAbschnitte.add(a.id());
            }
            abschnitt.setBereich(a.bereich());
            abschnitt.setLabel(a.label().trim());
            abschnitt.setSortierung(abschnittSortierung++);
            abschnittNeu.add(abschnitt);

            for (WartungModulBearbeitenDto.Pruefpunkt p : a.pruefpunkte() == null ? List.<WartungModulBearbeitenDto.Pruefpunkt>of() : a.pruefpunkte()) {
                WartungModulPruefpunkt punkt;
                if (p.id() == null) {
                    punkt = new WartungModulPruefpunkt();
                    punkt.setModulId(modulId);
                } else {
                    punkt = punktNachId.get(p.id());
                    if (punkt == null) {
                        throw falsch("Ein Prüfpunkt wurde inzwischen gelöscht. Bitte neu laden.");
                    }
                    behaltenePunkte.add(p.id());
                }
                String bauart = leerZuNull(p.nurBeiUntertyp());
                if (bauart != null && !endgueltigeSchluessel.contains(bauart)) {
                    throw falsch("Der Prüfpunkt \"" + p.label() + "\" gilt für eine Bauart, die es nicht mehr gibt.");
                }
                punkt.setLabel(p.label().trim());
                punkt.setNurBeiUntertyp(bauart);
                punkt.setSortierung(punktSortierung++);
                punkteSpeichern.add(punkt);
                punktZuordnung.add(new PunktImAbschnitt(punkt, abschnitt));
            }
            for (WartungModulBearbeitenDto.Datenfeld d : a.datenfelder() == null ? List.<WartungModulBearbeitenDto.Datenfeld>of() : a.datenfelder()) {
                WartungModulDatenfeld feld;
                if (d.id() == null) {
                    feld = new WartungModulDatenfeld();
                    feld.setModulId(modulId);
                } else {
                    feld = feldNachId.get(d.id());
                    if (feld == null) {
                        throw falsch("Ein Datenfeld wurde inzwischen gelöscht. Bitte neu laden.");
                    }
                    behalteneFelder.add(d.id());
                }
                feld.setLabel(d.label().trim());
                feld.setFeldtyp(d.feldtyp());
                feld.setOptionen(optionenText(d.feldtyp(), d.optionen(), d.label()));
                feld.setSortierung(feldSortierung++);
                felderSpeichern.add(feld);
                feldZuordnung.add(new FeldImAbschnitt(feld, abschnitt));
            }
        }

        // Zuerst löschen, dann anlegen. Prüfpunkte und Datenfelder vor ihren Abschnitten, wegen der Verweise.
        pruefpunktRepository.deleteAll(pruefpunkte.stream().filter(p -> !behaltenePunkte.contains(p.getId())).toList());
        datenfeldRepository.deleteAll(datenfelder.stream().filter(f -> !behalteneFelder.contains(f.getId())).toList());
        abschnittRepository.deleteAll(abschnitte.stream().filter(a -> !behalteneAbschnitte.contains(a.getId())).toList());
        abschnittRepository.flush();
        abschnittRepository.saveAll(abschnittNeu);
        abschnittRepository.flush(); // vergibt die Nummern der neuen Abschnitte
        punktZuordnung.forEach(z -> z.punkt().setAbschnittId(z.abschnitt().getId()));
        feldZuordnung.forEach(z -> z.feld().setAbschnittId(z.abschnitt().getId()));
        pruefpunktRepository.saveAll(punkteSpeichern);
        datenfeldRepository.saveAll(felderSpeichern);
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    /** Merkt sich, zu welchem Abschnitt ein Eintrag gehört, bis die Abschnitte ihre Nummern haben. */
    private record PunktImAbschnitt(WartungModulPruefpunkt punkt, WartungModulAbschnitt abschnitt) { }

    private record FeldImAbschnitt(WartungModulDatenfeld feld, WartungModulAbschnitt abschnitt) { }

    private WartungModulBearbeitenDto alsDto(WartungModul modul) {
        Long id = modul.getId();
        List<WartungModulUntertyp> untertypen = untertypRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        List<WartungModulAbschnitt> abschnitte = abschnittRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        Set<Long> pruefAbschnittIds = abschnitte.stream().filter(a -> a.getBereich() == WartungBereich.PRUEFPUNKTE)
                .map(WartungModulAbschnitt::getId).collect(Collectors.toSet());
        Set<Long> datenAbschnittIds = abschnitte.stream().filter(a -> a.getBereich() == WartungBereich.DATENFELDER)
                .map(WartungModulAbschnitt::getId).collect(Collectors.toSet());
        List<WartungModulPruefpunkt> allePunkte = pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(id);
        List<WartungModulDatenfeld> alleFelder = datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(id);

        // Einträge ohne passenden Abschnitt gibt es wirklich: Die mitgelieferten Datenfelder haben keinen, und ein
        // gelöschter Abschnitt hinterlässt Prüfpunkte ohne Abschnitt. Sie werden in einem eigenen Abschnitt gezeigt,
        // denn der Editor löscht beim Speichern alles, was er nicht zeigt. Mit dem ersten Speichern entsteht daraus
        // ein echter Abschnitt, die Einträge behalten ihre Nummer.
        Map<Long, List<WartungModulPruefpunkt>> punkte = alsGruppen(allePunkte, pruefAbschnittIds, WartungModulPruefpunkt::getAbschnittId);
        Map<Long, List<WartungModulDatenfeld>> felder = alsGruppen(alleFelder, datenAbschnittIds, WartungModulDatenfeld::getAbschnittId);
        List<WartungModulPruefpunkt> verwaisteePunkte = allePunkte.stream()
                .filter(p -> p.getAbschnittId() == null || !pruefAbschnittIds.contains(p.getAbschnittId())).toList();
        List<WartungModulDatenfeld> verwaisteFelder = alleFelder.stream()
                .filter(f -> f.getAbschnittId() == null || !datenAbschnittIds.contains(f.getAbschnittId())).toList();

        List<WartungModulBearbeitenDto.Abschnitt> abschnittDtos = new ArrayList<>(abschnitte.stream().map(a -> new WartungModulBearbeitenDto.Abschnitt(
                a.getId(), a.getBereich(), a.getLabel(),
                a.getBereich() == WartungBereich.PRUEFPUNKTE ? punkte.getOrDefault(a.getId(), List.of()).stream().map(WartungModulAdminService::alsDto).toList() : List.of(),
                a.getBereich() == WartungBereich.DATENFELDER ? felder.getOrDefault(a.getId(), List.of()).stream().map(WartungModulAdminService::alsDto).toList() : List.of())).toList());
        if (!verwaisteePunkte.isEmpty()) {
            abschnittDtos.add(new WartungModulBearbeitenDto.Abschnitt(null, WartungBereich.PRUEFPUNKTE, "Prüfpunkte",
                    verwaisteePunkte.stream().map(WartungModulAdminService::alsDto).toList(), List.of()));
        }
        if (!verwaisteFelder.isEmpty()) {
            abschnittDtos.add(new WartungModulBearbeitenDto.Abschnitt(null, WartungBereich.DATENFELDER, "Datenaufnahme",
                    List.of(), verwaisteFelder.stream().map(WartungModulAdminService::alsDto).toList()));
        }

        int tueren = zaehlen(tuerRepository.zaehleTuerenJeModul()).getOrDefault(id, 0L).intValue();
        int protokolle = zaehlen(protokollRepository.zaehleProtokolleJeModul()).getOrDefault(id, 0L).intValue();
        return new WartungModulBearbeitenDto(id, modul.getVersion(), modul.getModulKey(), modul.getName(), modul.getBeschreibung(),
                modul.getIcon(), modul.getKategorie(), modul.isSystemModul(), modul.isAktiv(), modul.getStandardIntervallMonate(),
                modul.getAndockbarAnBasisModulIds().stream().sorted().toList(),
                untertypen.stream().map(u -> new WartungModulBearbeitenDto.Untertyp(u.getId(), u.getSchluessel(), u.getLabel())).toList(),
                abschnittDtos, tueren, protokolle);
    }

    private static <T> Map<Long, List<T>> alsGruppen(List<T> eintraege, Set<Long> gueltigeAbschnitte, Function<T, Long> abschnittVon) {
        return eintraege.stream()
                .filter(e -> abschnittVon.apply(e) != null && gueltigeAbschnitte.contains(abschnittVon.apply(e)))
                .collect(Collectors.groupingBy(abschnittVon));
    }

    private static WartungModulBearbeitenDto.Pruefpunkt alsDto(WartungModulPruefpunkt p) {
        return new WartungModulBearbeitenDto.Pruefpunkt(p.getId(), p.getLabel(), p.getNurBeiUntertyp());
    }

    private static WartungModulBearbeitenDto.Datenfeld alsDto(WartungModulDatenfeld f) {
        return new WartungModulBearbeitenDto.Datenfeld(f.getId(), f.getLabel(), f.getFeldtyp(), WartungMapper.optionenListe(f.getOptionen()));
    }

    private static WartungGlobalesDatenfeldDto alsDto(WartungGlobalesDatenfeld feld) {
        return new WartungGlobalesDatenfeldDto(feld.getId(), feld.getLabel(), feld.getFeldtyp(), WartungMapper.optionenListe(feld.getOptionen()));
    }

    private WartungModul ladeModul(Long id) {
        return modulRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Modul wurde nicht gefunden."));
    }

    private static Map<Long, Long> zaehlen(List<Object[]> zeilen) {
        Map<Long, Long> ergebnis = new HashMap<>();
        for (Object[] zeile : zeilen) {
            ergebnis.put((Long) zeile[0], ((Number) zeile[1]).longValue());
        }
        return ergebnis;
    }

    /** Optionen nur bei {@code AUSWAHL}, dann mindestens eine, ohne Doppelte und ohne Zeilenumbruch (eine Option je Zeile gespeichert). */
    private static String optionenText(WartungFeldtyp typ, List<String> optionen, String feldname) {
        if (typ != WartungFeldtyp.AUSWAHL) {
            return null;
        }
        List<String> bereinigt = optionen == null ? List.of()
                : optionen.stream().filter(Objects::nonNull).map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (bereinigt.isEmpty()) {
            throw falsch("Beim Datenfeld \"" + feldname + "\" fehlen die Auswahlmöglichkeiten.");
        }
        if (bereinigt.stream().anyMatch(o -> o.contains("\n") || o.contains("\r"))) {
            throw falsch("Eine Auswahlmöglichkeit darf nur eine Zeile lang sein.");
        }
        if (new HashSet<>(bereinigt).size() != bereinigt.size()) {
            throw falsch("Beim Datenfeld \"" + feldname + "\" kommt eine Auswahlmöglichkeit doppelt vor.");
        }
        return String.join("\n", bereinigt);
    }

    /** Dieselbe Nummer zweimal in einer Liste hieße: ein Eintrag würde zweimal bearbeitet. Nur ein fehlerhafter Client schickt das. */
    private static void pruefeEindeutigeNummern(List<Long> nummern, String art) {
        Set<Long> gesehen = new HashSet<>();
        for (Long nummer : nummern) {
            if (nummer != null && !gesehen.add(nummer)) {
                throw falsch("Ein " + art + " kommt in der Anfrage doppelt vor. Bitte neu laden.");
            }
        }
    }

    private static void pruefeDoppelteBezeichnungen(List<String> bezeichnungen, String art) {
        Set<String> gesehen = new HashSet<>();
        for (String bezeichnung : bezeichnungen) {
            if (bezeichnung != null && !gesehen.add(bezeichnung.trim().toLowerCase(Locale.GERMAN))) {
                throw falsch("Die Bezeichnung \"" + bezeichnung.trim() + "\" gibt es bei " + art + " doppelt.");
            }
        }
    }

    private String neuerModulKey(String name) {
        String basis = "m_" + slug(name, 40);
        String kandidat = basis;
        for (int i = 2; modulRepository.existsByModulKey(kandidat); i++) {
            kandidat = basis + "_" + i;
        }
        return kandidat;
    }

    private static String schluesselAus(String label, Set<String> belegt) {
        String basis = slug(label, 26);
        String kandidat = basis;
        for (int i = 2; belegt.contains(kandidat); i++) {
            kandidat = basis + "_" + i;
        }
        return kandidat;
    }

    /** Kleinbuchstaben und Ziffern: "2-flügelig" wird zu "2fluegelig". Leere Ergebnisse heißen "x". */
    static String slug(String text, int maxLaenge) {
        String ersetzt = text.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        String ohneAkzente = Normalizer.normalize(ersetzt, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String bereinigt = ohneAkzente.replaceAll("[^a-z0-9]+", "");
        if (bereinigt.isEmpty()) {
            bereinigt = "x";
        }
        return bereinigt.length() > maxLaenge ? bereinigt.substring(0, maxLaenge) : bereinigt;
    }

    private static String leerZuNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static ResponseStatusException falsch(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }

    private static ResponseStatusException konflikt(String meldung) {
        return new ResponseStatusException(HttpStatus.CONFLICT, meldung);
    }
}
