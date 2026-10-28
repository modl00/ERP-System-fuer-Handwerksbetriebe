package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.norm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.KundeZeile;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.ProtokollZeile;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.WertZeile;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Zusammenfassung;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.Ergebnis;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.Fluegel;

import tools.jackson.databind.JsonNode;

/**
 * Die Analyse für die Vorschau der Übernahme aus HandwerkPro: liest die Exportdatei und den Katalog des ERP und berechnet, was der Import tun würde. Reine Berechnung
 * ohne Datenbankzugriff und ohne etwas zu schreiben. Fragen, die der Mensch beantworten muss, stehen bei den Protokollen ({@code fragen}), nie als stille Vermutung.
 */
public final class WartungImportAnalyse {

    /** Was die Analyse über den Katalog des ERP wissen muss. */
    public record Katalog(Map<String, ModulInfo> module, Map<String, Set<String>> optionen, Set<String> felder, List<KundeInfo> kunden, Set<String> objekte, Set<String> tueren) {

        /** Die Auswahlmöglichkeiten eines Katalogfelds (Originalschreibweise); leer, wenn es dort keine Auswahl ist. */
        public Set<String> optionenVon(String feld) {
            return optionen.getOrDefault(feld, Set.of());
        }
    }

    /** Ein Modul des Katalogs: Untertypen (Schlüssel zu Bezeichnung) und die normalisierten Bezeichnungen seiner Prüfpunkte. */
    public record ModulInfo(String key, String name, boolean basis, Map<String, String> untertypen, Set<String> pruefpunkte) { }

    public record KundeInfo(Long id, String name, String kundennummer) { }

    private WartungImportAnalyse() { }

    public static String objektSchluessel(Long kundeId, String objekt) {
        return kundeId + "|" + norm(objekt);
    }

    public static String tuerSchluessel(Long kundeId, String objekt, String tuer) {
        return objektSchluessel(kundeId, objekt) + "|" + norm(tuer);
    }

    /** Module aus dem Titel eines Altprotokolls ohne Module; leer, wenn der Titel nichts Eindeutiges hergibt. */
    static List<String> moduleAusTitel(String titel) {
        String t = norm(titel);
        List<String> r = new ArrayList<>();
        if (t.contains("brandschutztor")) {
            r.add("m_tor");
        } else if (t.contains("brandschutztür") || t.contains("brandschutztuer")) {
            r.add("m_bst");
        } else if (t.contains("rauchschutztür") || t.contains("rauchschutztuer")) {
            r.add("m_rs");
        }
        if (!r.isEmpty() && t.contains("feststellanlage")) {
            r.add("m_fsa");
        }
        return r;
    }

    /**
     * Altprotokolle ohne Module haben einen flachen Aufbau: Alle Prüfpunkte stehen ohne Modul in einer Liste. Das Modul ergibt sich dann aus den Prüfpunkten selbst:
     * Ein Zusatzmodul gilt als gewählt, wenn mindestens die Hälfte seiner Katalogpunkte (mindestens zwei) im Protokoll stehen; fehlt ein Basismodul aus dem Titel,
     * gilt das Basismodul mit den meisten Treffern (mindestens drei). Gibt die Liste {@code module} zurück, ob etwas ergänzt wurde.
     */
    static boolean ergaenzeAusPruefpunkten(List<String> module, List<JsonNode> punkte, Katalog katalog) {
        return ergaenzeAusPruefpunkten(module, punkte, katalog, false);
    }

    /** Mit {@code streng} (Protokolle, die schon Module haben) gilt ein Zusatzmodul nur, wenn mindestens drei Viertel seiner Katalogpunkte (mindestens drei) im Protokoll stehen; ein Basismodul wird so nie ersetzt. */
    static boolean ergaenzeAusPruefpunkten(List<String> module, List<JsonNode> punkte, Katalog katalog, boolean streng) {
        Set<String> labels = new HashSet<>();
        for (JsonNode p : punkte) {
            if ("check".equals(p.path("typ").asText())) {
                labels.add(norm(p.path("label").asText()));
            }
        }
        boolean ergaenzt = false;
        if (!streng && module.stream().noneMatch(k -> katalog.module().containsKey(k) && katalog.module().get(k).basis())) {
            String beste = null;
            int bestTreffer = 2;
            for (ModulInfo m : katalog.module().values()) {
                int t = treffer(m, labels);
                if (m.basis() && t > bestTreffer) {
                    beste = m.key();
                    bestTreffer = t;
                }
            }
            if (beste != null) {
                module.add(0, beste);
                ergaenzt = true;
            }
        }
        for (ModulInfo m : katalog.module().values()) {
            if (m.basis() || module.contains(m.key())) {
                continue;
            }
            int t = treffer(m, labels);
            if (streng ? (t >= 3 && t * 4 >= m.pruefpunkte().size() * 3) : (t >= 2 && t * 2 >= m.pruefpunkte().size())) {
                module.add(m.key());
                ergaenzt = true;
            }
        }
        return ergaenzt;
    }

