package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Artikelvarianten: Ein <b>Grundartikel</b> (zum Beispiel "QH Quadratrohr") trägt die gemeinsamen Angaben. Aus Maßreihen (Kantenlängen × Wandstärken)
 * legt dieser Dienst auf einen Schlag alle <b>Varianten</b> an ("QH 40x40x2"). Jede Variante ist ein ganz normaler Artikel mit eigenen Preisen und
 * übernimmt die Angaben des Grundartikels nur beim Anlegen; danach ist sie unabhängig. Das Gewicht rechnet der {@link ProfilRechner}.
 */
@Service
@RequiredArgsConstructor
public class ArtikelVariantenService {

    static final int MAX_ACHSE_A = 60;
    static final int MAX_ACHSE_B = 30;
    static final int MAX_KOMBINATIONEN = 400;
    private static final int MAX_NAME = 255;
    private static final int MAX_KURZ = 32;
    private static final int MAX_EINGABE_ZEICHEN = 2_000;
    private static final int MAX_WERT_ZEICHEN = 24;

    private final ArtikelRepository artikel;
    private final KategorieRepository kategorien;
    private final WerkstoffRepository werkstoffe;
    private final ProfilRechner rechner;

    /** Welche Maße eine Reihe für eine Form braucht: erste Reihe ({@code a}), zweite Reihe ({@code b}, leer = keine). */
    public record Vorgabe(String profilform, String form, String achseA, String hinweisA, boolean aMitZweitemMass, String achseB, String hinweisB) { }

    private static final Map<Profilform, Vorgabe> VORGABEN = new EnumMap<>(Profilform.class);

    static {
        VORGABEN.put(Profilform.RUNDROHR, new Vorgabe("RUNDROHR", "Rundrohr", "Durchmesser (mm)", "zum Beispiel 21,3 33,7 42,4", false, "Wandstärke (mm)", "zum Beispiel 2 2,6 3"));
        VORGABEN.put(Profilform.QUADRATROHR, new Vorgabe("QUADRATROHR", "Quadratrohr", "Kantenlänge (mm)", "zum Beispiel 20 25 30 40 50", false, "Wandstärke (mm)", "zum Beispiel 1,5 2 3 4"));
        VORGABEN.put(Profilform.RECHTECKROHR, new Vorgabe("RECHTECKROHR", "Rechteckrohr", "Höhe x Breite (mm)", "zum Beispiel 40x20 60x40 80x40", true, "Wandstärke (mm)", "zum Beispiel 2 3 4"));
        VORGABEN.put(Profilform.RUNDSTAB, new Vorgabe("RUNDSTAB", "Rundstab", "Durchmesser (mm)", "zum Beispiel 8 10 12 16 20", false, null, null));
        VORGABEN.put(Profilform.VIERKANTSTAB, new Vorgabe("VIERKANTSTAB", "Vierkantstab", "Kantenlänge (mm)", "zum Beispiel 8 10 12 16", false, null, null));
        VORGABEN.put(Profilform.SECHSKANTSTAB, new Vorgabe("SECHSKANTSTAB", "Sechskantstab", "Schlüsselweite (mm)", "zum Beispiel 13 17 19 24", false, null, null));
        VORGABEN.put(Profilform.FLACHSTAB, new Vorgabe("FLACHSTAB", "Flachstahl", "Breite (mm)", "zum Beispiel 20 30 40 50", false, "Dicke (mm)", "zum Beispiel 4 5 6 8 10"));
        VORGABEN.put(Profilform.BREITFLACHSTAHL, new Vorgabe("BREITFLACHSTAHL", "Breitflachstahl", "Breite (mm)", "zum Beispiel 150 200 250", false, "Dicke (mm)", "zum Beispiel 8 10 12"));
        VORGABEN.put(Profilform.WINKEL_GLEICHSCHENKLIG, new Vorgabe("WINKEL_GLEICHSCHENKLIG", "Winkel gleichschenklig", "Schenkellänge (mm)", "zum Beispiel 30 40 50", false, "Dicke (mm)", "zum Beispiel 3 4 5"));
        VORGABEN.put(Profilform.WINKEL_UNGLEICHSCHENKLIG, new Vorgabe("WINKEL_UNGLEICHSCHENKLIG", "Winkel ungleichschenklig", "Längerer x kürzerer Schenkel (mm)", "zum Beispiel 60x40 80x40", true, "Dicke (mm)", "zum Beispiel 5 6 8"));
        VORGABEN.put(Profilform.BLECH, new Vorgabe("BLECH", "Blech", null, null, false, "Dicke (mm)", "zum Beispiel 1 1,5 2 3 4 5"));
        VORGABEN.put(Profilform.IPE_PROFIL, new Vorgabe("IPE_PROFIL", "IPE-Träger", "Nenngröße (Höhe in mm)", "zum Beispiel 80 100 120 140 160 200", false, null, null));
        VORGABEN.put(Profilform.HEA_PROFIL, new Vorgabe("HEA_PROFIL", "HEA-Träger", "Nenngröße (Höhe in mm)", "zum Beispiel 100 120 140 160 200", false, null, null));
        VORGABEN.put(Profilform.HEB_PROFIL, new Vorgabe("HEB_PROFIL", "HEB-Träger", "Nenngröße (Höhe in mm)", "zum Beispiel 100 120 140 160 200", false, null, null));
        VORGABEN.put(Profilform.U_PROFIL, new Vorgabe("U_PROFIL", "U-Profil", "Nenngröße (Höhe in mm)", "zum Beispiel 50 65 80 100 120", false, null, null));
    }

