package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.norm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungImportZuordnung;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.ProtokollZeile;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungImportZuordnungRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.example.kalkulationsprogramm.service.wartung.WartungInhaltKopierer;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.Ergebnis;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.Fluegel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * Übernahme von Wartungsprotokollen aus HandwerkPro, Schritt 2: die eigentliche <b>Übernahme</b>. Sie läuft in <b>einer Transaktion</b> (geht etwas schief,
 * bleibt nichts halb übernommen) und ist <b>wiederholbar</b>: Was schon übernommen wurde, steht in {@code wartung_import_zuordnung} und wird nicht noch einmal angelegt.
 * Protokolle, bei denen die Vorschau noch eine Frage offen hat, werden ausgelassen und gemeldet. Die alten Protokollnummern bleiben erhalten; das PDF entsteht wie bei
 * jedem Protokoll aus den Daten.
 */
@Service
@RequiredArgsConstructor
public class WartungImportAusfuehrer {

    static final String QUELLE = "HandwerkPro";

    /** Was die Übernahme getan hat. {@code uebersprungen} nennt jedes ausgelassene Protokoll mit Grund. */
    public record Ergebnisbericht(int protokolleNeu, int protokolleSchonDa, int kundenNeu, int objekteNeu, int tuerenNeu, int pruefpunkteEigene, int werteVerworfen,
            int katalogErgaenzt, List<String> uebersprungen, List<String> hinweise) { }

    private final WartungImportService importService;
    private final WartungImportZuordnungRepository zuordnungen;
    private final WartungModulRepository module;
    private final WartungModulUntertypRepository untertypen;
    private final WartungGlobalesDatenfeldRepository globaleFelder;
    private final WartungModulDatenfeldRepository modulFelder;
    private final KundeRepository kunden;
    private final KundennummerService kundennummern;
    private final MitarbeiterRepository mitarbeiter;
    private final WartungObjektRepository objekte;
    private final WartungTuerRepository tueren;
    private final WartungProtokollRepository protokolle;
    private final WartungProtokollPruefpunktRepository protokollPruefpunkte;
    private final WartungProtokollDatenfeldRepository protokollFelder;
    private final WartungInhaltKopierer inhaltKopierer;

    /** Zähler und Meldungen eines Laufs. */
    private static final class Lauf {
        int protokolleNeu;
        int schonDa;
        int kundenNeu;
        int objekteNeu;
        int tuerenNeu;
        int eigene;
        int verworfen;
        int katalog;
        final List<String> uebersprungen = new ArrayList<>();
        final Set<String> hinweise = new LinkedHashSet<>();
        final Map<String, Set<String>> neueOptionen = new LinkedHashMap<>();
        final Map<Long, Long> kundeErp = new HashMap<>();
        final Map<Long, Long> objektErp = new HashMap<>();
        final Map<Long, Long> tuerErp = new HashMap<>();
        final Map<Long, LocalDate> neuesterAbschluss = new HashMap<>();
    }

