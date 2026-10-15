package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungGlobalesDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kopiert den Vorlagenkatalog in ein neues Protokoll (Snapshot). Spätere Änderungen am Katalog wirken
 * nicht auf bestehende Protokolle.
 *
 * <p>Prüfpunkte, die nur für eine bestimmte Bauart gelten ({@code nurBeiUntertyp}), werden nur
 * übernommen, wenn genau diese Bauart gewählt wurde. Die Reihenfolge im Protokoll ist: globale Felder,
 * dann Modul für Modul (Basis-Element zuerst), darin Abschnitt für Abschnitt.</p>
 */
@Component
@AllArgsConstructor
public class WartungInhaltKopierer {

    private final WartungModulAbschnittRepository katalogAbschnitte;
    private final WartungModulPruefpunktRepository katalogPruefpunkte;
    private final WartungModulDatenfeldRepository katalogDatenfelder;
    private final WartungGlobalesDatenfeldRepository globaleDatenfelder;
    private final WartungProtokollAbschnittRepository abschnittRepository;
    private final WartungProtokollPruefpunktRepository pruefpunktRepository;
    private final WartungProtokollDatenfeldRepository datenfeldRepository;

    /**
     * @param vorherige Ergebnisse aus dem letzten abgeschlossenen Protokoll derselben Tür, Schlüssel
     *                  siehe {@link #schluessel(String, String)}; darf leer sein
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void kopiere(Long protokollId, List<GewaehltesModul> gewaehlt, Map<String, WartungErgebnis> vorherige) {
        List<Long> modulIds = gewaehlt.stream().map(g -> g.modul().getId()).toList();

        Map<Long, List<WartungModulAbschnitt>> abschnitte = katalogAbschnitte
                .findByModulIdInOrderBySortierungAscIdAsc(modulIds).stream()
                .collect(Collectors.groupingBy(WartungModulAbschnitt::getModulId));
        Map<Long, List<WartungModulPruefpunkt>> pruefpunkte = katalogPruefpunkte
                .findByModulIdInOrderBySortierungAscIdAsc(modulIds).stream()
                .collect(Collectors.groupingBy(WartungModulPruefpunkt::getModulId));
        Map<Long, List<WartungModulDatenfeld>> datenfelder = katalogDatenfelder
                .findByModulIdInOrderBySortierungAscIdAsc(modulIds).stream()
                .collect(Collectors.groupingBy(WartungModulDatenfeld::getModulId));

        int abschnittZaehler = 0;
        int pruefpunktZaehler = 0;
        int datenfeldZaehler = 0;
        List<WartungProtokollPruefpunkt> neuePruefpunkte = new ArrayList<>();
        List<WartungProtokollDatenfeld> neueDatenfelder = new ArrayList<>();

        for (WartungGlobalesDatenfeld global : globaleDatenfelder.findAllByOrderBySortierungAscIdAsc()) {
            WartungProtokollDatenfeld feld = new WartungProtokollDatenfeld();
            feld.setProtokollId(protokollId);
            feld.setLabel(global.getLabel());
            feld.setFeldtyp(global.getFeldtyp());
            feld.setOptionen(global.getOptionen());
            feld.setSortierung(datenfeldZaehler++);
            neueDatenfelder.add(feld);
        }

        for (GewaehltesModul gm : gewaehlt) {
            Long modulId = gm.modul().getId();
            String modulKey = gm.modul().getModulKey();
            String bauart = gm.untertyp() == null ? null : gm.untertyp().getSchluessel();

            List<WartungModulPruefpunkt> passende = pruefpunkte.getOrDefault(modulId, List.of()).stream()
                    .filter(p -> p.getNurBeiUntertyp() == null || Objects.equals(p.getNurBeiUntertyp(), bauart))
                    .toList();
            List<WartungModulDatenfeld> felder = datenfelder.getOrDefault(modulId, List.of());

            // Ein Abschnitt entsteht nur, wenn mindestens ein Eintrag darunter übernommen wird. Sonst
            // stünde bei "1-flügelig" eine leere Überschrift "Prüfpunkte 2-flügeliger Abschluss" im Protokoll.
            Set<Long> benutzteAbschnitte = new HashSet<>();
            passende.stream().map(WartungModulPruefpunkt::getAbschnittId).filter(Objects::nonNull).forEach(benutzteAbschnitte::add);
            felder.stream().map(WartungModulDatenfeld::getAbschnittId).filter(Objects::nonNull).forEach(benutzteAbschnitte::add);

            Map<Long, Long> abschnittIdNeu = new HashMap<>();
            List<WartungModulAbschnitt> pruefAbschnitte = new ArrayList<>();
            List<WartungModulAbschnitt> datenAbschnitte = new ArrayList<>();
            for (WartungModulAbschnitt a : abschnitte.getOrDefault(modulId, List.of())) {
                if (!benutzteAbschnitte.contains(a.getId())) {
                    continue;
                }
                WartungProtokollAbschnitt kopie = new WartungProtokollAbschnitt();
                kopie.setProtokollId(protokollId);
                kopie.setBereich(a.getBereich());
                kopie.setLabel(a.getLabel());
                kopie.setSortierung(abschnittZaehler++);
                abschnittIdNeu.put(a.getId(), abschnittRepository.save(kopie).getId());
                (a.getBereich() == WartungBereich.PRUEFPUNKTE ? pruefAbschnitte : datenAbschnitte).add(a);
            }

            for (WartungModulPruefpunkt p : inAbschnittsReihenfolge(passende, pruefAbschnitte,
                    WartungModulPruefpunkt::getAbschnittId, WartungModulPruefpunkt::getSortierung)) {
                WartungProtokollPruefpunkt kopie = new WartungProtokollPruefpunkt();
                kopie.setProtokollId(protokollId);
                kopie.setAbschnittId(p.getAbschnittId() == null ? null : abschnittIdNeu.get(p.getAbschnittId()));
                kopie.setModulKey(modulKey);
                kopie.setVorlageId(p.getId());
                kopie.setLabel(p.getLabel());
                kopie.setVorherigesErgebnis(vorherige.get(schluessel(modulKey, p.getLabel())));
                kopie.setSortierung(pruefpunktZaehler++);
                neuePruefpunkte.add(kopie);
            }

            for (WartungModulDatenfeld d : inAbschnittsReihenfolge(felder, datenAbschnitte,
                    WartungModulDatenfeld::getAbschnittId, WartungModulDatenfeld::getSortierung)) {
                WartungProtokollDatenfeld kopie = new WartungProtokollDatenfeld();
                kopie.setProtokollId(protokollId);
                kopie.setAbschnittId(d.getAbschnittId() == null ? null : abschnittIdNeu.get(d.getAbschnittId()));
                kopie.setModulKey(modulKey);
                kopie.setVorlageId(d.getId());
                kopie.setLabel(d.getLabel());
                kopie.setFeldtyp(d.getFeldtyp());
                kopie.setOptionen(d.getOptionen());
                kopie.setSortierung(datenfeldZaehler++);
                neueDatenfelder.add(kopie);
            }
        }

        pruefpunktRepository.saveAll(neuePruefpunkte);
        datenfeldRepository.saveAll(neueDatenfelder);
    }

    /**
     * Legt den Inhalt eines neuen Protokolls als <b>Kopie einer früheren Prüfung</b> an (Folgeprüfung).
     * Übernommen wird die Struktur genau so, wie sie in der Vorlage stand, auch dort ergänzte oder entfernte
     * Prüfpunkte. Die Vorlage selbst wird nur gelesen und nie verändert.
     *
     * @param werteUebernehmen ob Ergebnisse, Bemerkungen und Datenfeld-Werte mitkommen. Das Ergebnis der Vorlage steht
     *                         in jedem Fall als "Letztes Mal" am Prüfpunkt.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void kopiereAusProtokoll(Long vorlageId, Long neuesProtokollId, boolean werteUebernehmen) {
        Map<Long, Long> abschnittIdNeu = new HashMap<>();
        for (WartungProtokollAbschnitt a : abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(vorlageId)) {
            WartungProtokollAbschnitt kopie = new WartungProtokollAbschnitt();
            kopie.setProtokollId(neuesProtokollId);
            kopie.setBereich(a.getBereich());
            kopie.setLabel(a.getLabel());
            kopie.setSortierung(a.getSortierung());
            abschnittIdNeu.put(a.getId(), abschnittRepository.save(kopie).getId());
        }

        List<WartungProtokollPruefpunkt> punkte = new ArrayList<>();
        for (WartungProtokollPruefpunkt p : pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(vorlageId)) {
            WartungProtokollPruefpunkt kopie = new WartungProtokollPruefpunkt();
            kopie.setProtokollId(neuesProtokollId);
            kopie.setAbschnittId(p.getAbschnittId() == null ? null : abschnittIdNeu.get(p.getAbschnittId()));
            kopie.setModulKey(p.getModulKey());
            kopie.setVorlageId(p.getVorlageId());
            kopie.setLabel(p.getLabel());
            kopie.setVorherigesErgebnis(p.getErgebnis());
            if (werteUebernehmen) {
                kopie.setErgebnis(p.getErgebnis());
                kopie.setBemerkung(p.getBemerkung());
            }
            kopie.setSortierung(p.getSortierung());
            punkte.add(kopie);
        }

        List<WartungProtokollDatenfeld> felder = new ArrayList<>();
        for (WartungProtokollDatenfeld d : datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(vorlageId)) {
            WartungProtokollDatenfeld kopie = new WartungProtokollDatenfeld();
            kopie.setProtokollId(neuesProtokollId);
            kopie.setAbschnittId(d.getAbschnittId() == null ? null : abschnittIdNeu.get(d.getAbschnittId()));
            kopie.setModulKey(d.getModulKey());
            kopie.setVorlageId(d.getVorlageId());
            kopie.setLabel(d.getLabel());
            kopie.setFeldtyp(d.getFeldtyp());
            kopie.setOptionen(d.getOptionen());
            if (werteUebernehmen) {
                kopie.setWert(d.getWert());
            }
            kopie.setSortierung(d.getSortierung());
            felder.add(kopie);
        }
        pruefpunktRepository.saveAll(punkte);
        datenfeldRepository.saveAll(felder);
    }

    /** Schlüssel, unter dem ein Prüfpunkt im Vorgänger-Protokoll gesucht wird: Modul plus Bezeichnung. */
    public static String schluessel(String modulKey, String label) {
        return (modulKey == null ? "" : modulKey) + "\u0000" + label;
    }

