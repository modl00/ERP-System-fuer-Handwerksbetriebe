package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto.Position;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineEingabeDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MaschineRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Mitgelieferte <b>Leistungen für Metallbau</b> mit erfundenen Zeiten, vollständig verschachtelt: Leistungen benutzen Baugruppen, diese benutzen Artikel aus dem Artikelstamm,
 * Arbeitsgänge (mit ihren Maschinen) und Fremdleistungen (Verzinken, Pulverbeschichten). Beim Übernehmen legt das Programm alles an, was dafür fehlt:
 * die Kategorien, Abteilungen, Arbeitsgänge, Stahlprofile und Bleche als Artikel (über {@link MetallbauVorlagenService}), Maschinen, Beispiel-Lieferanten mit ihren Fremdleistungen
 * und zuletzt die Leistungen mit ihrer Kalkulation. Was es schon gibt (gleicher Name oder gleiche Artikelnummer), wird übersprungen; ein zweiter Lauf legt nichts doppelt an.
 * <p>Es werden <b>keine Preise, Stundensätze und echten Lieferanten</b> angelegt. Die Leistungen sind nicht „kalkuliert“, bis Stundensätze und Artikelpreise eingetragen sind.</p>
 */
@Service
@RequiredArgsConstructor
public class MetallbauLeistungenService {

    static final String RESSOURCE = "vorlagen/metallbau/leistungen.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_AUSWAHL = 100;

    private final MetallbauVorlagenService vorlagen;
    private final LeistungRepository leistungen;
    private final LeistungKalkulationService kalkulation;
    private final ProduktkategorieRepository produktkategorien;
    private final MaschineService maschineService;
    private final MaschineRepository maschinen;
    private final ArbeitsgangRepository arbeitsgaenge;
    private final ArtikelRepository artikel;
    private final AbteilungRepository abteilungen;
    private final LieferantenRepository lieferanten;
    private final FremdleistungRepository fremdleistungen;

    // ───────────────────────────── Katalog ─────────────────────────────

    public record KategorieVorlage(String name, List<String> unter) { }

    public record MaschineVorlage(String name, BigDecimal satz, String bemerkung, List<String> arbeitsgaenge) { }

    public record FremdVorlage(String lieferant, LieferantRolle rolle, Integer lieferzeitTage, String bezeichnung, Fremdleistung.Einheit einheit, BigDecimal preis, BigDecimal mindestpreis,
            BigDecimal einmal, BigDecimal anteil, BigDecimal charge, String erklaerung) { }

    public record PositionVorlage(String typ, String artikel, String arbeitsgang, String baugruppe, String fremdleistung, BigDecimal menge, BigDecimal verschnitt, BigDecimal ruest,
            BigDecimal bearbeitung, BigDecimal anzahl, String mengeAus) { }

    public record LeistungVorlage(String name, Verrechnungseinheit einheit, String kategorie, boolean baugruppe, BigDecimal losgroesse, BigDecimal zuschlag, String beschreibung,
            List<PositionVorlage> positionen) { }

    record Katalog(String hinweis, List<KategorieVorlage> kategorien, List<MaschineVorlage> maschinen, List<FremdVorlage> fremdleistungen, List<LeistungVorlage> leistungen) { }

    private volatile Katalog katalog;

    Katalog katalog() {
        Katalog k = katalog;
        if (k == null) {
            synchronized (this) {
                if (katalog == null) {
                    Katalog gelesen = lese();
                    pruefe(gelesen);
                    katalog = gelesen;
                }
                k = katalog;
            }
        }
        return k;
    }

