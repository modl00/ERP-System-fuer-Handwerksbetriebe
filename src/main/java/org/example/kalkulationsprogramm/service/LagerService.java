package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.LagerArtikel;
import org.example.kalkulationsprogramm.domain.LagerBuchung;
import org.example.kalkulationsprogramm.domain.Lagerort;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungDto;
import org.example.kalkulationsprogramm.dto.Lager.ArtikelAenderungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerZusammenfassungDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerortEingabe;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LagerArtikelRepository;
import org.example.kalkulationsprogramm.repository.LagerBuchungRepository;
import org.example.kalkulationsprogramm.repository.LagerortRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lagerverwaltung im kleinsten sinnvollen Umfang: Lagerorte, Bestand je Artikel und Lagerort, Zugang, Entnahme, Inventur und Mindestbestand.
 *
 * <ul>
 *   <li>Der Bestand ist immer die <b>Summe der Buchungen</b>. Es gibt keinen gespeicherten Zählerstand, der auseinanderlaufen könnte.</li>
 *   <li>Mengen stehen in der <b>Verrechnungseinheit des Artikels</b> (Stück, laufende Meter, Quadratmeter, Kilogramm).</li>
 *   <li>Der Bestand darf negativ werden. Eine Entnahme wird nie blockiert, weil Ware auch dann am Projekt gebraucht wird, wenn der Zugang nicht
 *       eingebucht wurde; die Oberfläche zeigt einen negativen Bestand deutlich an ("Zugang oder Inventur fehlt").</li>
 *   <li>Projektmaterial "aus Lager" bucht selbst ab ({@link #synchronisierePosition}) und bucht zurück, wenn die Position gelöscht wird.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LagerService {

    static final BigDecimal MAX_MENGE = new BigDecimal("10000000");
    private static final int NACHKOMMA = 3;

    private final LagerortRepository orte;
    private final LagerBuchungRepository buchungen;
    private final LagerArtikelRepository lagerArtikel;
    private final ArtikelRepository artikel;
    private final ProjektRepository projekte;

    /** Optional: Ist das Lagermodul ausgeschaltet, bucht Projektmaterial "aus Lager" nichts mehr ab. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LagerModulSchalterService modulSchalter;

    // ---------------------------------------------------------------- Lagerorte

    @Transactional(readOnly = true)
    public List<LagerortDto> orte() {
        return orte.findAllByOrderBySortOrderAscNameAsc().stream().map(LagerService::dto).toList();
    }

    @Transactional
    public LagerortDto ortAnlegen(LagerortEingabe eingabe) {
        String name = pruefeOrtName(eingabe, null);
        Lagerort o = new Lagerort();
        o.setName(name);
        o.setBeschreibung(beschreibung(eingabe));
        o.setSortOrder((int) orte.count());
        return dto(orte.save(o));
    }

    @Transactional
    public LagerortDto ortAendern(Long id, LagerortEingabe eingabe) {
        Lagerort o = ort(id);
        o.setName(pruefeOrtName(eingabe, o));
        o.setBeschreibung(beschreibung(eingabe));
        return dto(orte.save(o));
    }

    @Transactional
    public LagerortDto ortAktiv(Long id, boolean aktiv) {
        Lagerort o = ort(id);
        if (!aktiv && o.isAktiv() && orte.countByAktivTrue() <= 1) {
            throw fehler("Mindestens ein Lagerort muss aktiv bleiben.");
        }
        o.setAktiv(aktiv);
        return dto(orte.save(o));
    }

    // ---------------------------------------------------------------- Bestand

    /** Alle Artikel, die im Lager geführt werden (mindestens eine Buchung oder ein Mindestbestand). */
    @Transactional(readOnly = true)
    public List<LagerBestandDto> bestand(String suche, boolean nurUnterMindestbestand) {
        String q = suche == null ? "" : suche.strip().toLowerCase(Locale.ROOT);
        List<LagerBestandDto> zeilen = alleZeilen().stream()
                .filter(z -> q.isEmpty()
                        || z.bezeichnung().toLowerCase(Locale.ROOT).contains(q)
                        || (z.artikelnummer() != null && z.artikelnummer().toLowerCase(Locale.ROOT).contains(q)))
                .filter(z -> !nurUnterMindestbestand || z.unterMindestbestand() || z.negativ())
                .toList();
        return zeilen;
    }

    /** Der Bestand eines einzelnen Artikels, auch wenn noch nichts gebucht wurde (dann 0). */
    @Transactional(readOnly = true)
    public LagerBestandDto bestandVon(Long artikelId) {
        finde(artikelId);
        return zeile(artikelId);
    }

    /**
     * Der Bestand der genannten Artikel, auch ohne Buchungen (dann 0), in der Reihenfolge der Kennungen. Unbekannte Kennungen fehlen. Ein Aufruf liest den Lagerbestand nur einmal,
     * anders als wiederholtes {@link #bestandVon(Long)}.
     */
    @Transactional(readOnly = true)
    public List<LagerBestandDto> bestandFuer(java.util.Collection<Long> artikelIds) {
        if (artikelIds == null || artikelIds.isEmpty()) {
            return List.of();
        }
        Map<Long, LagerBestandDto> alle = new java.util.HashMap<>();
        for (LagerBestandDto z : alleZeilen()) {
            alle.put(z.artikelId(), z);
        }
        List<LagerBestandDto> ergebnis = new java.util.ArrayList<>();
        for (Long id : artikelIds) {
            LagerBestandDto z = alle.get(id);
            if (z == null) {
                z = artikel.findById(id).map(a -> baueZeile(a, Map.of(), null)).orElse(null);
            }
            if (z != null) {
                ergebnis.add(z);
            }
        }
        return ergebnis;
    }

    /** Wie weit zurück die Buchungen für „häufig benutzt“ zählen. */
    static final int HAEUFIG_TAGE = 90;

    /**
     * Die am häufigsten gebuchten Artikel der letzten {@value #HAEUFIG_TAGE} Tage: zuerst die des Benutzers, dann (zum Auffüllen bis {@code max}) die des ganzen Betriebs. Ohne
     * Buchungen leer.
     */
    @Transactional(readOnly = true)
    public List<Long> haeufigeArtikelIds(String benutzer, int max) {
        java.time.LocalDateTime seit = java.time.LocalDateTime.now().minusDays(HAEUFIG_TAGE);
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        if (benutzer != null && !benutzer.isBlank()) {
            ids.addAll(buchungen.haeufigsteArtikelVon(seit, benutzer, org.example.kalkulationsprogramm.domain.LagerBuchung.Art.KORREKTUR, org.springframework.data.domain.PageRequest.of(0, max)));
        }
        if (ids.size() < max) {
            ids.addAll(buchungen.haeufigsteArtikel(seit, org.example.kalkulationsprogramm.domain.LagerBuchung.Art.KORREKTUR, org.springframework.data.domain.PageRequest.of(0, max)));
        }
        return ids.stream().limit(max).toList();
    }

    @Transactional(readOnly = true)
    public LagerZusammenfassungDto zusammenfassung() {
        List<LagerBestandDto> alle = alleZeilen();
        return new LagerZusammenfassungDto(alle.size(),
                (int) alle.stream().filter(LagerBestandDto::unterMindestbestand).count(),
                (int) alle.stream().filter(LagerBestandDto::negativ).count());
    }

    @Transactional(readOnly = true)
    public List<LagerBuchungDto> verlauf(Long artikelId) {
        finde(artikelId);
        return buchungen.findByArtikelIdOrderByGebuchtAmDescIdDesc(artikelId, PageRequest.of(0, 200)).stream()
                .map(b -> new LagerBuchungDto(b.getId(), b.getArt().name(), b.getMenge(), b.getLagerort().getId(), b.getLagerort().getName(),
                        b.getGebuchtAm(), b.getProjekt() == null ? null : b.getProjekt().getId(), projektName(b.getProjekt()),
                        b.getNotiz(), b.getBenutzer()))
                .toList();
    }

    @Transactional
    public LagerBestandDto buche(LagerBuchungEingabe e) {
        return buche(e, aktuellerBenutzer());
    }

    /** Wie {@link #buche(LagerBuchungEingabe)}, mit ausdrücklich genanntem Benutzer (zum Beispiel der Mitarbeiter der Handy-App, die ohne Benutzer-Anmeldung arbeitet). */
    @Transactional
    public LagerBestandDto buche(LagerBuchungEingabe e, String benutzer) {
        if (e == null || e.art() == null) {
            throw fehler("Bitte angeben, was gebucht werden soll.");
        }
        LagerBuchung.Art art;
        try {
            art = LagerBuchung.Art.valueOf(e.art().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw fehler("Unbekannte Buchungsart: " + e.art());
        }
        Artikel a = finde(e.artikelId());
        Lagerort o = ort(e.lagerortId());
        if (!o.isAktiv()) {
            throw fehler("Der Lagerort \"" + o.getName() + "\" ist nicht mehr aktiv.");
        }
        BigDecimal menge = e.menge();
        if (menge == null) {
            throw fehler("Bitte eine Menge angeben.");
        }
        menge = menge.setScale(NACHKOMMA, RoundingMode.HALF_UP);
        if (menge.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Menge ist zu groß.");
        }
        String notiz = notiz(e.notiz());
        Projekt projekt = null;
        if (e.projektId() != null) {
            if (art != LagerBuchung.Art.ABGANG) {
                throw fehler("Ein Projekt lässt sich nur bei einer Entnahme angeben.");
            }
            projekt = projekte.findById(e.projektId()).orElseThrow(() -> fehler("Das Projekt existiert nicht."));
        }

        BigDecimal signiert;
        switch (art) {
            case ZUGANG -> {
                if (menge.signum() <= 0) {
                    throw fehler("Die Menge muss größer als 0 sein.");
                }
                signiert = menge;
            }
            case ABGANG -> {
                if (menge.signum() <= 0) {
                    throw fehler("Die Menge muss größer als 0 sein.");
                }
                signiert = menge.negate();
            }
            default -> {
                if (menge.signum() < 0) {
                    throw fehler("Der gezählte Bestand darf nicht negativ sein.");
                }
                signiert = menge.subtract(buchungen.bestand(a.getId(), o.getId()));
                if (signiert.signum() == 0) {
                    return zeile(a.getId());   // Zählung stimmt mit dem Bestand überein: nichts zu buchen
                }
            }
        }
        speichere(a, o, art, signiert, projekt, null, notiz, benutzer(benutzer));
        return zeile(a.getId());
    }

    @Transactional
    public LagerBestandDto setzeMindestbestand(Long artikelId, BigDecimal mindestbestand) {
        Artikel a = finde(artikelId);
        if (mindestbestand != null && (mindestbestand.signum() < 0 || mindestbestand.compareTo(MAX_MENGE) > 0)) {
            throw fehler("Der Mindestbestand muss zwischen 0 und 10.000.000 liegen.");
        }
        LagerArtikel la = lagerArtikel.findByArtikelId(a.getId()).orElseGet(() -> {
            LagerArtikel neu = new LagerArtikel();
            neu.setArtikel(a);
            return neu;
        });
        la.setMindestbestand(mindestbestand == null ? null : mindestbestand.setScale(NACHKOMMA, RoundingMode.HALF_UP));
        lagerArtikel.save(la);
        return zeile(a.getId());
    }

    /**
     * Legt Ware von einem Lagerort an einen anderen: eine Entnahme am alten und ein Zugang am neuen Ort, in einem Schritt.
     * Der Gesamtbestand bleibt gleich; im Verlauf stehen beide Buchungen mit dem Hinweis auf die Umbuchung.
     */
    @Transactional
    public LagerBestandDto umbuche(UmbuchungEingabe e) {
        return umbuche(e, aktuellerBenutzer());
    }

    /** Wie {@link #umbuche(UmbuchungEingabe)}, mit ausdrücklich genanntem Benutzer. */
    @Transactional
    public LagerBestandDto umbuche(UmbuchungEingabe e, String benutzer) {
        if (e == null) {
            throw fehler("Bitte angeben, was umgebucht werden soll.");
        }
        Artikel a = finde(e.artikelId());
        Lagerort von = ort(e.vonLagerortId());
        Lagerort nach = ort(e.nachLagerortId());
        if (von.getId().equals(nach.getId())) {
            throw fehler("Bitte zwei verschiedene Lagerorte wählen.");
        }
        if (!nach.isAktiv()) {
            throw fehler("Der Lagerort \"" + nach.getName() + "\" ist nicht mehr aktiv.");
        }
        BigDecimal menge = e.menge();
        if (menge == null || menge.signum() <= 0) {
            throw fehler("Die Menge muss größer als 0 sein.");
        }
        menge = menge.setScale(NACHKOMMA, RoundingMode.HALF_UP);
        if (menge.signum() <= 0 || menge.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Menge muss zwischen 0 und 10.000.000 liegen.");
        }
        if (buchungen.bestand(a.getId(), von.getId()).compareTo(menge) < 0) {
            throw fehler("Am Lagerort \"" + von.getName() + "\" sind nicht so viele vorhanden.");
        }
        String hinweis = notiz(e.notiz());
        String zusatz = hinweis == null ? "" : " – " + hinweis;
        speichere(a, von, LagerBuchung.Art.ABGANG, menge.negate(), null, null, notiz("Umbuchung nach " + nach.getName() + zusatz), benutzer(benutzer));
        speichere(a, nach, LagerBuchung.Art.ZUGANG, menge, null, null, notiz("Umbuchung von " + von.getName() + zusatz), benutzer(benutzer));
        return zeile(a.getId());
    }

    /** Ändert Bezeichnung und Einheit eines Artikels. Vorhandene Mengen bleiben, sie gelten dann in der neuen Einheit. */
    @Transactional
    public LagerBestandDto artikelAendern(Long artikelId, ArtikelAenderungEingabe e) {
        Artikel a = finde(artikelId);
        if (e == null) {
            throw fehler("Bitte die neuen Angaben mitschicken.");
        }
        String name = e.bezeichnung() == null ? "" : e.bezeichnung().strip();
        if (name.isEmpty()) {
            throw fehler("Bitte eine Bezeichnung angeben.");
        }
        if (name.length() > 255) {
            throw fehler("Die Bezeichnung ist zu lang.");
        }
        Verrechnungseinheit einheit;
        try {
            einheit = Verrechnungseinheit.fromValue(e.einheit());
        } catch (IllegalArgumentException ex) {
            throw fehler("Bitte eine gültige Einheit wählen.");
        }
        // Im Lager steht die Kurzbeschreibung vor dem Produktnamen: Wird sie angezeigt, muss die Änderung dort ankommen
        if (a.getKurzbeschreibung() != null && !a.getKurzbeschreibung().isBlank()) {
            a.setKurzbeschreibung(name);
        }
        a.setProduktname(name);
        a.setVerrechnungseinheit(einheit);
        artikel.save(a);
        return zeile(a.getId());
    }

    // ---------------------------------------------------------------- Projektmaterial "aus Lager"

    /**
     * Bringt das Lager in Einklang mit einer Projektposition: Ist die Position "aus Lager" und vorhanden, ist ihre Menge entnommen, sonst
     * zurückgebucht. Mehrfaches Aufrufen ist harmlos (es wird nur die Differenz gebucht).
     *
     * @param vorhanden {@code false}, wenn die Position gerade gelöscht wird
     */
    @Transactional
    public void synchronisierePosition(ArtikelInProjekt position, boolean vorhanden) {
        if (position == null || position.getId() == null || position.getArtikel() == null) {
            return;
        }
        if (modulSchalter != null && !modulSchalter.istAktiv()) {
            return;   // Modul ausgeschaltet: kein Lager, keine neue Buchung (vorhandene bleiben)
        }
        BigDecimal soll = BigDecimal.ZERO;
        if (vorhanden && position.isAusLager()) {
            BigDecimal menge = mengeAusPosition(position);
            if (menge == null || menge.signum() <= 0) {
                log.warn("Lager: Für die Projektposition {} ließ sich keine Menge ermitteln, es wurde nichts gebucht.", position.getId());
                return;
            }
            soll = menge.setScale(NACHKOMMA, RoundingMode.HALF_UP);
        }
        BigDecimal entnommen = buchungen.summeFuerPosition(position.getId()).negate();
        BigDecimal differenz = soll.subtract(entnommen);
        if (differenz.signum() == 0) {
            return;
        }
        Lagerort ort = differenz.signum() > 0 ? standardOrt() : ortDerPosition(position.getId());
        String projektName = projektName(position.getProjekt());
        if (differenz.signum() > 0) {
            speichere(position.getArtikel(), ort, LagerBuchung.Art.ABGANG, differenz.negate(), position.getProjekt(), position.getId(),
                    "Material für Projekt " + projektName);
        } else {
            speichere(position.getArtikel(), ort, LagerBuchung.Art.ZUGANG, differenz.negate(), position.getProjekt(), position.getId(),
                    "Rückbuchung: Material aus Projekt " + projektName + " entfernt");
        }
    }

    /** Menge der Position in der Verrechnungseinheit des Artikels, {@code null} wenn nicht ermittelbar. */
    static BigDecimal mengeAusPosition(ArtikelInProjekt p) {
        Verrechnungseinheit einheit = p.getArtikel().getVerrechnungseinheit();
        BigDecimal stueck = p.getStueckzahl() == null ? null : BigDecimal.valueOf(p.getStueckzahl());
        if (einheit == null || einheit == Verrechnungseinheit.STUECK) {
            return stueck;
        }
        return switch (einheit) {
            case KILOGRAMM -> p.getKilogramm();
            case LAUFENDE_METER, QUADRATMETER -> p.getMeter() != null ? p.getMeter() : stueck;
            default -> stueck;
        };
    }

    // ---------------------------------------------------------------- intern

    private LagerBestandDto zeile(Long artikelId) {
        return alleZeilen().stream().filter(z -> z.artikelId().equals(artikelId)).findFirst()
                .orElseGet(() -> baueZeile(finde(artikelId), Map.of(), null));
    }

    private List<LagerBestandDto> alleZeilen() {
        Map<Long, Map<Long, BigDecimal>> jeArtikel = new HashMap<>();
        for (Object[] r : buchungen.bestandJeArtikelUndOrt()) {
            jeArtikel.computeIfAbsent((Long) r[0], k -> new HashMap<>()).put((Long) r[1], (BigDecimal) r[2]);
        }
        Map<Long, BigDecimal> mindest = new HashMap<>();
        for (LagerArtikel la : lagerArtikel.findAll()) {
            mindest.put(la.getArtikel().getId(), la.getMindestbestand());
        }
        Set<Long> ids = new LinkedHashSet<>(jeArtikel.keySet());
        ids.addAll(mindest.keySet());
        Map<Long, Lagerort> ortNamen = new HashMap<>();
        orte.findAll().forEach(o -> ortNamen.put(o.getId(), o));

        List<LagerBestandDto> ergebnis = new ArrayList<>();
        for (Artikel a : artikel.findAllById(ids)) {
            Map<Long, BigDecimal> proOrt = jeArtikel.getOrDefault(a.getId(), Map.of());
            LagerBestandDto z = baueZeile(a, proOrt, mindest.get(a.getId()), ortNamen);
            ergebnis.add(z);
        }
        ergebnis.sort(Comparator
                .comparing((LagerBestandDto z) -> !(z.negativ() || z.unterMindestbestand()))
                .thenComparing(z -> z.bezeichnung().toLowerCase(Locale.ROOT)));
        return ergebnis;
    }

    private LagerBestandDto baueZeile(Artikel a, Map<Long, BigDecimal> proOrt, BigDecimal mindest) {
        Map<Long, Lagerort> ortNamen = new HashMap<>();
        orte.findAll().forEach(o -> ortNamen.put(o.getId(), o));
        return baueZeile(a, proOrt, mindest, ortNamen);
    }

    private LagerBestandDto baueZeile(Artikel a, Map<Long, BigDecimal> proOrt, BigDecimal mindest, Map<Long, Lagerort> ortNamen) {
        BigDecimal summe = proOrt.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LagerBestandDto.OrtBestand> liste = proOrt.entrySet().stream()
                .filter(e -> e.getValue().signum() != 0)
                .map(e -> new LagerBestandDto.OrtBestand(e.getKey(), ortNamen.containsKey(e.getKey()) ? ortNamen.get(e.getKey()).getName() : "?", e.getValue()))
                .sorted(Comparator.comparing(LagerBestandDto.OrtBestand::lagerortName))
                .toList();
        boolean unter = mindest != null && summe.compareTo(mindest) < 0;
        BigDecimal kgProMeter = a instanceof ArtikelWerkstoffe aw ? aw.getMasse() : null;
        return new LagerBestandDto(a.getId(), bezeichnung(a), a.getArtikelnummer(), einheit(a), summe, mindest, unter, summe.signum() < 0, liste,
                kgProMeter, a.getHandelseinheitBezeichnung(), a.getHandelseinheitMeter(), a.getHandelseinheitVarianten());
    }

    private void speichere(Artikel a, Lagerort o, LagerBuchung.Art art, BigDecimal signiert, Projekt projekt, Long positionId, String notiz) {
        speichere(a, o, art, signiert, projekt, positionId, notiz, aktuellerBenutzer());
    }

    private void speichere(Artikel a, Lagerort o, LagerBuchung.Art art, BigDecimal signiert, Projekt projekt, Long positionId, String notiz, String benutzer) {
        LagerBuchung b = new LagerBuchung();
        b.setArtikel(a);
        b.setLagerort(o);
        b.setArt(art);
        b.setMenge(signiert);
        b.setGebuchtAm(LocalDateTime.now());
        b.setProjekt(projekt);
        b.setArtikelInProjektId(positionId);
        b.setNotiz(notiz);
        b.setBenutzer(benutzer);
        buchungen.save(b);
    }

    /** Der Lagerort für automatische Buchungen: der erste aktive, sonst wird "Hauptlager" angelegt oder wieder aktiviert. */
    private Lagerort standardOrt() {
        return orte.findFirstByAktivTrueOrderBySortOrderAscIdAsc().orElseGet(() -> {
            Lagerort o = orte.findByNameIgnoreCase("Hauptlager").orElseGet(() -> {
                Lagerort neu = new Lagerort();
                neu.setName("Hauptlager");
                neu.setBeschreibung("Standard-Lagerort");
                return neu;
            });
            o.setAktiv(true);
            return orte.save(o);
        });
    }

    /** Rückbuchungen gehen an den Lagerort, aus dem entnommen wurde (auch wenn er inzwischen deaktiviert ist). */
    private Lagerort ortDerPosition(Long positionId) {
        return buchungen.findByArtikelInProjektIdOrderByIdDesc(positionId).stream().findFirst()
                .map(LagerBuchung::getLagerort).orElseGet(this::standardOrt);
    }

    private Artikel finde(Long id) {
        if (id == null || id <= 0) {
            throw fehler("Bitte einen Artikel wählen.");
        }
        return artikel.findById(id).orElseThrow(() -> fehler("Der Artikel existiert nicht."));
    }

    private Lagerort ort(Long id) {
        if (id == null || id <= 0) {
            throw fehler("Bitte einen Lagerort wählen.");
        }
        return orte.findById(id).orElseThrow(() -> fehler("Der Lagerort existiert nicht."));
    }

    private String pruefeOrtName(LagerortEingabe e, Lagerort bisher) {
        if (e == null || e.name() == null || e.name().isBlank()) {
            throw fehler("Bitte einen Namen für den Lagerort eingeben.");
        }
        String name = e.name().strip();
        if (name.length() > 120) {
            throw fehler("Der Name ist zu lang (höchstens 120 Zeichen).");
        }
        boolean gleicherName = orte.findByNameIgnoreCase(name).filter(o -> bisher == null || !o.getId().equals(bisher.getId())).isPresent();
        if (gleicherName) {
            throw fehler("Es gibt schon einen Lagerort mit diesem Namen.");
        }
        return name;
    }

    private static String beschreibung(LagerortEingabe e) {
        if (e.beschreibung() == null || e.beschreibung().isBlank()) {
            return null;
        }
        if (e.beschreibung().length() > 255) {
            throw fehler("Die Beschreibung ist zu lang (höchstens 255 Zeichen).");
        }
        return e.beschreibung().strip();
    }

    private static String notiz(String n) {
        if (n == null || n.isBlank()) {
            return null;
        }
        if (n.length() > 255) {
            throw fehler("Die Notiz ist zu lang (höchstens 255 Zeichen).");
        }
        return n.strip();
    }

    private static LagerortDto dto(Lagerort o) {
        return new LagerortDto(o.getId(), o.getName(), o.getBeschreibung(), o.isAktiv(), o.getSortOrder(),
                o.isArchiviert(), o.getArchiviertAm());
    }

    @Transactional
    public LagerortDto ortArchivieren(Long id) {
        Lagerort o = ort(id);
        o.setArchiviert(true);
        o.setArchiviertAm(java.time.LocalDateTime.now());
        return dto(orte.save(o));
    }

    @Transactional
    public LagerortDto ortWiederherstellen(Long id) {
        Lagerort o = ort(id);
        o.setArchiviert(false);
        o.setArchiviertAm(null);
        return dto(orte.save(o));
    }

    @Transactional
    public void ortLoeschen(Long id) {
        Lagerort o = ort(id);
        if (!o.isArchiviert()) {
            throw fehler("Nur archivierte Lagerorte dürfen gelöscht werden.");
        }
        if (buchungen.existsByLagerortId(id)) {
            throw fehler("Dieser Lagerort hat Buchungen und kann nicht gelöscht werden. Die Buchungshistorie muss erhalten bleiben.");
        }
        orte.delete(o);
    }

    private static String projektName(Projekt p) {
        if (p == null) {
            return null;
        }
        String nr = p.getAuftragsnummer() == null ? "" : p.getAuftragsnummer().strip();
        String bv = p.getBauvorhaben() == null ? "" : p.getBauvorhaben().strip();
        String text = (nr + " " + bv).strip();
        return text.isEmpty() ? "Projekt " + p.getId() : text;
    }

    static String einheit(Artikel a) {
        Verrechnungseinheit e = a.getVerrechnungseinheit();
        if (e == null) {
            return "Stk";
        }
        return switch (e) {
            case LAUFENDE_METER -> "m";
            case QUADRATMETER -> "m²";
            case KILOGRAMM -> "kg";
            case LITER -> "l";
            default -> "Stk";
        };
    }

    static String bezeichnung(Artikel a) {
        for (String s : new String[] { a.getKurzbeschreibung(), a.getProduktname(), a.getArtikelnummer() }) {
            if (s != null && !s.isBlank()) {
                return s.strip();
            }
        }
        return "Artikel " + a.getId();
    }

    /** Der Name für die Buchung: höchstens 120 Zeichen, leer wird zu {@code null}. */
    private static String benutzer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String s = name.strip();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static String aktuellerBenutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || "anonymousUser".equals(name) ? null : (name.length() > 120 ? name.substring(0, 120) : name);
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
