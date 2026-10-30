package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.ProtokollZeile;
import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto.Vorschau;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.KundeInfo;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.Katalog;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAnalyse.ModulInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Nur Dummy-Daten (Muster GmbH, Beispiel AG). */
class WartungImportAnalyseTest {

    private final ObjectMapper json = new ObjectMapper();
    private Katalog katalog;
    private ObjectNode export;
    private long naechsteId = 1;

    @BeforeEach
    void setUp() {
        Map<String, ModulInfo> module = Map.of(
                "m_bst", new ModulInfo("m_bst", "Brandschutztür", true, Map.of("einflg", "1-flügelig", "zweiflg", "2-flügelig", "schiebe", "Schiebetür"),
                        Set.of("zulassungsschild lesbar", "spaltmaße prüfen", "bänder prüfen", "zarge prüfen")),
                "m_boden", new ModulInfo("m_boden", "Bodendichtung", false, Map.of(), Set.of("boden 1", "boden 2", "boden 3", "boden 4")),
                "m_fsa", new ModulInfo("m_fsa", "Feststellanlage", false, Map.of("magnet", "Haftmagnet", "freilauf", "Freilauf"), Set.of("fsa 1", "fsa 2", "fsa 3", "fsa 4", "fsa 5", "fsa 6")));
        katalog = new Katalog(module, Map.of("Hersteller", Set.of("Schüco", "Hörmann"), "Anschlag", Set.of("DIN links", "DIN rechts")),
                Set.of("panikbeschlag / typ", "hersteller"), List.of(new KundeInfo(1L, "Muster GmbH", "K-1"), new KundeInfo(2L, "Erika Beispiel", "K-2")),
                Set.of(WartungImportAnalyse.objektSchluessel(1L, "Werk Nord")), Set.of(WartungImportAnalyse.tuerSchluessel(1L, "Werk Nord", "Tür 1")));
        export = json.createObjectNode().put("quelle", "HandwerkPro");
        export.putArray("untertypen").addObject().put("modul_key", "m_fsa").put("schluessel", "ut_1").put("label", "Standart ohne Handtaster");
        export.putArray("kunden");
        export.putArray("objekte");
        export.putArray("tueren");
        export.putArray("wartungen");
        export.putArray("komponenten");
        export.putArray("pruefpunkte");
    }

    private ObjectNode wartung(String nummer, String titel, String status, String typ, String... module) {
        ObjectNode w = ((ArrayNode) export.get("wartungen")).addObject();
        w.put("id", naechsteId++).put("nummer", nummer).put("titel", titel).put("status", status).put("protokoll_typ", typ);
        ArrayNode m = w.putArray("aktive_module");
        for (String k : module) {
            m.add(k);
        }
        w.putObject("modul_untertypen");
        return w;
    }

    private void punkt(long wartungId, String label) {
        ((ArrayNode) export.get("pruefpunkte")).addObject().put("wartung_id", wartungId).put("typ", "check").put("label", label);
    }

    private void feld(long wartungId, String label, String wert) {
        ((ArrayNode) export.get("komponenten")).addObject().put("wartung_id", wartungId).put("label", label).put("wert", wert);
    }