    public enum Status { NEU, SCHON_DA, NICHT_MOEGLICH }

    /** Eine mögliche Variante in der Vorschau. */
    public record Kombination(String schluessel, String name, String artikelnummer, Status status, String grund, BigDecimal kgProMeter, BigDecimal kgProQm) { }

    /** Die Reihen, aus denen die Varianten entstehen (Werte als getippt, mit Komma oder Punkt). */
    public record Reihen(List<String> a, List<String> b, List<String> ausgeschlossen) { }

    /** Eingabe für einen neuen Grundartikel. */
    public record GrundartikelEingabe(String kurzbezeichnung, String produktname, Profilform profilform, Long werkstoffId, Long kategorieId,
            Verrechnungseinheit verrechnungseinheit, BigDecimal verkaufsaufschlagProzent, Integer standardlaenge) { }

    /** Eine Variante in der Liste am Grundartikel. */
    public record VarianteZeile(Long id, String produktname, String artikelnummer, String abmessung, BigDecimal kgProMeter, BigDecimal kgProQm, BigDecimal guenstigsterPreis) { }

    /** Die Eingabevorgabe für das Raster dieses Grundartikels. */
    @Transactional(readOnly = true)
    public Vorgabe rasterVorgabe(Long grundartikelId) {
        return vorgabe(grundartikel(grundartikelId).getProfilform());
    }

    public Vorgabe vorgabe(Profilform form) {
        return form == null ? null : VORGABEN.get(form);
    }

    // ---------------------------------------------------------------- Grundartikel anlegen

    @Transactional
    public Artikel legeGrundartikelAn(GrundartikelEingabe e) {
        String kurz = pruefeKurzbezeichnung(e.kurzbezeichnung());
        String name = text(e.produktname(), MAX_NAME, "Der Name");
        if (name == null) {
            throw ungueltig("Bitte einen Namen eingeben, zum Beispiel Quadratrohr.");
        }
        if (e.profilform() == null || !VORGABEN.containsKey(e.profilform())) {
            throw ungueltig("Für diese Form gibt es noch keine Varianten. Bitte eine andere Form wählen.");
        }
        if (artikel.existsByGrundartikelTrueAndKurzbezeichnungIgnoreCase(kurz)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Es gibt schon einen Grundartikel mit der Kurzbezeichnung " + kurz + ".");
        }
        BigDecimal aufschlag = e.verkaufsaufschlagProzent();
        if (aufschlag != null && (aufschlag.signum() < 0 || aufschlag.compareTo(new BigDecimal("999.99")) > 0)) {
            throw ungueltig("Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.");
        }
        if (e.standardlaenge() != null && (e.standardlaenge() < 1 || e.standardlaenge() > 30_000)) {
            throw ungueltig("Die Standardlänge muss zwischen 1 und 30000 mm liegen.");
        }
        ArtikelWerkstoffe g = new ArtikelWerkstoffe();
        g.setGrundartikel(true);
        g.setKurzbezeichnung(kurz);
        g.setProduktname(name);
        g.setProfilform(e.profilform());
        g.setVerrechnungseinheit(e.verrechnungseinheit());
        g.setVerkaufsaufschlagProzent(aufschlag == null ? null : aufschlag.setScale(2, java.math.RoundingMode.HALF_UP));
        g.setStandardlaenge(e.standardlaenge());
        if (e.werkstoffId() != null) {
            g.setWerkstoff(werkstoffe.findById(e.werkstoffId()).orElseThrow(() -> ungueltig("Den gewählten Werkstoff gibt es nicht.")));
        }
        if (e.kategorieId() != null) {
            g.setKategorie(kategorie(e.kategorieId()));
        }
        g.setSuchtext(ArtikelSuchtext.aufbauen(g));
        return artikel.save(g);
    }