    /** @param neueWerteInKatalog ob unbekannte Werte (zum Beispiel neue Hersteller) in die Auswahllisten des Katalogs aufgenommen werden; im Protokoll stehen sie in jedem Fall */
    @Transactional
    public Ergebnisbericht ausfuehren(JsonNode export, boolean neueWerteInKatalog) {
        importService.pruefeExport(export);
        Vorschau vorschau = WartungImportAnalyse.analysiere(export, importService.katalogLaden());
        Lauf lauf = new Lauf();

        Map<Long, JsonNode> kundenJson = nachId(export.path("kunden"));
        Map<Long, JsonNode> objekteJson = nachId(export.path("objekte"));
        Map<Long, JsonNode> tuerenJson = nachId(export.path("tueren"));
        Map<Long, JsonNode> technikerJson = nachId(export.path("techniker"));
        Map<Long, JsonNode> wartungJson = nachId(export.path("wartungen"));
        Map<Long, List<JsonNode>> punkte = gruppiere(export.path("pruefpunkte"));
        Map<Long, List<JsonNode>> felder = gruppiere(export.path("komponenten"));
        Map<Long, Long> vorschlag = new HashMap<>();
        vorschau.kunden().forEach(k -> vorschlag.put(k.altId(), k.erpId()));

        ergaenzeUntertypen(vorschau, lauf);
        Map<String, WartungModul> modulNachKey = new HashMap<>();
        module.findAll().forEach(m -> modulNachKey.put(m.getModulKey(), m));
        Set<String> herstellerListe = importService.katalogLaden().optionenVon(WartungImportAbbildung.F_HERSTELLER);
        List<Mitarbeiter> alleMitarbeiter = mitarbeiter.findAktiveMenschen();

        for (ProtokollZeile zeile : vorschau.protokolle()) {
            JsonNode w = wartungJson.get(zeile.altId());
            if (zuordnungen.findByQuelleAndTypAndAltId(QUELLE, "PROTOKOLL", zeile.altId()).isPresent()) {
                lauf.schonDa++;
                continue;
            }
            if (!zeile.fragen().isEmpty()) {
                lauf.uebersprungen.add(zeile.altNummer() + ": " + String.join(" ", zeile.fragen()));
                continue;
            }
            if (protokolle.existsByNummer(zeile.altNummer())) {
                lauf.uebersprungen.add(zeile.altNummer() + ": Diese Protokollnummer gibt es im ERP schon (aus einer anderen Quelle).");
                continue;
            }
            // Die Auswahl wird hier nicht noch einmal wie bei einem neuen Protokoll geprüft (Andocken, aktive Module): Ein altes, abgeschlossenes Protokoll soll so
            // übernommen werden, wie es war. Eine Ausnahme aus dem Prüfer würde außerdem die ganze Übernahme zurückrollen.
            List<GewaehltesModul> gewaehlt = new ArrayList<>();
            for (String key : zeile.module()) {
                WartungModul m = modulNachKey.get(key);
                String ut = zeile.untertypen().get(key);
                String schluessel = ut == null ? null : ut.startsWith("NEU:") ? schluesselNeu(m, ut.substring(4)) : ut;
                WartungModulUntertyp untertyp = schluessel == null ? null : untertypen.findByModulIdOrderBySortierungAscIdAsc(m.getId()).stream()
                        .filter(u -> u.getSchluessel().equals(schluessel)).findFirst().orElse(null);
                gewaehlt.add(new GewaehltesModul(m, untertyp));
            }
            gewaehlt.sort(java.util.Comparator.comparing((GewaehltesModul g) -> g.modul().istBasis() ? 0 : 1).thenComparingInt(g -> g.modul().getSortierung()));

            Kunde kunde = kundeFuer(w.path("kunde_id").asLong(), kundenJson, vorschlag, lauf);
            WartungObjekt objekt = w.hasNonNull("objekt_id") ? objektFuer(w.path("objekt_id").asLong(), kunde, objekteJson, lauf) : null;
            WartungTuer tuer = w.hasNonNull("tuer_id") && objekt != null ? tuerFuer(w.path("tuer_id").asLong(), objekt, tuerenJson, lauf) : null;
            WartungProtokoll p = legeProtokollAn(w, zeile, kunde, objekt, tuer, gewaehlt, technikerJson, alleMitarbeiter, lauf);
            inhaltKopierer.kopiere(p.getId(), gewaehlt, Map.of());
            uebernehmeInhalt(p, w, punkte.getOrDefault(zeile.altId(), List.of()), felder.getOrDefault(zeile.altId(), List.of()), herstellerListe, lauf);
            merke("PROTOKOLL", zeile.altId(), p.getId());
            lauf.protokolleNeu++;
            if (tuer != null && p.getAbgeschlossenAm() != null) {
                lauf.neuesterAbschluss.merge(tuer.getId(), p.getDatum(), (a, b) -> a.isAfter(b) ? a : b);
                if (tuer.getModule().isEmpty()) {
                    gewaehlt.forEach(g -> tuer.getModule().add(new WartungModulAuswahl(g.modul().getId(), g.untertyp() == null ? null : g.untertyp().getSchluessel())));
                }
            }
        }
        setzeWartungsplan(lauf);
        if (neueWerteInKatalog) {
            lauf.katalog += nehmeInKatalogAuf(lauf.neueOptionen);
        } else if (!lauf.neueOptionen.isEmpty()) {
            lauf.hinweise.add("Neue Werte stehen nur in den Protokollen, nicht in den Auswahllisten des Katalogs.");
        }
        return new Ergebnisbericht(lauf.protokolleNeu, lauf.schonDa, lauf.kundenNeu, lauf.objekteNeu, lauf.tuerenNeu, lauf.eigene, lauf.verworfen, lauf.katalog,
                lauf.uebersprungen, new ArrayList<>(lauf.hinweise));
    }