    private ProtokollZeile zeile(Vorschau v, String nummer) {
        return v.protokolle().stream().filter(p -> p.altNummer().equals(nummer)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Abgeschlossenes Protokoll mit Modulen: Module und Bauart aus dem Protokoll, Prüfpunkte zugeordnet, unbekannte als eigene gezählt, Katalog-Ergänzung als Hinweis")
    void mitModulen() {
        ObjectNode w = wartung("A-1", "Jahreswartung", "abgeschlossen", "Wartungsprotokoll", "m_bst", "m_fsa");
        ((ObjectNode) w.get("modul_untertypen")).put("m_bst", "zweiflg").put("m_fsa", "ut_1");
        for (String p : new String[] { "Zulassungsschild lesbar", "Spaltmaße prüfen ", "Bänder  prüfen", "Zarge prüfen", "Sonderpunkt Kunde" }) {
            punkt(w.get("id").asLong(), p);
        }
        ((ArrayNode) export.get("pruefpunkte")).addObject().put("wartung_id", w.get("id").asLong()).put("typ", "section_alt").put("label", "Überschrift");

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);

        ProtokollZeile z = zeile(v, "A-1");
        assertThat(z.module()).containsExactly("m_bst", "m_fsa");
        assertThat(z.modulQuelle()).isEqualTo("PROTOKOLL");
        assertThat(z.bauart()).isEqualTo("2-flügelig");
        assertThat(z.protokollart()).isEqualTo("Wartungsprotokoll");
        assertThat(z.pruefpunkte()).isEqualTo(5);
        assertThat(z.pruefpunkteZugeordnet()).isEqualTo(4);
        assertThat(z.pruefpunkteEigene()).isEqualTo(1);
        assertThat(z.fragen()).isEmpty();
        assertThat(v.eigenePruefpunkte()).extracting(e -> e.label()).containsExactly("Sonderpunkt Kunde");
        assertThat(v.hinweise()).containsExactly("Variante „Standard ohne Handtaster“ des Moduls Feststellanlage wird im Katalog ergänzt.");
        assertThat(v.zusammenfassung().abgeschlossen()).isEqualTo(1);
    }

    @Test
    @DisplayName("Flaches Altprotokoll ohne Module: Basismodul aus dem Titel, Bodendichtung aus den Prüfpunkten, Bauart aus der Flügelzahl; Herkunft wird angezeigt")
    void ohneModule() {
        ObjectNode w = wartung("A-2", "Instandhaltungsprotokoll – Brandschutztür (BT)", "offen", "Instandhaltungsprotokoll");
        long id = w.get("id").asLong();
        for (String p : new String[] { "Zulassungsschild lesbar", "Spaltmaße prüfen", "Bänder prüfen", "boden 1", "boden 2", "boden 3", "boden 4" }) {
            punkt(id, p);
        }
        feld(id, "Anzahl Türflügel / Torart", "2-flügelig");

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);

        ProtokollZeile z = zeile(v, "A-2");
        assertThat(z.module()).containsExactly("m_bst", "m_boden");
        assertThat(z.modulQuelle()).isEqualTo("TITEL_UND_PRUEFPUNKTE");
        assertThat(z.bauart()).isEqualTo("2-flügelig");
        assertThat(z.pruefpunkteEigene()).isZero();
        assertThat(v.zusammenfassung().modulErgaenzt()).isEqualTo(1);
        assertThat(v.zusammenfassung().offen()).isEqualTo(1);
    }

    @Test
    @DisplayName("Kein Modul erkennbar: keine Vermutung, sondern die Frage „Modul fehlt“; unbekannte Protokollart ebenso")
    void unklar() {
        wartung("A-3", "Wartungsprotokoll", "offen", "Wartungsprotokoll");
        wartung("A-4", "Wartungsprotokoll – Brandschutztür", "offen", "Fantasieprotokoll");

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);