    /** Die Kurzbezeichnung eines Grundartikels: Pflicht, höchstens 32 Zeichen, nur Buchstaben, Ziffern und Bindestriche (sie steckt in Namen und Artikelnummern). */
    public static String pruefeKurzbezeichnung(String roh) {
        String kurz = text(roh, MAX_KURZ, "Die Kurzbezeichnung");
        if (kurz == null) {
            throw ungueltig("Bitte eine Kurzbezeichnung eingeben, zum Beispiel QH.");
        }
        if (!kurz.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
            throw ungueltig("Die Kurzbezeichnung darf nur Buchstaben, Ziffern und Bindestriche enthalten.");
        }
        return kurz;
    }

    // ---------------------------------------------------------------- Vorschau und Anlegen

    @Transactional(readOnly = true)
    public List<Kombination> vorschau(Long grundartikelId, Reihen reihen) {
        ArtikelWerkstoffe g = grundartikel(grundartikelId);
        return plane(g, reihen).stream().map(Plan::kombination).toList();
    }

    /** Legt alle neuen, nicht ausgeschlossenen Varianten an und gibt die Vorschau-Zeilen mit dem Ergebnis zurück. */
    @Transactional
    public List<Kombination> legeVariantenAn(Long grundartikelId, Reihen reihen) {
        ArtikelWerkstoffe g = grundartikel(grundartikelId);
        Set<String> aus = reihen.ausgeschlossen() == null ? Set.of() : new LinkedHashSet<>(reihen.ausgeschlossen());
        List<Plan> plaene = plane(g, reihen);
        List<ArtikelWerkstoffe> neu = new ArrayList<>();
        for (Plan p : plaene) {
            if (p.kombination().status() == Status.NEU && !aus.contains(p.kombination().schluessel())) {
                neu.add(baueVariante(g, p));
            }
        }
        if (neu.isEmpty()) {
            throw ungueltig("Es gibt nichts Neues anzulegen: Alle Varianten sind schon da, nicht möglich oder abgewählt.");
        }
        artikel.saveAll(neu);
        return plaene.stream().map(Plan::kombination).toList();
    }

    @Transactional(readOnly = true)
    public List<VarianteZeile> varianten(Long grundartikelId) {
        ArtikelWerkstoffe g = grundartikel(grundartikelId);
        return artikel.findByGrundartikelRef(g).stream()
                .sorted(Comparator.comparing((Artikel a) -> masszahl(a, 0)).thenComparing(a -> masszahl(a, 1)).thenComparing(a -> a.getProduktname() == null ? "" : a.getProduktname()))
                .map(a -> {
                    ArtikelWerkstoffe w = a instanceof ArtikelWerkstoffe x ? x : null;
                    return new VarianteZeile(a.getId(), a.getProduktname(), a.getArtikelnummer(), w == null ? null : w.getAbmessungText(), w == null ? null : w.getMasse(),
                            w == null ? null : w.getMassePerQm(), a.getGuenstigsterPreis().map(p -> p.getPreis()).orElse(null));
                }).toList();
    }

    // ---------------------------------------------------------------- Planung

    private record Plan(Kombination kombination, BigDecimal[] masse, ProfilRechner.Ergebnis ergebnis, List<String> namensteile) { }