    // ---------------------------------------------------------------- Katalog

    /** Neue Varianten (zum Beispiel „Standard ohne Handtaster“) kommen als Untertyp ins Modul, bevor Protokolle sie brauchen. */
    private void ergaenzeUntertypen(Vorschau vorschau, Lauf lauf) {
        Map<String, WartungModul> byKey = new HashMap<>();
        module.findAll().forEach(m -> byKey.put(m.getModulKey(), m));
        for (ProtokollZeile z : vorschau.protokolle()) {
            for (Map.Entry<String, String> e : z.untertypen().entrySet()) {
                if (e.getValue().startsWith("NEU:") && byKey.containsKey(e.getKey())) {
                    WartungModul modul = byKey.get(e.getKey());
                    int davor = untertypen.findByModulIdOrderBySortierungAscIdAsc(modul.getId()).size();
                    schluesselNeu(modul, e.getValue().substring(4));
                    lauf.katalog += untertypen.findByModulIdOrderBySortierungAscIdAsc(modul.getId()).size() - davor;
                }
            }
        }
    }

    /** Der Schlüssel der Variante mit dieser Bezeichnung; sie wird angelegt, wenn es sie noch nicht gibt. */
    private String schluesselNeu(WartungModul modul, String label) {
        List<WartungModulUntertyp> vorhanden = untertypen.findByModulIdOrderBySortierungAscIdAsc(modul.getId());
        for (WartungModulUntertyp u : vorhanden) {
            if (norm(u.getLabel()).equals(norm(label))) {
                return u.getSchluessel();
            }
        }
        Set<String> belegt = new HashSet<>();
        vorhanden.forEach(u -> belegt.add(u.getSchluessel()));
        String basis = schluesselAus(label);
        String schluessel = basis;
        for (int i = 2; belegt.contains(schluessel); i++) {
            schluessel = basis.substring(0, Math.min(basis.length(), 27)) + "_" + i;
        }
        WartungModulUntertyp neu = new WartungModulUntertyp();
        neu.setModulId(modul.getId());
        neu.setSchluessel(schluessel);
        neu.setLabel(label.strip());
        neu.setSortierung(vorhanden.stream().mapToInt(WartungModulUntertyp::getSortierung).max().orElse(0) + 1);
        untertypen.saveAndFlush(neu);
        return schluessel;
    }

    static String schluesselAus(String label) {
        String s = norm(label).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            s = "variante";
        }
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    /** Hängt einen Wert an eine Auswahlliste (eine Option pro Zeile), wenn er dort noch nicht steht; liefert die neue Liste oder {@code null}. */
    static String mitOption(String optionen, String wert) {
        String liste = optionen == null ? "" : optionen;
        for (String o : liste.split("\n")) {
            if (norm(o).equals(norm(wert))) {
                return null;
            }
        }
        return liste.isBlank() ? wert.strip() : liste.stripTrailing() + "\n" + wert.strip();
    }