        assertThat(zeile(v, "A-3").module()).isEmpty();
        assertThat(zeile(v, "A-3").modulQuelle()).isEqualTo("UNKLAR");
        assertThat(zeile(v, "A-3").fragen()).anyMatch(f -> f.startsWith("Modul fehlt"));
        assertThat(zeile(v, "A-4").protokollart()).isNull();
        assertThat(zeile(v, "A-4").fragen()).anyMatch(f -> f.contains("Fantasieprotokoll") && f.contains("gibt es im ERP nicht"));
        assertThat(v.zusammenfassung().modulUnklar()).isEqualTo(1);
    }

    @Test
    @DisplayName("Titelregeln: Brandschutztor, Rauchschutztür, Brandschutztür mit Feststellanlage")
    void titel() {
        assertThat(WartungImportAnalyse.moduleAusTitel("Instandhaltungsprotokoll – Brandschutztor")).containsExactly("m_tor");
        assertThat(WartungImportAnalyse.moduleAusTitel("Wartung Rauchschutztür")).containsExactly("m_rs");
        assertThat(WartungImportAnalyse.moduleAusTitel("Instandhaltungsprotokoll – Brandschutztür mit Feststellanlage")).containsExactly("m_bst", "m_fsa");
        assertThat(WartungImportAnalyse.moduleAusTitel("Wartungsprotokoll")).isEmpty();
        assertThat(WartungImportAnalyse.moduleAusTitel(null)).isEmpty();
    }

    @Test
    @DisplayName("Werte: Abbildungstabelle zählt Vorkommen, trennt Hersteller und Typ, nennt Neues für die Liste und behält eigene Felder; Freitext wird nur gezählt")
    void werte() {
        ObjectNode w = wartung("A-5", "x", "abgeschlossen", "Wartungsprotokoll", "m_bst");
        long id = w.get("id").asLong();
        feld(id, "Hersteller", "Hörmann H6");
        feld(id, "Anschlag", "DINL");
        feld(id, "Anschlag", "Pendeltür");
        feld(id, "Baujahr", "Keine Angabe");
        feld(id, "Standort / Lage", "Halle 3");
        feld(id, "Panikbeschlag / Typ", "Schüco");
        feld(id, "Türschließer ", "Dorma TS 93");
        feld(id, "Feuerwiederstand / Rauchschutzklasse", "T30 RS");

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);

        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Hersteller"); assertThat(x.neuerWert()).isEqualTo("Hörmann"); assertThat(x.aktion()).isEqualTo("ZUGEORDNET"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Typ / Modell"); assertThat(x.neuerWert()).isEqualTo("unverändert"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Anschlag"); assertThat(x.altWert()).isEqualTo("DINL"); assertThat(x.neuerWert()).isEqualTo("DIN links"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Anschlag"); assertThat(x.altWert()).isEqualTo("Pendeltür"); assertThat(x.aktion()).isEqualTo("NEU_IN_LISTE"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Baujahr"); assertThat(x.aktion()).isEqualTo("LEER"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Standort / Lage"); assertThat(x.altWert()).isEqualTo("Freitext"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Panikbeschlag / Typ"); assertThat(x.neuerWert()).isEqualTo("unverändert"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("(eigenes Feld) Türschließer"); assertThat(x.neuerWert()).isEqualTo("bleibt als eigenes Feld"); });
        assertThat(v.werte()).anySatisfy(x -> { assertThat(x.feld()).isEqualTo("Rauchschutz (RS)"); assertThat(x.neuerWert()).isEqualTo("Ja"); });
        assertThat(v.zusammenfassung().werteNeuInListe()).isEqualTo(1);
    }

    @Test
    @DisplayName("Kunden, Objekte, Türen: Treffer über Name oder Kundennummer, sonst neu; vorhandene Objekte und Türen zählen nicht als neu")
    void kundenObjekteTueren() {
        ((ArrayNode) export.get("kunden")).addObject().put("id", 10).put("firma", "muster gmbh ").put("kundennummer", "X-9");
        ((ArrayNode) export.get("kunden")).addObject().put("id", 11).put("firma", "Neue Firma AG");
        ((ArrayNode) export.get("kunden")).addObject().put("id", 12).put("firma", "").put("vorname", "Erika").put("nachname", "Beispiel");
        ((ArrayNode) export.get("objekte")).addObject().put("id", 100).put("kunden_id", 10).put("bezeichnung", "Werk Nord");
        ((ArrayNode) export.get("objekte")).addObject().put("id", 101).put("kunden_id", 10).put("bezeichnung", "Halle");
        ((ArrayNode) export.get("objekte")).addObject().put("id", 102).put("kunden_id", 11).put("bezeichnung", "Werk Nord");
        ((ArrayNode) export.get("tueren")).addObject().put("id", 1000).put("objekt_id", 100).put("bezeichnung", "Tür 1");
        ((ArrayNode) export.get("tueren")).addObject().put("id", 1001).put("objekt_id", 100).put("bezeichnung", "Tür 2");
        ((ArrayNode) export.get("tueren")).addObject().put("id", 1002).put("objekt_id", 102).put("bezeichnung", "Tür 1");
        ObjectNode w = wartung("A-6", "x", "offen", "Wartungsprotokoll", "m_bst");
        w.put("kunde_id", 10);

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);

        assertThat(v.kunden()).extracting(k -> k.altId() + ":" + k.vorschlag() + ":" + k.erpId()).containsExactly("10:ZUORDNEN:1", "11:NEU:null", "12:ZUORDNEN:2");
        assertThat(v.kunden().get(0).wartungen()).isEqualTo(1);
        assertThat(v.zusammenfassung().kundenNeu()).isEqualTo(1);
        assertThat(v.zusammenfassung().objekteNeu()).isEqualTo(2);
        assertThat(v.zusammenfassung().tuerenNeu()).isEqualTo(2);
    }

    @Test
    @DisplayName("Unbekanntes Modul im Protokoll wird als Frage gemeldet und nicht übernommen; leere Datei ergibt eine leere Vorschau")
    void unbekanntesModulUndLeer() {
        wartung("A-7", "x", "offen", "Wartungsprotokoll", "m_bst", "m_gibtsnicht");

        Vorschau v = WartungImportAnalyse.analysiere(export, katalog);
        assertThat(zeile(v, "A-7").module()).containsExactly("m_bst");
        assertThat(zeile(v, "A-7").fragen()).anyMatch(f -> f.contains("m_gibtsnicht"));

        JsonNode leer = json.createObjectNode().put("quelle", "HandwerkPro").set("wartungen", json.createArrayNode());
        assertThat(WartungImportAnalyse.analysiere(leer, katalog).zusammenfassung().wartungen()).isZero();
    }
}