    private List<Plan> plane(ArtikelWerkstoffe g, Reihen reihen) {
        Profilform form = g.getProfilform();
        Vorgabe v = vorgabe(form);
        if (v == null) {
            throw ungueltig("Für diese Form gibt es noch keine Varianten.");
        }
        if (g.getKurzbezeichnung() == null || g.getKurzbezeichnung().isBlank()) {
            throw ungueltig("Der Grundartikel braucht eine Kurzbezeichnung.");
        }
        List<BigDecimal[]> as = v.achseA() == null ? java.util.Collections.singletonList(new BigDecimal[0]) : lies(reihen.a(), v.aMitZweitemMass() ? 2 : 1, v.achseA(), MAX_ACHSE_A);
        List<BigDecimal[]> bs = v.achseB() == null ? java.util.Collections.singletonList(new BigDecimal[0]) : lies(reihen.b(), 1, v.achseB(), MAX_ACHSE_B);
        if ((long) as.size() * bs.size() > MAX_KOMBINATIONEN) {
            throw ungueltig("Das sind mehr als " + MAX_KOMBINATIONEN + " Varianten auf einmal. Bitte in kleineren Schritten anlegen.");
        }
        BigDecimal dichte = g.getWerkstoff() != null ? g.getWerkstoff().getDichte() : null;
        List<Plan> plaene = new ArrayList<>();
        Set<String> vergeben = new LinkedHashSet<>();
        // Vorhandene Artikelnummern in einem Zug holen statt je Kombination einzeln nachzufragen
        Set<String> vorhanden = new java.util.HashSet<>();
        List<String> alleNummern = new ArrayList<>();
        for (BigDecimal[] a : as) {
            for (BigDecimal[] b : bs) {
                alleNummern.add(g.getKurzbezeichnung() + "-" + String.join("x", namensteile(g.getProfilform(), a, b)));
            }
        }
        artikel.findByArtikelnummerIn(alleNummern).forEach(x -> vorhanden.add(x.getArtikelnummer()));
        for (BigDecimal[] a : as) {
            for (BigDecimal[] b : bs) {
                BigDecimal[] masse = zuMasse(form, a, b);
                List<String> teile = namensteile(form, a, b);
                String suffix = String.join("x", teile);
                String name = g.getKurzbezeichnung() + " " + suffix;
                String nummer = g.getKurzbezeichnung() + "-" + suffix;
                String schluessel = schluessel(a, b);
                if (nummer.length() > 64 || name.length() > MAX_NAME) {
                    plaene.add(plan(schluessel, name, nummer, Status.NICHT_MOEGLICH, "Der Name wird zu lang.", masse, null, teile));
                    continue;
                }
                if (!vergeben.add(nummer)) {
                    continue;
                }
                if (vorhanden.contains(nummer)) {
                    plaene.add(plan(schluessel, name, nummer, Status.SCHON_DA, "Gibt es schon.", masse, null, teile));
                    continue;
                }
                try {
                    ProfilRechner.Ergebnis erg = rechner.berechne(form, new ProfilRechner.Masse(masse[0], masse[1], masse[2], masse[3]), dichte);
                    plaene.add(plan(schluessel, name, nummer, Status.NEU, null, masse, erg, teile));
                } catch (ResponseStatusException ex) {
                    plaene.add(plan(schluessel, name, nummer, Status.NICHT_MOEGLICH, ex.getReason(), masse, null, teile));
                }
            }
        }
        return plaene;
    }

    private static Plan plan(String schluessel, String name, String nummer, Status status, String grund, BigDecimal[] masse, ProfilRechner.Ergebnis erg, List<String> teile) {
        return new Plan(new Kombination(schluessel, name, nummer, status, grund, erg == null ? null : erg.kgProMeter(), erg == null ? null : erg.kgProQm()), masse, erg, teile);
    }

