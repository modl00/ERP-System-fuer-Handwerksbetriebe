package org.example.kalkulationsprogramm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelStammdatenRequest;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Mitgelieferte Vorlagen für Metallbau zum Übernehmen: <b>Arbeitsgänge mit Formeln</b> und <b>Stahlprofile und Bleche</b> als Artikel. Nichts davon wird von selbst angelegt:
 * Erst die Vorschau (neu oder schon vorhanden), dann die Auswahl, dann erst wird geschrieben. Ein zweiter Lauf legt nichts doppelt an (Abgleich über Namen und Artikelnummer).
 * <p>Arbeitsgänge bekommen Namen, Abteilung und Formel samt Richtwerten, <b>aber keinen Stundensatz</b>. Artikel bekommen Maße, Gewicht und Oberfläche (aus den Maßen oder der
 * Profiltabelle berechnet), <b>aber keinen Preis</b> und keinen Lieferanten.</p>
 */
@Service
@RequiredArgsConstructor
public class MetallbauVorlagenService {

    static final String ARBEITSGAENGE = "vorlagen/metallbau/arbeitsgaenge.json";
    static final String PROFILE = "vorlagen/metallbau/profile-masse.csv";
    static final String KAT_PROFILE = "Stahlprofile";
    static final String KAT_BLECHE = "Bleche";
    static final String WERKSTOFF = "S235JR";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DecimalFormat ZAHL = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.GERMAN));

    private final ArbeitsgangRepository arbeitsgaenge;
    private final AbteilungRepository abteilungen;
    private final ArtikelRepository artikel;
    private final ArtikelService artikelService;
    private final KategorieRepository kategorien;
    private final WerkstoffRepository werkstoffe;
    private final ProfilTabelle profilTabelle;

    // ───────────────────────────── Arbeitsgänge ─────────────────────────────

    public record FeldVorlage(String name, String bezeichnung, String einheit, String standard) { }

    public record ArbeitsgangVorlage(String name, String abteilung, String einheit, String erklaerung, List<FeldVorlage> felder, String bearbeitung, String ruest) { }

    public record ArbeitsgangZeile(String name, String abteilung, String einheit, String erklaerung, boolean vorhanden, int felder, BigDecimal beispielMinuten) { }

    public record AbteilungOption(Long id, String name) { }

    public record ArbeitsgangVorschau(String hinweis, List<ArbeitsgangZeile> zeilen, List<AbteilungOption> abteilungen) { }

    public record Ergebnis(int angelegt, int uebersprungen) { }

    private volatile String hinweisText;
    private volatile List<ArbeitsgangVorlage> arbeitsgangKatalog;

    /** Der gelesene und geprüfte Katalog. Eine kaputte Formel im Katalog ist ein Programmfehler und fällt sofort auf (wirft). */
    List<ArbeitsgangVorlage> arbeitsgangKatalog() {
        List<ArbeitsgangVorlage> k = arbeitsgangKatalog;
        if (k == null) {
            synchronized (this) {
                if (arbeitsgangKatalog == null) {
                    arbeitsgangKatalog = leseArbeitsgaenge();
                }
                k = arbeitsgangKatalog;
            }
        }
        return k;
    }

    private List<ArbeitsgangVorlage> leseArbeitsgaenge() {
        try (InputStream in = new ClassPathResource(ARBEITSGAENGE).getInputStream()) {
            JsonNode wurzel = JSON.readTree(in);
            hinweisText = wurzel.path("hinweis").asText("");
            List<ArbeitsgangVorlage> liste = new ArrayList<>();
            for (JsonNode a : wurzel.path("arbeitsgaenge")) {
                List<FeldVorlage> felder = new ArrayList<>();
                for (JsonNode f : a.path("felder")) {
                    felder.add(new FeldVorlage(f.path("name").asText(), f.path("bezeichnung").asText(), f.path("einheit").asText(""), f.path("standard").asText("")));
                }
                ArbeitsgangVorlage v = new ArbeitsgangVorlage(a.path("name").asText(), a.path("abteilung").asText(""), a.path("einheit").asText(""), a.path("erklaerung").asText(""), felder,
                        a.path("bearbeitung").asText(), a.hasNonNull("ruest") ? a.path("ruest").asText() : null);
                formelDerVorlage(v); // prüft die Formel
                liste.add(v);
            }
            return List.copyOf(liste);
        } catch (IOException e) {
            throw new IllegalStateException("Der Katalog der Arbeitsgänge ist nicht lesbar.", e);
        }
    }

    static ArbeitsgangFormeln.Formel formelDerVorlage(ArbeitsgangVorlage v) {
        List<ArbeitsgangFormeln.Feld> felder = new ArrayList<>();
        for (FeldVorlage f : v.felder()) {
            felder.add(new ArbeitsgangFormeln.Feld(f.name(), f.bezeichnung(), f.einheit(), zahl(f.standard())));
        }
        return ArbeitsgangFormeln.pruefe(new ArbeitsgangFormeln.Formel(felder, v.bearbeitung(), v.ruest()));
    }

    private static BigDecimal zahl(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s.strip().replace(',', '.'));
    }

    @Transactional(readOnly = true)
    public ArbeitsgangVorschau arbeitsgangVorschau() {
        List<ArbeitsgangVorlage> katalog = arbeitsgangKatalog();
        Set<String> vorhanden = new HashSet<>();
        for (Arbeitsgang a : arbeitsgaenge.findAll()) {
            vorhanden.add(a.getBeschreibung().toLowerCase(Locale.ROOT));
        }
        List<ArbeitsgangZeile> zeilen = new ArrayList<>();
        for (ArbeitsgangVorlage v : katalog) {
            var formel = formelDerVorlage(v);
            BigDecimal minuten = ArbeitsgangFormeln.berechne(formel, Map.of()).bearbeitungMinuten();
            zeilen.add(new ArbeitsgangZeile(v.name(), v.abteilung(), v.einheit(), v.erklaerung(), vorhanden.contains(v.name().toLowerCase(Locale.ROOT)), v.felder().size(), minuten));
        }
        List<AbteilungOption> optionen = abteilungen.findAll().stream().map(a -> new AbteilungOption(a.getId(), a.getName())).sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
        return new ArbeitsgangVorschau(hinweisText, zeilen, optionen);
    }

    /**
     * Legt die gewählten Arbeitsgänge an. Die Abteilung kommt aus dem Namen im Katalog (Werkstatt, Montage, Büro), wenn es sie gibt, sonst aus {@code ersatzAbteilungId}.
     * Vorhandene Namen werden übersprungen; ohne Abteilung scheitert der ganze Lauf, bevor etwas geschrieben wird.
     */
    @Transactional
    public Ergebnis arbeitsgaengeUebernehmen(List<String> namen, Long ersatzAbteilungId) {
        if (namen == null || namen.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Arbeitsgang auswählen.");
        }
        Map<String, ArbeitsgangVorlage> nachName = new LinkedHashMap<>();
        arbeitsgangKatalog().forEach(v -> nachName.put(v.name().toLowerCase(Locale.ROOT), v));
        Abteilung ersatz = ersatzAbteilungId == null ? null : abteilungen.findById(ersatzAbteilungId).orElseThrow(() -> new IllegalArgumentException("Die gewählte Abteilung gibt es nicht."));
        Set<String> vorhanden = new HashSet<>();
        for (Arbeitsgang a : arbeitsgaenge.findAll()) {
            vorhanden.add(a.getBeschreibung().toLowerCase(Locale.ROOT));
        }
        List<Arbeitsgang> neu = new ArrayList<>();
        int uebersprungen = 0;
        for (String name : namen.stream().filter(n -> n != null).map(n -> n.toLowerCase(Locale.ROOT)).distinct().toList()) {
            ArbeitsgangVorlage v = nachName.get(name);
            if (v == null) {
                throw new IllegalArgumentException("Den Arbeitsgang „" + name + "“ gibt es im Katalog nicht.");
            }
            if (vorhanden.contains(name)) {
                uebersprungen++;
                continue;
            }
            Abteilung abt = abteilungen.findByName(v.abteilung()).orElse(ersatz);
            if (abt == null) {
                throw new IllegalArgumentException("Für „" + v.name() + "“ gibt es keine Abteilung „" + v.abteilung() + "“. Bitte eine Abteilung wählen.");
            }
            var formel = formelDerVorlage(v);
            Arbeitsgang g = new Arbeitsgang();
            g.setBeschreibung(v.name());
            g.setAbteilung(abt);
            g.setFormelFelderJson(ArbeitsgangFormeln.felderAlsJson(formel.felder()));
            g.setFormelBearbeitung(formel.bearbeitung());
            g.setFormelRuest(formel.ruest());
            neu.add(g);
        }
        arbeitsgaenge.saveAll(neu);
        return new Ergebnis(neu.size(), uebersprungen);
    }

    // ───────────────────────────── Stahlprofile und Bleche ─────────────────────────────

    /** Ein Artikel aus der Vorlage mit allem, was zum Anlegen nötig ist. {@code kg}/{@code flaeche} je Meter, beim Blech je m². */
    public record ProfilVorlage(String nummer, String name, Profilform form, boolean blech, BigDecimal durchmesser, BigDecimal hoehe, BigDecimal breite, BigDecimal wand,
            BigDecimal kg, BigDecimal flaeche, String herkunft) { }

    public record ProfilZeile(String nummer, String name, String form, boolean vorhanden, BigDecimal kg, BigDecimal flaeche, String einheit, String herkunft) { }

    public record ProfilVorschau(List<ProfilZeile> zeilen, boolean werkstoffGefunden) { }

    private volatile List<ProfilVorlage> profilKatalog;

    List<ProfilVorlage> profilKatalog() {
        List<ProfilVorlage> k = profilKatalog;
        if (k == null) {
            synchronized (this) {
                if (profilKatalog == null) {
                    profilKatalog = List.copyOf(leseProfile());
                }
                k = profilKatalog;
            }
        }
        return k;
    }

    private List<ProfilVorlage> leseProfile() {
        List<ProfilVorlage> liste = new ArrayList<>();
        try (InputStream in = new ClassPathResource(PROFILE).getInputStream(); BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String zeile;
            while ((zeile = r.readLine()) != null) {
                if (zeile.isBlank() || zeile.startsWith("#")) {
                    continue;
                }
                liste.add(rechne(zeile.split(";")));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Die Maßreihen der Stahlprofile sind nicht lesbar.", e);
        }
        for (Profilform form : profilTabelle.formen()) {
            for (ProfilTabelle.Eintrag e : profilTabelle.fuer(form)) {
                String kurz = e.bezeichnung().replace(' ', '-');
                liste.add(new ProfilVorlage("VL-" + kurz, e.bezeichnung(), form, false, null, e.hoeheMm(), e.breiteMm(), null, e.kgJeMeter(), e.m2JeMeter(), "TABELLE"));
            }
        }
        return liste;
    }

    private ProfilVorlage rechne(String[] t) {
        Profilform form = Profilform.valueOf(t[0]);
        BigDecimal[] m = new BigDecimal[t.length - 1];
        for (int i = 1; i < t.length; i++) {
            m[i - 1] = new BigDecimal(t[i]);
        }
        BigDecimal d = null;
        BigDecimal h = null;
        BigDecimal b = null;
        BigDecimal w = null;
        String name;
        String code;
        switch (form) {
            case RUNDROHR -> { d = m[0]; w = m[1]; name = "Rundrohr " + f(d) + " x " + f(w); code = "RR-" + p(d) + "x" + p(w); }
            case QUADRATROHR -> { h = m[0]; b = m[0]; w = m[1]; name = "Quadratrohr " + f(h) + " x " + f(h) + " x " + f(w); code = "QR-" + p(h) + "x" + p(w); }
            case RECHTECKROHR -> { h = m[0]; b = m[1]; w = m[2]; name = "Rechteckrohr " + f(h) + " x " + f(b) + " x " + f(w); code = "RHR-" + p(h) + "x" + p(b) + "x" + p(w); }
            case RUNDSTAB -> { d = m[0]; name = "Rundstahl " + f(d); code = "RS-" + p(d); }
            case VIERKANTSTAB -> { h = m[0]; b = m[0]; name = "Vierkantstahl " + f(h) + " x " + f(h); code = "VS-" + p(h); }
            case FLACHSTAB -> { b = m[0]; h = m[1]; name = "Flachstahl " + f(b) + " x " + f(h); code = "FS-" + p(b) + "x" + p(h); }
            case WINKEL_GLEICHSCHENKLIG -> { h = m[0]; b = m[0]; w = m[1]; name = "Winkelstahl " + f(h) + " x " + f(h) + " x " + f(w); code = "WG-" + p(h) + "x" + p(w); }
            case WINKEL_UNGLEICHSCHENKLIG -> { h = m[0]; b = m[1]; w = m[2]; name = "Winkelstahl " + f(h) + " x " + f(b) + " x " + f(w); code = "WU-" + p(h) + "x" + p(b) + "x" + p(w); }
            case BLECH -> { w = m[0]; name = "Blech " + f(w) + " mm"; code = "BL-" + p(w); }
            default -> throw new IllegalStateException("Die Form " + form + " ist in den Maßreihen nicht vorgesehen.");
        }
        BigDecimal dichte = ProfilRechner.STAHL;
        ProfilRechner.Ergebnis e = ProfilRechner.rechne(form, d, h, b, w, dichte);
        boolean blech = form == Profilform.BLECH;
        return new ProfilVorlage("VL-" + code, name, form, blech, d, h, b, w, blech ? e.kgJeQm() : e.kgJeMeter(), blech ? e.m2JeQm() : e.m2JeMeter(), "BERECHNET");
    }

    private static String f(BigDecimal v) {
        return ZAHL.format(v);
    }

    private static String p(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    @Transactional(readOnly = true)
    public ProfilVorschau profilVorschau() {
        Set<String> nummern = artikel.alleArtikelnummern();
        Set<String> namen = artikel.alleProduktnamenKlein();
        List<ProfilZeile> zeilen = new ArrayList<>();
        for (ProfilVorlage v : profilKatalog()) {
            boolean da = nummern.contains(v.nummer()) || namen.contains(v.name().toLowerCase(Locale.ROOT));
            zeilen.add(new ProfilZeile(v.nummer(), v.name(), v.form().getAnzeigename(), da, v.kg(), v.flaeche(), v.blech() ? "je m²" : "je Meter", v.herkunft()));
        }
        return new ProfilVorschau(zeilen, werkstoffe.findByNameIgnoreCase(WERKSTOFF).isPresent());
    }

    /** Legt die gewählten Profile als Artikel an (ohne Preis und Lieferant); Vorhandene werden übersprungen. Läuft über die geprüfte Artikel-Anlage der Oberfläche. */
    @Transactional
    public Ergebnis profileUebernehmen(List<String> nummernGewaehlt) {
        if (nummernGewaehlt == null || nummernGewaehlt.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Artikel auswählen.");
        }
        Map<String, ProfilVorlage> nachNummer = new LinkedHashMap<>();
        profilKatalog().forEach(v -> nachNummer.put(v.nummer(), v));
        Set<String> nummern = artikel.alleArtikelnummern();
        Set<String> namen = artikel.alleProduktnamenKlein();
        Werkstoff werkstoff = werkstoffe.findByNameIgnoreCase(WERKSTOFF).orElse(null);
        Long katProfile = null;
        Long katBleche = null;
        int angelegt = 0;
        int uebersprungen = 0;
        for (String nummer : nummernGewaehlt.stream().filter(n -> n != null).distinct().toList()) {
            ProfilVorlage v = nachNummer.get(nummer);
            if (v == null) {
                throw new IllegalArgumentException("Den Artikel „" + nummer + "“ gibt es in der Vorlage nicht.");
            }
            if (nummern.contains(v.nummer()) || namen.contains(v.name().toLowerCase(Locale.ROOT))) {
                uebersprungen++;
                continue;
            }
            Long kategorieId;
            if (v.blech()) {
                katBleche = katBleche != null ? katBleche : kategorie(KAT_BLECHE);
                kategorieId = katBleche;
            } else {
                katProfile = katProfile != null ? katProfile : kategorie(KAT_PROFILE);
                kategorieId = katProfile;
            }
            ArtikelCreateDto dto = new ArtikelCreateDto();
            dto.setProduktname(v.name());
            dto.setVerrechnungseinheit(v.blech() ? Verrechnungseinheit.QUADRATMETER : Verrechnungseinheit.LAUFENDE_METER);
            dto.setKategorieId(kategorieId);
            dto.setWerkstoffId(werkstoff == null ? null : werkstoff.getId());
            Artikel angelegtArtikel = artikelService.erstelleArtikel(dto);

            ArtikelStammdatenRequest r = new ArtikelStammdatenRequest();
            r.setProduktname(v.name());
            r.setArtikelnummer(v.nummer());
            r.setVerrechnungseinheit(dto.getVerrechnungseinheit());
            r.setKategorieId(kategorieId);
            r.setWerkstoffId(dto.getWerkstoffId());
            r.setProfilform(v.form());
            r.setDurchmesser(v.durchmesser());
            r.setHoehe(v.hoehe());
            r.setBreite(v.breite());
            r.setWandstaerke(v.wand());
            if (v.blech()) {
                r.setKgProQm(v.kg());
                r.setFlaecheJeQm(v.flaeche());
            } else {
                r.setKgProMeter(v.kg());
                r.setMantelflaeche(v.flaeche());
                r.setStandardlaenge(6000);
            }
            r.setGewichtHerkunft(v.herkunft());
            if ("BERECHNET".equals(v.herkunft())) {
                r.setGewichtDichte(werkstoff != null && werkstoff.getDichte() != null && werkstoff.getDichte().signum() > 0 ? werkstoff.getDichte() : ProfilRechner.STAHL);
            }
            artikelService.aktualisiereStammdaten(angelegtArtikel.getId(), r);
            nummern.add(v.nummer());
            namen.add(v.name().toLowerCase(Locale.ROOT));
            angelegt++;
        }
        return new Ergebnis(angelegt, uebersprungen);
    }

    private Long kategorie(String bezeichnung) {
        for (Kategorie k : kategorien.findAll()) {
            if (k.getBeschreibung() != null && k.getBeschreibung().equalsIgnoreCase(bezeichnung)) {
                return k.getId().longValue();
            }
        }
        Kategorie neu = new Kategorie();
        neu.setBeschreibung(bezeichnung);
        return kategorien.save(neu).getId().longValue();
    }
}