    private int nehmeInKatalogAuf(Map<String, Set<String>> neu) {
        int n = 0;
        for (Map.Entry<String, Set<String>> e : neu.entrySet()) {
            for (var g : globaleFelder.findAllByOrderBySortierungAscIdAsc()) {
                if (norm(g.getLabel()).equals(norm(e.getKey())) && g.getFeldtyp() == WartungFeldtyp.AUSWAHL) {
                    for (String wert : e.getValue()) {
                        String liste = mitOption(g.getOptionen(), wert);
                        if (liste != null) {
                            g.setOptionen(liste);
                            n++;
                        }
                    }
                    globaleFelder.save(g);
                }
            }
            for (var f : modulFelder.findAll()) {
                if (norm(f.getLabel()).equals(norm(e.getKey())) && f.getFeldtyp() == WartungFeldtyp.AUSWAHL) {
                    for (String wert : e.getValue()) {
                        String liste = mitOption(f.getOptionen(), wert);
                        if (liste != null) {
                            f.setOptionen(liste);
                            n++;
                        }
                    }
                    modulFelder.save(f);
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- Kunden, Objekte, Türen

    private Kunde kundeFuer(long altId, Map<Long, JsonNode> json, Map<Long, Long> vorschlag, Lauf lauf) {
        Long bekannt = lauf.kundeErp.get(altId);
        if (bekannt == null) {
            bekannt = zuordnungen.findByQuelleAndTypAndAltId(QUELLE, "KUNDE", altId).map(WartungImportZuordnung::getErpId).orElse(vorschlag.get(altId));
        }
        if (bekannt != null) {
            Kunde k = kunden.findById(bekannt).orElse(null);
            if (k != null) {
                lauf.kundeErp.put(altId, k.getId());
                return k;
            }
        }
        JsonNode j = json.get(altId);
        if (j == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "In der Exportdatei fehlt der Kunde mit der Nummer " + altId + ".");
        }
        Kunde k = new Kunde();
        String firma = j.path("firma").asText("").strip();
        String person = (j.path("vorname").asText("").strip() + " " + j.path("nachname").asText("").strip()).strip();
        k.setName(firma.isEmpty() ? person : firma);
        k.setAnrede(Anrede.fromString(j.path("anrede").asText(null)));
        if (!firma.isEmpty() && !person.isEmpty()) {
            k.setAnsprechspartner(person);
        }
        k.setStrasse(leer(j.path("strasse")));
        k.setPlz(leer(j.path("plz")));
        k.setOrt(leer(j.path("ort")));
        k.setTelefon(leer(j.path("telefon")));
        String mail = leer(j.path("email"));
        if (mail != null && !kunden.existsByKundenEmail(mail)) {
            k.getKundenEmails().add(mail);
        }
        k.setKundennummer(kundennummern.reserviereNaechsteKundennummer());
        k = kunden.saveAndFlush(k);
        merke("KUNDE", altId, k.getId());
        lauf.kundeErp.put(altId, k.getId());
        lauf.kundenNeu++;
        return k;
    }

    private WartungObjekt objektFuer(long altId, Kunde kunde, Map<Long, JsonNode> json, Lauf lauf) {
        Long bekannt = zuordnungen.findByQuelleAndTypAndAltId(QUELLE, "OBJEKT", altId).map(WartungImportZuordnung::getErpId).orElse(lauf.objektErp.get(altId));
        if (bekannt != null) {
            WartungObjekt o = objekte.findById(bekannt).orElse(null);
            if (o != null) {
                return o;
            }
        }
        JsonNode j = json.get(altId);
        String name = j == null ? "" : j.path("bezeichnung").asText("").strip();
        for (WartungObjekt o : objekte.findByKundeIdOrderByBezeichnungAscIdAsc(kunde.getId())) {
            if (norm(o.getBezeichnung()).equals(norm(name))) {
                merke("OBJEKT", altId, o.getId());
                lauf.objektErp.put(altId, o.getId());
                return o;
            }
        }
        WartungObjekt o = new WartungObjekt();
        o.setKunde(kunde);
        o.setBezeichnung(name.isEmpty() ? "Objekt aus HandwerkPro" : name);
        o.setStrasse(j == null ? null : leer(j.path("strasse")));
        o.setPlz(j == null ? null : leer(j.path("plz")));
        o.setOrt(j == null ? null : leer(j.path("ort")));
        o.setNotizen(j == null ? null : leer(j.path("notizen")));
        o = objekte.saveAndFlush(o);
        merke("OBJEKT", altId, o.getId());
        lauf.objektErp.put(altId, o.getId());
        lauf.objekteNeu++;
        return o;
    }

    private WartungTuer tuerFuer(long altId, WartungObjekt objekt, Map<Long, JsonNode> json, Lauf lauf) {
        Long bekannt = zuordnungen.findByQuelleAndTypAndAltId(QUELLE, "TUER", altId).map(WartungImportZuordnung::getErpId).orElse(lauf.tuerErp.get(altId));
        if (bekannt != null) {
            WartungTuer t = tueren.findById(bekannt).orElse(null);
            if (t != null) {
                return t;
            }
        }
        JsonNode j = json.get(altId);
        String name = j == null ? "" : j.path("bezeichnung").asText("").strip();
        for (WartungTuer t : tueren.findByObjektIdOrderByBezeichnungAscIdAsc(objekt.getId())) {
            if (norm(t.getBezeichnung()).equals(norm(name))) {
                merke("TUER", altId, t.getId());
                lauf.tuerErp.put(altId, t.getId());
                return t;
            }
        }
        WartungTuer t = new WartungTuer();
        t.setObjekt(objekt);
        t.setBezeichnung(name.isEmpty() ? "Tür aus HandwerkPro" : name);
        t.setNotizen(j == null ? null : leer(j.path("notizen")));
        t = tueren.saveAndFlush(t);
        merke("TUER", altId, t.getId());
        lauf.tuerErp.put(altId, t.getId());
        lauf.tuerenNeu++;
        return t;
    }

    /** Fälligkeit der neu angelegten Türen: aus dem jüngsten abgeschlossenen Protokoll, wie beim Abschluss im ERP. */
    private void setzeWartungsplan(Lauf lauf) {
        for (Map.Entry<Long, LocalDate> e : lauf.neuesterAbschluss.entrySet()) {
            WartungTuer t = tueren.findById(e.getKey()).orElse(null);
            if (t == null || t.getNaechsteFaelligkeit() != null) {
                continue;
            }
            WartungProtokoll letztes = protokolle.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(t.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
            if (letztes != null) {
                t.setIntervallMonate(letztes.getIntervallMonate());
                t.setNaechsteFaelligkeit(letztes.getDatum().plusMonths(letztes.getIntervallMonate()));
                tueren.save(t);
            }
        }
    }

    // ---------------------------------------------------------------- Protokoll

    private WartungProtokoll legeProtokollAn(JsonNode w, ProtokollZeile zeile, Kunde kunde, WartungObjekt objekt, WartungTuer tuer, List<GewaehltesModul> gewaehlt,
            Map<Long, JsonNode> technikerJson, List<Mitarbeiter> alleMitarbeiter, Lauf lauf) {
        WartungProtokoll p = new WartungProtokoll();
        p.setNummer(zeile.altNummer());
        p.setKunde(kunde);
        p.setObjekt(objekt);
        p.setTuer(tuer);
        p.setTitel(leer(w.path("titel")));
        p.setProtokollTyp(zeile.protokollart());
        LocalDate datum = LocalDate.parse(w.path("datum").asText());
        p.setDatum(datum);
        int intervall = w.path("intervall_monate").asInt(12) > 0 ? w.path("intervall_monate").asInt(12) : 12;
        p.setIntervallMonate(intervall);
        p.setNaechsteWartung(w.hasNonNull("naechste_wartung") ? LocalDate.parse(w.path("naechste_wartung").asText()) : datum.plusMonths(intervall));
        p.setBemerkungen(leer(w.path("bemerkungen")));
        p.setAnsprechpartnerName(leer(w.path("ansprechpartner_name")));
        boolean fertig = "abgeschlossen".equals(w.path("status").asText());
        p.setStatus(fertig ? WartungStatus.ABGESCHLOSSEN : WartungStatus.OFFEN);
        if (fertig) {
            p.setAbgeschlossenAm(zeitpunkt(w.path("geaendert_am"), datum));
        }
        p.setAngelegtAm(zeitpunkt(w.path("erstellt_am"), datum));
        p.setUnterschriftTechniker(leer(w.path("unterschrift_techniker")));
        p.setUnterschriftTechnikerName(leer(w.path("unterschrift_techniker_name")));
        p.setUnterschriftKunde(leer(w.path("unterschrift_kunde")));
        p.setUnterschriftKundeName(leer(w.path("unterschrift_kunde_name")));
        JsonNode t = technikerJson.get(w.path("techniker_id").asLong(-1));
        if (t != null) {
            String name = (t.path("vorname").asText("") + " " + t.path("nachname").asText("")).strip();
            Mitarbeiter m = alleMitarbeiter.stream().filter(x -> norm((x.getVorname() == null ? "" : x.getVorname()) + " " + (x.getNachname() == null ? "" : x.getNachname())).equals(norm(name)))
                    .findFirst().orElse(null);
            p.setTechniker(m);
            if (m == null) {
                lauf.hinweise.add("Der Techniker „" + name + "“ ist im ERP kein Mitarbeiter. Die Protokolle zeigen ihn nur über die Unterschrift; bei Bedarf den Mitarbeiter anlegen und am Protokoll wählen.");
            }
        }
        for (GewaehltesModul g : gewaehlt) {
            p.getModule().add(new WartungProtokollModul(g.modul().getId(), g.modul().getModulKey(), g.modul().getName(),
                    g.untertyp() == null ? null : g.untertyp().getSchluessel(), g.untertyp() == null ? null : g.untertyp().getLabel()));
        }
        return protokolle.saveAndFlush(p);
    }

    /** Ergebnisse der Prüfpunkte und Werte der Datenfelder in das aus dem Katalog erzeugte Protokoll schreiben; was der Katalog nicht kennt, bleibt als eigener Punkt/eigenes Feld. */
    private void uebernehmeInhalt(WartungProtokoll p, JsonNode w, List<JsonNode> altePunkte, List<JsonNode> alteFelder, Set<String> herstellerListe, Lauf lauf) {
        List<WartungProtokollPruefpunkt> neuePunkte = protokollPruefpunkte.findByProtokollIdOrderBySortierungAscIdAsc(p.getId());
        Map<String, List<WartungProtokollPruefpunkt>> frei = new HashMap<>();
        neuePunkte.forEach(x -> frei.computeIfAbsent(norm(x.getLabel()), k -> new ArrayList<>()).add(x));
        int sortierung = neuePunkte.stream().mapToInt(WartungProtokollPruefpunkt::getSortierung).max().orElse(-1) + 1;
        for (JsonNode alt : altePunkte) {
            if (!"check".equals(alt.path("typ").asText())) {
                continue;
            }
            WartungErgebnis ergebnis = ergebnis(alt.path("ergebnis").asText(null));
            WartungErgebnis vorher = ergebnis(alt.path("vorheriges_ergebnis").asText(null));
            List<WartungProtokollPruefpunkt> treffer = frei.get(norm(alt.path("label").asText("")));
            WartungProtokollPruefpunkt ziel;
            if (treffer != null && !treffer.isEmpty()) {
                ziel = treffer.remove(0);
            } else {
                ziel = new WartungProtokollPruefpunkt();
                ziel.setProtokollId(p.getId());
                ziel.setLabel(alt.path("label").asText("").strip());
                ziel.setSortierung(sortierung++);
                lauf.eigene++;
            }
            ziel.setErgebnis(ergebnis);
            ziel.setBemerkung(leer(alt.path("bemerkung")));
            if (vorher != null) {
                ziel.setVorherigesErgebnis(vorher);
            }
            protokollPruefpunkte.save(ziel);
        }

        List<WartungProtokollDatenfeld> neueFelder = new ArrayList<>(protokollFelder.findByProtokollIdOrderBySortierungAscIdAsc(p.getId()));
        int[] feldSortierung = { neueFelder.stream().mapToInt(WartungProtokollDatenfeld::getSortierung).max().orElse(-1) + 1 };
        for (JsonNode f : alteFelder) {
            String label = f.path("label").asText("");
            String wert = f.path("wert").isNull() ? "" : f.path("wert").asText("");
            if (WartungImportAbbildung.istFluegelFeld(label)) {
                Fluegel fl = WartungImportAbbildung.fluegel(wert);
                fl.felder().forEach(e -> setze(p, neueFelder, e, feldSortierung, lauf));
                continue;
            }
            List<Ergebnis> erg = WartungImportAbbildung.abbilden(label, wert, herstellerListe);
            if (erg.isEmpty()) {
                if (!wert.isBlank() && !WartungImportAbbildung.istKeineAngabe(wert)) {
                    setze(p, neueFelder, new Ergebnis(label.strip(), wert.strip(), WartungImportDto.FREITEXT), feldSortierung, lauf);
                }
                continue;
            }
            erg.forEach(e -> setze(p, neueFelder, e, feldSortierung, lauf));
        }
        protokollFelder.saveAll(neueFelder);
    }

    private void setze(WartungProtokoll p, List<WartungProtokollDatenfeld> felder, Ergebnis e, int[] sortierung, Lauf lauf) {
        String aktion = e.aktion();
        if (WartungImportDto.UNGUELTIG.equals(aktion)) {
            lauf.verworfen++;
            lauf.hinweise.add("Ein Wert für „" + e.feld() + "“ war nicht verwendbar und blieb leer (" + p.getNummer() + ").");
            return;
        }
        if (WartungImportDto.LEER.equals(aktion) || e.wert() == null || e.wert().isBlank()) {
            return;
        }
        WartungProtokollDatenfeld d = felder.stream().filter(x -> norm(x.getLabel()).equals(norm(e.feld()))).findFirst().orElse(null);
        if (d == null) {
            d = new WartungProtokollDatenfeld();
            d.setProtokollId(p.getId());
            d.setLabel(e.feld());
            d.setFeldtyp(WartungFeldtyp.TEXT);
            d.setSortierung(sortierung[0]++);
            felder.add(d);
        }
        if (d.getWert() != null && !d.getWert().isBlank() && !norm(d.getWert()).equals(norm(e.wert()))) {
            p.setBemerkungen((p.getBemerkungen() == null ? "" : p.getBemerkungen() + "\n") + "Aus HandwerkPro zusätzlich: " + e.feld() + " – " + e.wert());
            protokolle.save(p);
            return;
        }
        d.setWert(e.wert());
        boolean liste = d.getFeldtyp() == WartungFeldtyp.AUSWAHL;
        if (liste) {
            String neu = mitOption(d.getOptionen(), e.wert());
            if (neu != null) {
                d.setOptionen(neu);
                lauf.neueOptionen.computeIfAbsent(e.feld(), k -> new LinkedHashSet<>()).add(e.wert());
            }
        }
    }

    // ---------------------------------------------------------------- Helfer

    private void merke(String typ, long altId, long erpId) {
        WartungImportZuordnung z = new WartungImportZuordnung();
        z.setQuelle(QUELLE);
        z.setTyp(typ);
        z.setAltId(altId);
        z.setErpId(erpId);
        zuordnungen.save(z);
    }

    static WartungErgebnis ergebnis(String alt) {
        if (alt == null) {
            return null;
        }
        return switch (norm(alt)) {
            case "i.o." -> WartungErgebnis.IO;
            case "n.i.o." -> WartungErgebnis.NIO;
            case "n.a." -> WartungErgebnis.NA;
            default -> null;
        };
    }

    private static LocalDateTime zeitpunkt(JsonNode n, LocalDate ersatz) {
        try {
            return n.isNull() || n.isMissingNode() ? ersatz.atStartOfDay() : LocalDateTime.parse(n.asText());
        } catch (RuntimeException e) {
            return ersatz.atStartOfDay();
        }
    }

    private static String leer(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.asText("").strip();
        return s.isEmpty() ? null : s;
    }

    private static Map<Long, JsonNode> nachId(JsonNode liste) {
        Map<Long, JsonNode> r = new HashMap<>();
        liste.forEach(n -> r.put(n.path("id").asLong(), n));
        return r;
    }

    private static Map<Long, List<JsonNode>> gruppiere(JsonNode liste) {
        Map<Long, List<JsonNode>> r = new HashMap<>();
        for (Iterator<JsonNode> it = liste.elements(); it.hasNext();) {
            JsonNode n = it.next();
            r.computeIfAbsent(n.path("wartung_id").asLong(), k -> new ArrayList<>()).add(n);
        }
        return r;
    }
}