    private ArtikelWerkstoffe baueVariante(ArtikelWerkstoffe g, Plan p) {
        ArtikelWerkstoffe v = new ArtikelWerkstoffe();
        v.setGrundartikelRef(g);
        v.setProduktname(p.kombination().name());
        v.setArtikelnummer(p.kombination().artikelnummer());
        v.setProduktlinie(g.getProduktlinie());
        v.setProdukttext(g.getProdukttext());
        v.setVerpackungseinheit(g.getVerpackungseinheit());
        v.setPreiseinheit(g.getPreiseinheit());
        v.setVerrechnungseinheit(g.getVerrechnungseinheit());
        v.setKategorie(g.getKategorie());
        v.setWerkstoff(g.getWerkstoff());
        v.setProfilform(g.getProfilform());
        v.setHerstellverfahren(g.getHerstellverfahren());
        v.setFertigungszustand(g.getFertigungszustand());
        v.setMassnorm(g.getMassnorm());
        v.setWerkstoffnorm(g.getWerkstoffnorm());
        v.setVerzinkungsgeeignet(g.getVerzinkungsgeeignet());
        v.setPulverbeschichtungsgeeignet(g.getPulverbeschichtungsgeeignet());
        v.setVerkaufsaufschlagProzent(g.getVerkaufsaufschlagProzent());
        v.setKurzbeschreibung(g.getKurzbeschreibung());
        v.setBeschreibung(g.getBeschreibung());
        v.setStandardlaenge(g.getStandardlaenge());
        v.setDurchmesser(p.masse()[0]);
        v.setHoehe(p.masse()[1]);
        v.setBreite(p.masse()[2]);
        v.setWandstaerke(p.masse()[3]);
        ProfilRechner.Ergebnis e = p.ergebnis();
        if (e != null) {
            v.setMasse(e.kgProMeter());
            v.setMassePerQm(e.kgProQm());
            v.setQuerschnittsflaeche(e.querschnittCm2());
            v.setMantelflaeche(e.mantelflaecheQm());
        }
        v.setSuchtext(ArtikelSuchtext.aufbauen(v));
        return v;
    }

    // ---------------------------------------------------------------- Zuordnung der Maße je Form

    /** Maße als Feld {durchmesser, hoehe, breite, wandstaerke}. */
    private static BigDecimal[] zuMasse(Profilform f, BigDecimal[] a, BigDecimal[] b) {
        BigDecimal[] m = new BigDecimal[4];
        BigDecimal dicke = b.length > 0 ? b[0] : null;
        switch (f) {
            case RUNDROHR -> { m[0] = a[0]; m[3] = dicke; }
            case QUADRATROHR, WINKEL_GLEICHSCHENKLIG -> { m[1] = a[0]; m[2] = a[0]; m[3] = dicke; }
            case RECHTECKROHR, WINKEL_UNGLEICHSCHENKLIG -> { m[1] = a[0]; m[2] = a[1]; m[3] = dicke; }
            case RUNDSTAB -> m[0] = a[0];
            case VIERKANTSTAB -> { m[1] = a[0]; m[2] = a[0]; }
            case SECHSKANTSTAB, IPE_PROFIL, HEA_PROFIL, HEB_PROFIL, U_PROFIL -> m[1] = a[0];
            case FLACHSTAB, BREITFLACHSTAHL -> { m[2] = a[0]; m[3] = dicke; }
            case BLECH -> m[3] = dicke;
            default -> throw ungueltig("Für diese Form gibt es noch keine Varianten.");
        }
        return m;
    }

    /** Die Teile des Namens, zum Beispiel {40, 40, 2} für "40x40x2". */
    private static List<String> namensteile(Profilform f, BigDecimal[] a, BigDecimal[] b) {
        List<String> t = new ArrayList<>();
        switch (f) {
            case QUADRATROHR, WINKEL_GLEICHSCHENKLIG -> { t.add(zahl(a[0])); t.add(zahl(a[0])); }
            case VIERKANTSTAB -> { t.add(zahl(a[0])); t.add(zahl(a[0])); }
            default -> { for (BigDecimal x : a) { t.add(zahl(x)); } }
        }
        for (BigDecimal x : b) {
            t.add(zahl(x));
        }
        return t;
    }

    private static String schluessel(BigDecimal[] a, BigDecimal[] b) {
        return String.join("x", java.util.Arrays.stream(a).map(ArtikelVariantenService::zahl).toList()) + "|"
                + String.join("x", java.util.Arrays.stream(b).map(ArtikelVariantenService::zahl).toList());
    }

    private static double masszahl(Artikel a, int stelle) {
        if (!(a instanceof ArtikelWerkstoffe w)) {
            return 0;
        }
        BigDecimal[] werte = { w.getDurchmesser() != null ? w.getDurchmesser() : w.getHoehe() != null ? w.getHoehe() : w.getBreite(), w.getWandstaerke() };
        return werte[stelle] == null ? 0 : werte[stelle].doubleValue();
    }

    // ---------------------------------------------------------------- Eingabe lesen