    private static Katalog lese() {
        try (InputStream in = new ClassPathResource(RESSOURCE).getInputStream()) {
            JsonNode w = JSON.readTree(in);
            List<KategorieVorlage> kategorien = new ArrayList<>();
            for (JsonNode k : w.path("kategorien")) {
                List<String> unter = new ArrayList<>();
                k.path("unter").forEach(u -> unter.add(u.asText()));
                kategorien.add(new KategorieVorlage(k.path("name").asText(), unter));
            }
            List<MaschineVorlage> maschinen = new ArrayList<>();
            for (JsonNode m : w.path("maschinen")) {
                List<String> gaenge = new ArrayList<>();
                m.path("arbeitsgaenge").forEach(a -> gaenge.add(a.asText()));
                maschinen.add(new MaschineVorlage(m.path("name").asText(), zahl(m, "satz"), m.path("bemerkung").asText(null), gaenge));
            }
            List<FremdVorlage> fremd = new ArrayList<>();
            for (JsonNode f : w.path("fremdleistungen")) {
                fremd.add(new FremdVorlage(f.path("lieferant").asText(), LieferantRolle.valueOf(f.path("rolle").asText()), f.hasNonNull("lieferzeitTage") ? f.path("lieferzeitTage").asInt() : null,
                        f.path("bezeichnung").asText(), Fremdleistung.Einheit.valueOf(f.path("einheit").asText()), zahl(f, "preis"), zahl(f, "mindestpreis"), zahl(f, "einmal"), zahl(f, "anteil"),
                        zahl(f, "charge"), f.path("erklaerung").asText("")));
            }
            List<LeistungVorlage> leistungen = new ArrayList<>();
            for (JsonNode l : w.path("leistungen")) {
                List<PositionVorlage> pos = new ArrayList<>();
                for (JsonNode p : l.path("positionen")) {
                    pos.add(new PositionVorlage(p.path("typ").asText(), p.path("artikel").asText(null), p.path("arbeitsgang").asText(null), p.path("baugruppe").asText(null), p.path("fremdleistung").asText(null),
                            zahl(p, "menge"), zahl(p, "verschnitt"), zahl(p, "ruest"), zahl(p, "bearbeitung"), zahl(p, "anzahl"), p.path("mengeAus").asText(null)));
                }
                leistungen.add(new LeistungVorlage(l.path("name").asText(), Verrechnungseinheit.valueOf(l.path("einheit").asText()), l.path("kategorie").asText(), l.path("baugruppe").asBoolean(false),
                        l.hasNonNull("losgroesse") ? new BigDecimal(l.path("losgroesse").asText()) : BigDecimal.ONE, zahl(l, "zuschlag"), l.path("beschreibung").asText(""), List.copyOf(pos)));
            }
            return new Katalog(w.path("hinweis").asText(""), List.copyOf(kategorien), List.copyOf(maschinen), List.copyOf(fremd), List.copyOf(leistungen));
        } catch (Exception e) {
            throw new IllegalStateException("Der Katalog der Leistungen ist nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static BigDecimal zahl(JsonNode n, String feld) {
        return n.hasNonNull(feld) ? new BigDecimal(n.path(feld).asText().strip().replace(',', '.')) : null;
    }

    /** Ein Fehler im Katalog ist ein Programmfehler und fällt sofort auf: Jeder Bezug muss auflösbar sein, Baugruppen dürfen sich nicht im Kreis benutzen. */
    private void pruefe(Katalog k) {
        Set<String> gaenge = new HashSet<>();
        vorlagen.arbeitsgangKatalog().forEach(a -> gaenge.add(a.name()));
        Set<String> nummern = new HashSet<>();
        vorlagen.profilKatalog().forEach(p -> nummern.add(p.nummer()));
        Set<String> fremd = new HashSet<>();
        k.fremdleistungen().forEach(f -> fremd.add(f.bezeichnung()));
        Set<String> kategorien = new HashSet<>();
        k.kategorien().forEach(c -> { kategorien.add(c.name()); kategorien.addAll(c.unter()); });
        Map<String, LeistungVorlage> nachName = new LinkedHashMap<>();
        for (LeistungVorlage l : k.leistungen()) {
            if (nachName.put(l.name(), l) != null) {
                throw new IllegalStateException("Die Leistung „" + l.name() + "“ steht doppelt im Katalog.");
            }
        }
        for (MaschineVorlage m : k.maschinen()) {
            m.arbeitsgaenge().forEach(a -> pruefeGang(gaenge, a, "Maschine " + m.name()));
        }
        for (LeistungVorlage l : k.leistungen()) {
            if (!kategorien.contains(l.kategorie())) {
                throw new IllegalStateException("Die Kategorie „" + l.kategorie() + "“ von „" + l.name() + "“ gibt es im Katalog nicht.");
            }
            if (l.positionen().isEmpty()) {
                throw new IllegalStateException("Die Leistung „" + l.name() + "“ hat keine Positionen.");
            }
            for (PositionVorlage p : l.positionen()) {
                switch (p.typ()) {
                    case "ARTIKEL" -> {
                        if (!nummern.contains(p.artikel())) {
                            throw new IllegalStateException("Der Artikel „" + p.artikel() + "“ in „" + l.name() + "“ gibt es in den Stahlprofilen nicht.");
                        }
                        if (p.menge() == null || p.menge().signum() <= 0) {
                            throw new IllegalStateException("Der Artikel „" + p.artikel() + "“ in „" + l.name() + "“ braucht eine Menge.");
                        }
                    }
                    case "ARBEIT" -> {
                        pruefeGang(gaenge, p.arbeitsgang(), l.name());
                        if ((p.ruest() == null || p.ruest().signum() == 0) && (p.bearbeitung() == null || p.bearbeitung().signum() == 0)) {
                            throw new IllegalStateException("Der Arbeitsgang „" + p.arbeitsgang() + "“ in „" + l.name() + "“ braucht eine Zeit.");
                        }
                    }
                    case "BAUGRUPPE" -> {
                        LeistungVorlage b = nachName.get(p.baugruppe());
                        if (b == null || !b.baugruppe()) {
                            throw new IllegalStateException("Die Baugruppe „" + p.baugruppe() + "“ in „" + l.name() + "“ gibt es im Katalog nicht.");
                        }
                    }
                    case "FREMDLEISTUNG" -> {
                        if (!fremd.contains(p.fremdleistung())) {
                            throw new IllegalStateException("Die Fremdleistung „" + p.fremdleistung() + "“ in „" + l.name() + "“ gibt es im Katalog nicht.");
                        }
                    }
                    default -> throw new IllegalStateException("Unbekannte Zeile „" + p.typ() + "“ in „" + l.name() + "“.");
                }
            }
        }
        for (LeistungVorlage l : k.leistungen()) {
            reihenfolge(l.name(), nachName, new LinkedHashSet<>(), new ArrayList<>());
        }
    }

    private static void pruefeGang(Set<String> gaenge, String name, String wo) {
        if (name == null || !gaenge.contains(name)) {
            throw new IllegalStateException("Der Arbeitsgang „" + name + "“ (" + wo + ") gibt es im Katalog der Arbeitsgänge nicht.");
        }
    }

    /** Die Leistung und alles, was sie an Baugruppen braucht, so geordnet, dass Baugruppen vor ihren Benutzern stehen. */
    private static void reihenfolge(String name, Map<String, LeistungVorlage> nachName, Set<String> weg, List<String> ergebnis) {
        if (ergebnis.contains(name)) {
            return;
        }
        if (!weg.add(name)) {
            throw new IllegalStateException("Die Baugruppen im Katalog benutzen sich im Kreis: " + String.join(" → ", weg) + " → " + name);
        }
        for (PositionVorlage p : nachName.get(name).positionen()) {
            if ("BAUGRUPPE".equals(p.typ())) {
                reihenfolge(p.baugruppe(), nachName, weg, ergebnis);
            }
        }
        weg.remove(name);
        ergebnis.add(name);
    }

    // ───────────────────────────── Vorschau ─────────────────────────────

    /** {@code vorhanden}: Es gibt schon eine Leistung mit diesem Namen. {@code enthaelt}: die Baugruppen darin (in der Anzeige eingerückt). */
    public record Zeile(String name, String einheit, String kategorie, boolean baugruppe, boolean vorhanden, String beschreibung, int positionen, List<String> enthaelt) { }

    public record FremdZeile(String bezeichnung, String lieferant, String einheit, BigDecimal preis, BigDecimal mindestpreis, String erklaerung) { }

    public record Vorschau(String hinweis, List<Zeile> leistungen, List<FremdZeile> fremdleistungen, int maschinen, int kategorien) { }

    @Transactional(readOnly = true)
    public Vorschau vorschau() {
        Katalog k = katalog();
        Set<String> vorhanden = new HashSet<>();
        leistungen.findAll().forEach(l -> vorhanden.add(l.getBezeichnung().toLowerCase(Locale.ROOT)));
        List<Zeile> zeilen = new ArrayList<>();
        for (LeistungVorlage l : k.leistungen()) {
            List<String> enthaelt = l.positionen().stream().filter(p -> "BAUGRUPPE".equals(p.typ())).map(PositionVorlage::baugruppe).distinct().toList();
            zeilen.add(new Zeile(l.name(), l.einheit().getAnzeigename(), l.kategorie(), l.baugruppe(), vorhanden.contains(l.name().toLowerCase(Locale.ROOT)), l.beschreibung(), l.positionen().size(), enthaelt));
        }
        List<FremdZeile> fremd = k.fremdleistungen().stream().map(f -> new FremdZeile(f.bezeichnung(), f.lieferant(), f.einheit().kurz(), f.preis(), f.mindestpreis(), f.erklaerung())).toList();
        return new Vorschau(k.hinweis(), zeilen, fremd, k.maschinen().size(), k.kategorien().size());
    }

    // ───────────────────────────── Übernehmen ─────────────────────────────

    /** Was angelegt wurde (Zahlen); {@code uebersprungen} = Leistungen, die es schon gab. */
    public record Ergebnis(int leistungen, int uebersprungen, int arbeitsgaenge, int artikel, int maschinen, int kategorien, int lieferanten, int fremdleistungen) { }

    @Transactional
    public Ergebnis uebernehmen(List<String> namen) {
        if (namen == null || namen.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens eine Leistung auswählen.");
        }
        if (namen.size() > MAX_AUSWAHL) {
            throw new IllegalArgumentException("Es können höchstens " + MAX_AUSWAHL + " Leistungen auf einmal übernommen werden.");
        }
        Katalog k = katalog();
        Map<String, LeistungVorlage> nachName = new LinkedHashMap<>();
        k.leistungen().forEach(l -> nachName.put(l.name(), l));
        List<String> geordnet = new ArrayList<>();
        for (String n : namen.stream().filter(x -> x != null).distinct().toList()) {
            if (!nachName.containsKey(n)) {
                throw new IllegalArgumentException("Die Leistung „" + n + "“ gibt es im Katalog nicht.");
            }
            reihenfolge(n, nachName, new LinkedHashSet<>(), geordnet);
        }
        // Was brauchen die Leistungen insgesamt?
        Set<String> gaengeNoetig = new LinkedHashSet<>();
        Set<String> nummernNoetig = new LinkedHashSet<>();
        Set<String> fremdNoetig = new LinkedHashSet<>();
        Set<String> kategorienNoetig = new LinkedHashSet<>();
        for (String n : geordnet) {
            LeistungVorlage l = nachName.get(n);
            kategorienNoetig.add(l.kategorie());
            for (PositionVorlage p : l.positionen()) {
                switch (p.typ()) {
                    case "ARTIKEL" -> nummernNoetig.add(p.artikel());
                    case "ARBEIT" -> gaengeNoetig.add(p.arbeitsgang());
                    case "FREMDLEISTUNG" -> fremdNoetig.add(p.fremdleistung());
                    default -> { }
                }
            }
        }

        abteilungenSichern();
        int neueGaenge = arbeitsgaengeSichern(gaengeNoetig);
        int neueArtikel = artikelSichern(nummernNoetig);
        int neueMaschinen = maschinenSichern(k, gaengeNoetig);
        Map<String, Produktkategorie> kat = new HashMap<>();
        int neueKategorien = kategorienSichern(k, kategorienNoetig, nachName, kat);
        int[] lief = new int[2];
        Map<String, Fremdleistung> fremd = fremdleistungenSichern(k, fremdNoetig, lief);

        Map<String, Arbeitsgang> gangNachName = new HashMap<>();
        arbeitsgaenge.findAll().forEach(a -> gangNachName.put(a.getBeschreibung().toLowerCase(Locale.ROOT), a));
        Map<String, Leistung> vorhanden = new HashMap<>();
        leistungen.findAll().forEach(l -> vorhanden.put(l.getBezeichnung().toLowerCase(Locale.ROOT), l));
        Map<String, ProfilName> namenJeNummer = new HashMap<>();
        vorlagen.profilKatalog().forEach(p -> namenJeNummer.put(p.nummer(), new ProfilName(p.name())));
        int angelegt = 0;
        int uebersprungen = 0;
        Map<String, Leistung> ergebnis = new HashMap<>();
        for (String n : geordnet) {
            LeistungVorlage l = nachName.get(n);
            Leistung da = vorhanden.get(n.toLowerCase(Locale.ROOT));
            if (da != null) {
                ergebnis.put(n, da);
                uebersprungen++;
                continue;
            }
            Leistung neu = new Leistung();
            neu.setBezeichnung(l.name());
            neu.setBeschreibung(l.beschreibung());
            neu.setEinheit(l.einheit());
            neu.setPreis(BigDecimal.ZERO);
            neu.setKategorie(kat.get(l.kategorie()));
            neu.setBaugruppe(l.baugruppe());
            neu = leistungen.save(neu);
            List<Position> positionen = new ArrayList<>();
            for (PositionVorlage p : l.positionen()) {
                positionen.add(position(p, gangNachName, namenJeNummer, ergebnis, fremd));
            }
            kalkulation.speichern(neu.getId(), new LeistungKalkulationSpeichernDto(false, l.zuschlag(), l.losgroesse(), positionen));
            ergebnis.put(n, neu);
            angelegt++;
        }
        return new Ergebnis(angelegt, uebersprungen, neueGaenge, neueArtikel, neueMaschinen, neueKategorien, lief[0], lief[1]);
    }

    private record ProfilName(String name) { }

    private Position position(PositionVorlage p, Map<String, Arbeitsgang> gaenge, Map<String, ProfilName> profile, Map<String, Leistung> bisher, Map<String, Fremdleistung> fremd) {
        return switch (p.typ()) {
            case "ARTIKEL" -> {
                Artikel a = artikel.findByArtikelnummer(p.artikel()).or(() -> artikel.findFirstByProduktnameIgnoreCase(profile.get(p.artikel()).name()))
                        .orElseThrow(() -> new IllegalStateException("Der Artikel „" + p.artikel() + "“ wurde nicht angelegt."));
                yield new Position("ARTIKEL", a.getId(), null, p.menge(), null, null, null, null, null, p.verschnitt());
            }
            case "ARBEIT" -> new Position("ARBEIT", null, gaenge.get(p.arbeitsgang().toLowerCase(Locale.ROOT)).getId(), null, null, p.ruest(), p.bearbeitung(), null, p.anzahl());
            case "BAUGRUPPE" -> new Position("BAUGRUPPE", null, null, p.menge(), null, null, null, bisher.get(p.baugruppe()).getId(), null);
            case "FREMDLEISTUNG" -> new Position("FREMDLEISTUNG", null, null, null, null, null, null, null, null, null, null, fremd.get(p.fremdleistung()).getId(), null, p.mengeAus());
            default -> throw new IllegalStateException("Unbekannte Zeile " + p.typ());
        };
    }

    // ───────────────────────────── Voraussetzungen anlegen ─────────────────────────────

    private void abteilungenSichern() {
        for (String name : List.of("Büro", "Werkstatt", "Montage")) {
            if (abteilungen.findByName(name).isEmpty()) {
                Abteilung a = new Abteilung();
                a.setName(name);
                abteilungen.save(a);
            }
        }
    }

    private int arbeitsgaengeSichern(Set<String> noetig) {
        Set<String> da = new HashSet<>();
        arbeitsgaenge.findAll().forEach(a -> da.add(a.getBeschreibung().toLowerCase(Locale.ROOT)));
        List<String> fehlt = noetig.stream().filter(n -> !da.contains(n.toLowerCase(Locale.ROOT))).toList();
        return fehlt.isEmpty() ? 0 : vorlagen.arbeitsgaengeUebernehmen(fehlt, null).angelegt();
    }

    private int artikelSichern(Set<String> nummern) {
        Set<String> da = artikel.alleArtikelnummern();
        Set<String> namenDa = artikel.alleProduktnamenKlein();
        Map<String, String> nameJeNummer = new HashMap<>();
        vorlagen.profilKatalog().forEach(p -> nameJeNummer.put(p.nummer(), p.name()));
        List<String> fehlt = nummern.stream().filter(n -> !da.contains(n) && !namenDa.contains(nameJeNummer.get(n).toLowerCase(Locale.ROOT))).toList();
        return fehlt.isEmpty() ? 0 : vorlagen.profileUebernehmen(fehlt).angelegt();
    }

    /** Legt die Maschinen an, deren Arbeitsgänge gebraucht werden, und weist ihnen die Arbeitsgänge zu, die noch keine Maschine haben. */
    private int maschinenSichern(Katalog k, Set<String> gaengeNoetig) {
        int neu = 0;
        Map<String, Arbeitsgang> gaenge = new HashMap<>();
        arbeitsgaenge.findAll().forEach(a -> gaenge.put(a.getBeschreibung().toLowerCase(Locale.ROOT), a));
        for (MaschineVorlage m : k.maschinen()) {
            if (m.arbeitsgaenge().stream().noneMatch(gaengeNoetig::contains) || maschinen.findByNameIgnoreCase(m.name()).isPresent()) {
                continue;
            }
            List<Long> frei = m.arbeitsgaenge().stream().map(n -> gaenge.get(n.toLowerCase(Locale.ROOT))).filter(a -> a != null && a.getMaschine() == null).map(Arbeitsgang::getId).toList();
            maschineService.anlegen(new MaschineEingabeDto(m.name(), null, null, null, null, null, null, null, null, null, null, null, m.satz(), true, m.bemerkung(), frei), "Vorlage Metallbau");
            neu++;
        }
        return neu;
    }

    private int kategorienSichern(Katalog k, Set<String> noetig, Map<String, LeistungVorlage> nachName, Map<String, Produktkategorie> ergebnis) {
        int neu = 0;
        List<Produktkategorie> alle = produktkategorien.findAllWithParent();
        for (KategorieVorlage c : k.kategorien()) {
            boolean gebraucht = noetig.contains(c.name()) || c.unter().stream().anyMatch(noetig::contains);
            if (!gebraucht) {
                continue;
            }
            Produktkategorie oben = alle.stream().filter(x -> x.getUebergeordneteKategorie() == null && x.getBezeichnung().equalsIgnoreCase(c.name())).findFirst().orElse(null);
            if (oben == null) {
                oben = neueKategorie(c.name(), null, einheitFuer(c.name(), c.unter(), k));
                alle.add(oben);
                neu++;
            }
            ergebnis.put(c.name(), oben);
            for (String unter : c.unter()) {
                if (!noetig.contains(unter)) {
                    continue;
                }
                final Produktkategorie eltern = oben;
                Produktkategorie kind = alle.stream().filter(x -> x.getUebergeordneteKategorie() != null && x.getUebergeordneteKategorie().getId().equals(eltern.getId())
                        && x.getBezeichnung().equalsIgnoreCase(unter)).findFirst().orElse(null);
                if (kind == null) {
                    kind = neueKategorie(unter, oben, einheitFuer(unter, List.of(), k));
                    alle.add(kind);
                    neu++;
                }
                ergebnis.put(unter, kind);
            }
        }
        return neu;
    }

    /** Die Einheit der Kategorie: die der ersten Leistung darin (sonst Stück). */
    private static Verrechnungseinheit einheitFuer(String name, List<String> unter, Katalog k) {
        return k.leistungen().stream().filter(l -> l.kategorie().equals(name) || unter.contains(l.kategorie())).map(LeistungVorlage::einheit).findFirst().orElse(Verrechnungseinheit.STUECK);
    }

    private Produktkategorie neueKategorie(String name, Produktkategorie eltern, Verrechnungseinheit einheit) {
        Produktkategorie p = new Produktkategorie();
        p.setBezeichnung(name);
        p.setUebergeordneteKategorie(eltern);
        p.setVerrechnungseinheit(einheit);
        return produktkategorien.save(p);
    }

    private Map<String, Fremdleistung> fremdleistungenSichern(Katalog k, Set<String> noetig, int[] zaehler) {
        Map<String, Fremdleistung> karte = new HashMap<>();
        for (FremdVorlage f : k.fremdleistungen()) {
            if (!noetig.contains(f.bezeichnung())) {
                continue;
            }
            Lieferanten l = lieferanten.findByLieferantennameIgnoreCase(f.lieferant()).orElse(null);
            if (l == null) {
                l = new Lieferanten();
                l.setLieferantenname(f.lieferant());
                l.setIstAktiv(true);
                l.getRollen().add(f.rolle());
                l.setStandardLieferzeitTage(f.lieferzeitTage());
                l = lieferanten.save(l);
                zaehler[0]++;
            }
            final Long lieferantId = l.getId();
            Fremdleistung fl = fremdleistungen.findByLieferantIdOrderByBezeichnungAscIdAsc(lieferantId).stream().filter(x -> x.getBezeichnung().equalsIgnoreCase(f.bezeichnung())).findFirst().orElse(null);
            if (fl == null) {
                fl = new Fremdleistung();
                fl.setLieferant(l);
                fl.setBezeichnung(f.bezeichnung());
                fl.setEinheit(f.einheit());
                fl.setPreisJeEinheit(f.preis());
                fl.setMindestpreis(f.mindestpreis());
                fl.setEinmalBetrag(f.einmal());
                fl.setAnteilBetrag(f.anteil());
                fl.setAnteilChargenmenge(f.charge());
                fl.setGueltigAb(java.time.LocalDate.now().withDayOfYear(1));
                fl = fremdleistungen.save(fl);
                zaehler[1]++;
            }
            karte.put(f.bezeichnung(), fl);
        }
        return karte;
    }
}