    private static int treffer(ModulInfo m, Set<String> labels) {
        int t = 0;
        for (String l : m.pruefpunkte()) {
            if (labels.contains(l)) {
                t++;
            }
        }
        return t;
    }

    public static Vorschau analysiere(JsonNode export, Katalog katalog) {
        Map<String, String> untertypLabel = new HashMap<>();
        for (JsonNode u : export.path("untertypen")) {
            untertypLabel.put(u.path("modul_key").asText() + "|" + u.path("schluessel").asText(), u.path("label").asText());
        }
        Map<Long, List<JsonNode>> felder = gruppiere(export.path("komponenten"));
        Map<Long, List<JsonNode>> punkte = gruppiere(export.path("pruefpunkte"));

        Map<String, Integer> eigene = new LinkedHashMap<>();
        Map<String, Integer> werte = new LinkedHashMap<>();
        Map<String, String[]> werteInfo = new HashMap<>();
        Set<String> hinweise = new LinkedHashSet<>();
        List<ProtokollZeile> zeilen = new ArrayList<>();
        Map<Long, Integer> proKunde = new HashMap<>();
        int abgeschlossen = 0;
        int offen = 0;
        int ergaenzt = 0;
        int unklar = 0;
        int ppGesamt = 0;
        int ppZu = 0;
        int ppEigene = 0;

        for (JsonNode w : export.path("wartungen")) {
            long id = w.path("id").asLong();
            String status = w.path("status").asText("");
            if (status.equals("abgeschlossen")) {
                abgeschlossen++;
            } else {
                offen++;
            }
            if (w.hasNonNull("kunde_id")) {
                proKunde.merge(w.path("kunde_id").asLong(), 1, Integer::sum);
            }
            List<String> fragen = new ArrayList<>();
            String art = WartungImportAbbildung.protokollart(w.path("protokoll_typ").asText(null));
            if (art == null) {
                fragen.add("Die Protokollart „" + w.path("protokoll_typ").asText("") + "“ gibt es im ERP nicht. Bitte wählen.");
            }

            // Module: aus dem Protokoll, sonst aus dem Titel, sonst Frage
            List<String> module = new ArrayList<>();
            for (JsonNode m : w.path("aktive_module")) {
                module.add(m.asText());
            }
            String quelle = "PROTOKOLL";
            List<JsonNode> pruefpunkteDiesesProtokolls = punkte.getOrDefault(id, List.of());
            List<String> ergaenzteModule = new ArrayList<>();
            if (module.isEmpty()) {
                List<String> ausTitel = moduleAusTitel(w.path("titel").asText(""));
                module = new ArrayList<>(ausTitel);
                quelle = module.isEmpty() ? "UNKLAR" : "TITEL";
                if (ergaenzeAusPruefpunkten(module, pruefpunkteDiesesProtokolls, katalog)) {
                    quelle = ausTitel.isEmpty() ? "PRUEFPUNKTE" : "TITEL_UND_PRUEFPUNKTE";
                }
                if (module.isEmpty()) {
                    unklar++;
                    fragen.add("Modul fehlt: Weder der Titel „" + w.path("titel").asText("") + "“ noch die Prüfpunkte nennen eines. Bitte wählen.");
                } else {
                    ergaenzt++;
                }
            }
            if (quelle.equals("PROTOKOLL")) {
                // Auch bei vorhandenen Modulen kann ein Zusatzmodul fehlen, dessen Prüfpunkte aber fast alle im Protokoll stehen (älterer Aufbau)
                List<String> davor = new ArrayList<>(module);
                if (ergaenzeAusPruefpunkten(module, pruefpunkteDiesesProtokolls, katalog, true)) {
                    module.stream().filter(k -> !davor.contains(k)).forEach(ergaenzteModule::add);
                }
            }
            List<String> bekannt = new ArrayList<>();
            for (String k : module) {
                if (katalog.module().containsKey(k)) {
                    bekannt.add(k);
                } else {
                    fragen.add("Das Modul „" + k + "“ gibt es im ERP nicht.");
                }
            }

            // Bauart aus dem Untertyp des Basismoduls, sonst aus der Flügelzahl
            String bauart = null;
            Map<String, String> utMap = new LinkedHashMap<>();
            JsonNode untertypen = w.path("modul_untertypen");
            String basis = bekannt.stream().filter(k -> katalog.module().get(k).basis()).findFirst().orElse(null);
            Fluegel fluegel = null;
            List<JsonNode> meineFelder = felder.getOrDefault(id, List.of());
            for (JsonNode f : meineFelder) {
                if (WartungImportAbbildung.istFluegelFeld(f.path("label").asText())) {
                    Fluegel neu = WartungImportAbbildung.fluegel(f.path("wert").asText(""));
                    if (fluegel == null || (fluegel.bauart() == null && neu.bauart() != null)) {
                        fluegel = neu;
                    }
                }
            }
            if (basis != null) {
                ModulInfo mi = katalog.module().get(basis);
                String schluessel = untertypen.path(basis).asText(null);
                if (schluessel != null && mi.untertypen().containsKey(schluessel)) {
                    bauart = mi.untertypen().get(schluessel);
                    utMap.put(basis, schluessel);
                } else if (schluessel != null) {
                    String alt = untertypLabel.getOrDefault(basis + "|" + schluessel, schluessel);
                    var treffer = mi.untertypen().entrySet().stream().filter(e -> norm(e.getValue()).equals(norm(alt))).findFirst().orElse(null);
                    if (treffer != null) {
                        bauart = treffer.getValue();
                        utMap.put(basis, treffer.getKey());
                    } else {
                        fragen.add("Die Bauart „" + alt + "“ gibt es im ERP nicht.");
                    }
                }
                if (bauart == null && fluegel != null && fluegel.bauart() != null && mi.untertypen().containsKey(fluegel.bauart())) {
                    bauart = mi.untertypen().get(fluegel.bauart());
                    utMap.put(basis, fluegel.bauart());
                }
                if (bauart == null && fragen.stream().noneMatch(f -> f.startsWith("Die Bauart"))) {
                    fragen.add("Die Bauart (1-flügelig, 2-flügelig, Schiebetür) ist nicht erkennbar. Bitte wählen.");
                }
            }
            // Untertypen der Zusatzmodule (zum Beispiel Feststellanlage), die der ERP noch nicht kennt
            for (String k : bekannt) {
                String schluessel = untertypen.path(k).asText(null);
                if (schluessel != null && !k.equals(basis)) {
                    if (katalog.module().get(k).untertypen().containsKey(schluessel)) {
                        utMap.put(k, schluessel);
                    } else {
                        String alt = untertypLabel.getOrDefault(k + "|" + schluessel, schluessel);
                        var treffer = katalog.module().get(k).untertypen().entrySet().stream().filter(e -> norm(e.getValue()).equals(norm(alt))).findFirst().orElse(null);
                        if (treffer != null) {
                            utMap.put(k, treffer.getKey());
                        } else {
                            utMap.put(k, "NEU:" + bereinigeTippfehler(alt));
                            hinweise.add("Variante „" + bereinigeTippfehler(alt) + "“ des Moduls " + katalog.module().get(k).name() + " wird im Katalog ergänzt.");
                        }
                    }
                }
            }

            // Prüfpunkte
            Set<String> katalogPunkte = new HashSet<>();
            for (String k : bekannt) {
                katalogPunkte.addAll(katalog.module().get(k).pruefpunkte());
            }
            int pp = 0;
            int zu = 0;
            for (JsonNode p : pruefpunkteDiesesProtokolls) {
                if (!"check".equals(p.path("typ").asText())) {
                    continue;
                }
                pp++;
                if (katalogPunkte.contains(norm(p.path("label").asText()))) {
                    zu++;
                } else {
                    eigene.merge(p.path("label").asText("").strip(), 1, Integer::sum);
                }
            }
            ppGesamt += pp;
            ppZu += zu;
            ppEigene += pp - zu;

            // Datenfelder
            for (JsonNode f : meineFelder) {
                String label = f.path("label").asText("");
                String wert = f.path("wert").isNull() ? "" : f.path("wert").asText("");
                if (WartungImportAbbildung.istFluegelFeld(label)) {
                    Fluegel fl = WartungImportAbbildung.fluegel(wert);
                    zaehle(werte, werteInfo, "Bauart (aus Flügelzahl)", wert, fl.bauart() == null ? "" : bauartText(fl.bauart()), fl.bauart() == null
                            ? (WartungImportAbbildung.istKeineAngabe(wert) ? WartungImportDto.LEER : WartungImportDto.UNGUELTIG) : WartungImportDto.ZUGEORDNET);
                    for (Ergebnis e : fl.felder()) {
                        zaehle(werte, werteInfo, e.feld(), wert, e.wert(), e.aktion());
                    }
                    continue;
                }
                List<Ergebnis> erg = WartungImportAbbildung.abbilden(label, wert, katalog.optionenVon(WartungImportAbbildung.F_HERSTELLER));
                if (erg.isEmpty()) {
                    if (katalog.felder().contains(norm(label))) {
                        // Das Feld gibt es im Katalog unter demselben Namen (zum Beispiel Panikbeschlag / Typ): der Wert wird unverändert übernommen
                        if (!wert.isBlank() && !WartungImportAbbildung.istKeineAngabe(wert)) {
                            zaehle(werte, werteInfo, label.strip(), "Freitext", "unverändert", WartungImportDto.FREITEXT);
                        }
                    } else if (!wert.isBlank() && !WartungImportAbbildung.istKeineAngabe(wert)) {
                        zaehle(werte, werteInfo, "(eigenes Feld) " + label.strip(), "Freitext", "bleibt als eigenes Feld", WartungImportDto.FREITEXT);
                    }
                    continue;
                }
                erg = gegenListe(erg, katalog);
                for (Ergebnis e : erg) {
                    boolean frei = WartungImportDto.FREITEXT.equals(e.aktion());
                    zaehle(werte, werteInfo, e.feld(), frei ? "Freitext" : wert.strip(), frei ? "unverändert" : e.wert(), e.aktion());
                }
            }

            zeilen.add(new ProtokollZeile(id, w.path("nummer").asText(""), w.path("titel").asText(""), status, art, bekannt, quelle, ergaenzteModule, bauart, utMap, pp, zu, pp - zu, fragen));
        }

        List<WertZeile> wertZeilen = new ArrayList<>();
        int neuInListe = 0;
        int ungueltig = 0;
        for (Map.Entry<String, Integer> e : werte.entrySet()) {
            String[] i = werteInfo.get(e.getKey());
            wertZeilen.add(new WertZeile(i[0], i[1], i[2], e.getValue(), i[3]));
            if (i[3].equals(WartungImportDto.NEU_IN_LISTE)) {
                neuInListe += e.getValue();
            } else if (i[3].equals(WartungImportDto.UNGUELTIG)) {
                ungueltig += e.getValue();
            }
        }
        wertZeilen.sort(Comparator.comparing(WertZeile::feld).thenComparing(Comparator.comparingInt(WertZeile::anzahl).reversed()));

        // Kunden, Objekte, Türen
        List<KundeZeile> kunden = new ArrayList<>();
        Map<Long, Long> kundeErp = new HashMap<>();
        for (JsonNode k : export.path("kunden")) {
            long altId = k.path("id").asLong();
            String name = kundenname(k);
            KundeInfo treffer = katalog.kunden().stream().filter(x -> (notBlank(k.path("kundennummer").asText(null)) && x.kundennummer() != null
                    && x.kundennummer().equalsIgnoreCase(k.path("kundennummer").asText().strip())) || norm(x.name()).equals(norm(name))).findFirst().orElse(null);
            kunden.add(new KundeZeile(altId, name, treffer == null ? null : treffer.id(), treffer == null ? null : treffer.name(), treffer == null ? "NEU" : "ZUORDNEN",
                    proKunde.getOrDefault(altId, 0)));
            if (treffer != null) {
                kundeErp.put(altId, treffer.id());
            }
        }
        Map<Long, Long> objektKunde = new HashMap<>();
        Map<Long, String> objektName = new HashMap<>();
        int objekteNeu = 0;
        for (JsonNode o : export.path("objekte")) {
            long kundenId = o.path("kunden_id").asLong();
            objektKunde.put(o.path("id").asLong(), kundenId);
            objektName.put(o.path("id").asLong(), o.path("bezeichnung").asText(""));
            Long erp = kundeErp.get(kundenId);
            if (erp == null || !katalog.objekte().contains(objektSchluessel(erp, o.path("bezeichnung").asText("")))) {
                objekteNeu++;
            }
        }
        int tuerenNeu = 0;
        for (JsonNode t : export.path("tueren")) {
            long objektId = t.path("objekt_id").asLong();
            Long erp = kundeErp.get(objektKunde.get(objektId));
            if (erp == null || !katalog.tueren().contains(tuerSchluessel(erp, objektName.getOrDefault(objektId, ""), t.path("bezeichnung").asText("")))) {
                tuerenNeu++;
            }
        }
        int kundenNeu = (int) kunden.stream().filter(k -> k.erpId() == null).count();

        Zusammenfassung z = new Zusammenfassung(zeilen.size(), abgeschlossen, offen, ergaenzt, unklar, kundenNeu, objekteNeu, tuerenNeu, ppGesamt, ppZu, ppEigene, neuInListe, ungueltig);
        List<WartungImportDto.EigenerPunkt> eigenePunkte = eigene.entrySet().stream().map(e -> new WartungImportDto.EigenerPunkt(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(WartungImportDto.EigenerPunkt::anzahl).reversed().thenComparing(WartungImportDto.EigenerPunkt::label)).toList();
        return new Vorschau(z, kunden, zeilen, wertZeilen, eigenePunkte, new ArrayList<>(hinweise));
    }

    /** Prüft zugeordnete und neue Werte gegen die Auswahlliste des Katalogfelds: Steht der Wert dort, ist er zugeordnet, sonst kommt er als NEU_IN_LISTE in die Vorschau. */
    static List<Ergebnis> gegenListe(List<Ergebnis> ergebnisse, Katalog katalog) {
        List<Ergebnis> r = new ArrayList<>();
        for (Ergebnis e : ergebnisse) {
            Set<String> liste = katalog.optionenVon(e.feld());
            boolean pruefen = !liste.isEmpty() && !e.wert().isEmpty() && !WartungImportAbbildung.KEINE_ANGABE.equals(e.wert())
                    && (WartungImportDto.ZUGEORDNET.equals(e.aktion()) || WartungImportDto.NEU_IN_LISTE.equals(e.aktion()));
            if (pruefen) {
                boolean drin = liste.stream().anyMatch(o -> norm(o).equals(norm(e.wert())));
                r.add(new Ergebnis(e.feld(), e.wert(), drin ? WartungImportDto.ZUGEORDNET : WartungImportDto.NEU_IN_LISTE));
            } else {
                r.add(e);
            }
        }
        return r;
    }

    private static void zaehle(Map<String, Integer> werte, Map<String, String[]> info, String feld, String alt, String neu, String aktion) {
        String key = feld + "\u0001" + alt + "\u0001" + neu + "\u0001" + aktion;
        werte.merge(key, 1, Integer::sum);
        info.putIfAbsent(key, new String[] { feld, alt, neu, aktion });
    }

    private static String bauartText(String schluessel) {
        return switch (schluessel) {
            case "einflg" -> "1-flügelig";
            case "zweiflg" -> "2-flügelig";
            default -> "Schiebetür";
        };
    }

    private static String bereinigeTippfehler(String text) {
        return text.replace("Standart", "Standard");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String kundenname(JsonNode k) {
        String firma = k.path("firma").asText("").strip();
        if (!firma.isEmpty()) {
            return firma;
        }
        return (k.path("vorname").asText("").strip() + " " + k.path("nachname").asText("").strip()).strip();
    }

    private static Map<Long, List<JsonNode>> gruppiere(JsonNode liste) {
        Map<Long, List<JsonNode>> r = new HashMap<>();
        for (JsonNode n : liste) {
            r.computeIfAbsent(n.path("wartung_id").asLong(), k -> new ArrayList<>()).add(n);
        }
        return r;
    }
}