    /** Liest eine Reihe wie "20 25, 30; 40" oder bei zwei Maßen "60x40 80x40". Doppelte fallen weg. */
    private static List<BigDecimal[]> lies(List<String> roh, int massAnzahl, String bezeichnung, int max) {
        String was = bezeichnung.replace(" (mm)", "");
        List<String> eintraege = new ArrayList<>();
        if (roh != null) {
            for (String s : roh) {
                if (s == null) {
                    continue;
                }
                // Vor dem Zerlegen kappen: Eine riesige Eingabe soll nicht erst komplett verarbeitet werden
                if (s.length() > MAX_EINGABE_ZEICHEN) {
                    throw ungueltig("Die Eingabe bei „" + was + "“ ist zu lang.");
                }
                for (String teil : s.split("[\\s;]+")) {
                    // "1,5, 2": Das Komma am Ende trennt nur, die Dezimalstelle bleibt
                    String wert = teil.strip();
                    while (wert.endsWith(",")) {
                        wert = wert.substring(0, wert.length() - 1);
                    }
                    if (wert.length() > MAX_WERT_ZEICHEN) {
                        throw ungueltig("„" + wert.substring(0, 12) + "…“ ist kein gültiges Maß für „" + was + "“.");
                    }
                    if (!wert.isBlank()) {
                        eintraege.add(wert);
                    }
                    if (eintraege.size() > 2 * max) {
                        throw ungueltig("Zu viele Werte bei „" + was + "“ (höchstens " + max + ").");
                    }
                }
            }
        }
        if (eintraege.isEmpty()) {
            throw ungueltig("Bitte mindestens einen Wert für „" + was + "“ eintragen.");
        }
        Set<String> gesehen = new LinkedHashSet<>();
        List<BigDecimal[]> ergebnis = new ArrayList<>();
        for (String e : eintraege) {
            String[] teile = e.split("[x×*]", -1);
            if (teile.length != massAnzahl) {
                throw ungueltig(massAnzahl == 2 ? "„" + e + "“ ist kein gültiges Maß. Bitte so schreiben: 60x40."
                        : "„" + e + "“ ist kein gültiges Maß für „" + was + "“.");
            }
            BigDecimal[] werte = new BigDecimal[massAnzahl];
            for (int i = 0; i < massAnzahl; i++) {
                werte[i] = zahlLesen(teile[i], e, was);
            }
            if (gesehen.add(java.util.Arrays.toString(werte))) {
                ergebnis.add(werte);
            }
        }
        if (ergebnis.size() > max) {
            throw ungueltig("Zu viele Werte bei „" + was + "“ (höchstens " + max + ").");
        }
        return ergebnis;
    }

    private static BigDecimal zahlLesen(String s, String ganz, String was) {
        try {
            BigDecimal d = new BigDecimal(s.strip().replace(',', '.')).stripTrailingZeros();
            if (d.signum() <= 0 || d.compareTo(new BigDecimal("100000")) > 0 || d.scale() > 2) {
                throw new NumberFormatException();
            }
            return d.scale() < 0 ? d.setScale(0) : d;
        } catch (NumberFormatException ex) {
            throw ungueltig("„" + ganz + "“ ist kein gültiges Maß für „" + was + "“ (erlaubt: Zahl über 0 mit höchstens zwei Nachkommastellen).");
        }
    }

    /** 40 wird "40", 2.5 wird "2,5". */
    static String zahl(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ---------------------------------------------------------------- Hilfen

    private ArtikelWerkstoffe grundartikel(Long id) {
        Artikel a = id == null ? null : artikel.findById(id).orElse(null);
        if (a == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Artikel gibt es nicht.");
        }
        if (!a.isGrundartikel() || !(a instanceof ArtikelWerkstoffe w)) {
            throw ungueltig("Dieser Artikel ist kein Grundartikel. Varianten gibt es nur zu einem Grundartikel.");
        }
        return w;
    }

    private Kategorie kategorie(Long id) {
        if (id < 0 || id > Integer.MAX_VALUE) {
            throw ungueltig("Die gewählte Kategorie gibt es nicht.");
        }
        return kategorien.findById(id.intValue()).orElseThrow(() -> ungueltig("Die gewählte Kategorie gibt es nicht."));
    }

    private static String text(String s, int max, String was) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        if (t.length() > max) {
            throw ungueltig(was + " darf höchstens " + max + " Zeichen lang sein.");
        }
        return t;
    }

    private static ResponseStatusException ungueltig(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