    /**
     * Sortiert Einträge erst nach der Reihenfolge ihrer Abschnitte und darin nach ihrer eigenen
     * Sortierung. Einträge ohne Abschnitt kommen zuletzt. Nur nach {@code sortierung} zu sortieren
     * würde Abschnitte durcheinanderwürfeln, weil jeder Abschnitt wieder bei 1 anfängt.
     */
    private static <T> List<T> inAbschnittsReihenfolge(
            List<T> eintraege,
            List<WartungModulAbschnitt> abschnitteInReihenfolge,
            java.util.function.Function<T, Long> abschnittVon,
            java.util.function.ToIntFunction<T> sortierungVon) {
        Comparator<T> nachSortierung = Comparator.comparingInt(sortierungVon);
        List<T> ergebnis = new ArrayList<>();
        for (WartungModulAbschnitt abschnitt : abschnitteInReihenfolge) {
            eintraege.stream()
                    .filter(e -> Objects.equals(abschnittVon.apply(e), abschnitt.getId()))
                    .sorted(nachSortierung)
                    .forEach(ergebnis::add);
        }
        Set<Long> bekannt = abschnitteInReihenfolge.stream().map(WartungModulAbschnitt::getId)
                .collect(Collectors.toSet());
        eintraege.stream()
                .filter(e -> abschnittVon.apply(e) == null || !bekannt.contains(abschnittVon.apply(e)))
                .sorted(nachSortierung)
                .forEach(ergebnis::add);
        return ergebnis;
    }
}
