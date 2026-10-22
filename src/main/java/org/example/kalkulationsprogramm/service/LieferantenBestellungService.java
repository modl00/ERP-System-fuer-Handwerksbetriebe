package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellPosition;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.DokumentnummerCounter;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.LieferantenBestellung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.BestellPositionAnhang;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungEingabe;
import org.example.kalkulationsprogramm.dto.Bestellwesen.FertigungsunterlageDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionAnhangRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionRepository;
import org.example.kalkulationsprogramm.repository.DokumentnummerCounterRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenBestellungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Bestellwesen Stufe 1 (Issue #86): Bestellungen an Lieferanten zusammenstellen (aus dem Mindestbestand des Lagers, aus dem Material eines Projekts oder
 * frei aus dem Artikelstamm), mit lückenloser Bestellnummer bestellen, den Lieferstand pflegen und stornieren.
 *
 * <p>Regeln: Ein Entwurf hat keine Nummer und lässt sich beliebig ändern oder löschen. Beim Bestellen wird die Nummer vergeben (je Monat lückenlos,
 * Zähler mit Sperre) und die zugehörigen Projektpositionen gelten als bestellt. Eine bestellte Bestellung ändert sich nicht mehr; möglich bleiben
 * "Position geliefert" und Storno mit Grund (die Projektpositionen, die noch nicht geliefert sind, erscheinen dann wieder als "zu bestellen").
 */
@Service
@RequiredArgsConstructor
public class LieferantenBestellungService {

    /** Ab so vielen Tagen ohne vollständige Lieferung wird eine Bestellung in der Übersicht als überfällig markiert (nur Anzeige). */
    public static final int UEBERFAELLIG_NACH_TAGEN = 7;

    private static final BigDecimal MAX_MENGE = new BigDecimal("1000000");
    private static final Set<BestellStatus> OFFEN = EnumSet.of(BestellStatus.ENTWURF, BestellStatus.BESTELLT);
    private static final String ZAEHLER_VORSATZ = "B";
    private static final Set<String> EINHEITEN = Set.of("Stk", "m", "m²", "kg", "l");

    private final LieferantenBestellungRepository bestellungen;
    private final BestellPositionRepository positionen;
    private final LieferantenRepository lieferanten;
    private final ArtikelRepository artikel;
    private final ProjektRepository projekte;
    private final ArtikelInProjektRepository projektPositionen;
    private final LieferantenArtikelPreiseRepository preise;
    private final DokumentnummerCounterRepository zaehler;
    private final LagerService lager;
    private final BestellungService projektBestellungen;
    private final BestellPositionAnhangRepository positionAnhaenge;

    // ------------------------------------------------------------------ Lesen

    /** Alle Bestellungen, neueste zuerst; Filter sind optional. {@code status} ist ein Wert aus {@link BestellStatus} oder LIEFERSTAND wie in {@link BestellungDto}. */
    @Transactional(readOnly = true)
    public List<BestellungDto> liste(String stand, Long lieferantId, Long projektId, boolean nurUeberfaellig) {
        String s = stand == null || stand.isBlank() ? null : stand.strip().toUpperCase(Locale.ROOT);
        return bestellungen.findAllByOrderByIdDesc().stream()
                .map(this::dto)
                .filter(b -> s == null || s.equals(b.status().name()) || s.equals(b.lieferstand()))
                .filter(b -> lieferantId == null || lieferantId.equals(b.lieferantId()))
                .filter(b -> projektId == null || b.positionen().stream().anyMatch(p -> projektId.equals(p.projektId())))
                .filter(b -> !nurUeberfaellig || b.ueberfaellig())
                .toList();
    }

    @Transactional(readOnly = true)
    public BestellungDto lade(Long id) {
        return dto(finde(id));
    }

    // ------------------------------------------------------------------ Entwurf

    @Transactional
    public BestellungDto anlegen(BestellungEingabe e) {
        LieferantenBestellung b = new LieferantenBestellung();
        b.setErstelltAm(LocalDateTime.now());
        b.setErstelltVon(aktuellerBenutzer());
        uebernehme(b, e);
        return dto(bestellungen.save(b));
    }

    /** Ersetzt Lieferant, Notiz und Positionen eines Entwurfs. */
    @Transactional
    public BestellungDto aendern(Long id, BestellungEingabe e) {
        LieferantenBestellung b = finde(id);
        verlangeEntwurf(b, "Eine bestellte Bestellung lässt sich nicht mehr ändern.");
        uebernehme(b, e);
        return dto(bestellungen.save(b));
    }

    @Transactional
    public void loeschen(Long id) {
        LieferantenBestellung b = finde(id);
        verlangeEntwurf(b, "Nur ein Entwurf lässt sich löschen. Eine bestellte Bestellung kann man stornieren.");
        bestellungen.delete(b);
    }

    // ------------------------------------------------------------------ Bestellen, Lieferung, Storno

    /**
     * Vergibt die Bestellnummer und setzt die Bestellung auf "bestellt". {@code per} ist EMAIL (die Oberfläche versendet danach die E-Mail mit dem PDF)
     * oder ANDERS (zum Beispiel telefonisch bestellt).
     */
    @Transactional
    public BestellungDto bestellen(Long id, String per) {
        LieferantenBestellung b = finde(id);
        verlangeEntwurf(b, "Diese Bestellung ist schon bestellt.");
        if (b.getPositionen().isEmpty()) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Bestellung hat noch keine Positionen.");
        }
        String art = per == null ? "EMAIL" : per.strip().toUpperCase(Locale.ROOT);
        if (!art.equals("EMAIL") && !art.equals("ANDERS")) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte angeben, wie bestellt wurde (E-Mail oder anders).");
        }
        // Eine Projektposition darf nicht in zwei Bestellungen stecken (Entwürfe dürfen sich überschneiden, bestellt wird nur einmal).
        for (BestellPosition p : b.getPositionen()) {
            if (p.getArtikelInProjektId() == null) {
                continue;
            }
            ArtikelInProjekt aip = projektPositionen.findById(p.getArtikelInProjektId()).orElse(null);
            if (aip != null && aip.isBestellt()) {
                throw fehler(HttpStatus.CONFLICT, "\"" + p.getBezeichnung() + "\" ist im Projekt " + projektName(aip.getProjekt())
                        + " schon als bestellt markiert. Bitte die Position aus dieser Bestellung entfernen.");
            }
        }
        b.setBestellnummer(naechsteNummer());
        b.setStatus(BestellStatus.BESTELLT);
        b.setBestelltAm(LocalDateTime.now());
        b.setBestelltVon(aktuellerBenutzer());
        b.setBestelltPer(art);
        for (BestellPosition p : b.getPositionen()) {
            if (p.getArtikelInProjektId() != null && projektPositionen.existsById(p.getArtikelInProjektId())) {
                projektBestellungen.setBestellt(p.getArtikelInProjektId(), true);
            }
        }
        return dto(bestellungen.save(b));
    }

    /** Markiert eine Position als geliefert (oder nimmt es zurück). Das Lager wird dabei nicht gebucht (Wareneingang folgt später). */
    @Transactional
    public BestellungDto liefern(Long id, Long positionId, boolean geliefert) {
        LieferantenBestellung b = finde(id);
        if (b.getStatus() != BestellStatus.BESTELLT) {
            throw fehler(HttpStatus.CONFLICT, "Geliefert lässt sich nur bei einer bestellten Bestellung eintragen.");
        }
        BestellPosition p = b.getPositionen().stream().filter(x -> x.getId().equals(positionId)).findFirst()
                .orElseThrow(() -> fehler(HttpStatus.NOT_FOUND, "Diese Position gehört nicht zur Bestellung."));
        p.setGeliefertAm(geliefert ? LocalDate.now() : null);
        return dto(bestellungen.save(b));
    }

    /** Alle noch offenen Positionen auf einmal als geliefert eintragen. */
    @Transactional
    public BestellungDto alleGeliefert(Long id) {
        LieferantenBestellung b = finde(id);
        if (b.getStatus() != BestellStatus.BESTELLT) {
            throw fehler(HttpStatus.CONFLICT, "Geliefert lässt sich nur bei einer bestellten Bestellung eintragen.");
        }
        LocalDate heute = LocalDate.now();
        b.getPositionen().stream().filter(p -> p.getGeliefertAm() == null).forEach(p -> p.setGeliefertAm(heute));
        return dto(bestellungen.save(b));
    }

    @Transactional
    public BestellungDto stornieren(Long id, String grund) {
        LieferantenBestellung b = finde(id);
        if (b.getStatus() != BestellStatus.BESTELLT) {
            throw fehler(HttpStatus.CONFLICT, b.getStatus() == BestellStatus.ENTWURF
                    ? "Ein Entwurf wird gelöscht, nicht storniert." : "Diese Bestellung ist schon storniert.");
        }
        String g = grund == null ? "" : grund.strip();
        if (g.length() < 3) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte kurz sagen, warum die Bestellung storniert wird.");
        }
        b.setStatus(BestellStatus.STORNIERT);
        b.setStorniertAm(LocalDateTime.now());
        b.setStornoGrund(g.length() > 500 ? g.substring(0, 500) : g);
        // Noch nicht gelieferte Projektpositionen gelten wieder als "zu bestellen".
        for (BestellPosition p : b.getPositionen()) {
            if (p.getGeliefertAm() == null && p.getArtikelInProjektId() != null && projektPositionen.existsById(p.getArtikelInProjektId())) {
                projektBestellungen.setBestellt(p.getArtikelInProjektId(), false);
            }
        }
        return dto(bestellungen.save(b));
    }

    // ------------------------------------------------------------------ Vorschläge

    /** Lagerartikel unter Mindestbestand: Menge bis zum Mindestbestand, abzüglich dessen, was schon in einer Bestellung unterwegs ist. */
    @Transactional(readOnly = true)
    public List<BestellVorschlagDto> vorschlaegeMindestbestand() {
        Map<Long, BigDecimal> unterwegs = new HashMap<>();
        for (BestellPosition p : positionen.findByBestellung_StatusInAndHerkunftAndGeliefertAmIsNull(OFFEN, BestellHerkunft.LAGER)) {
            if (p.getArtikel() != null) {
                unterwegs.merge(p.getArtikel().getId(), p.getMenge(), BigDecimal::add);
            }
        }
        List<BestellVorschlagDto> ergebnis = new ArrayList<>();
        for (LagerBestandDto z : lager.bestand(null, true)) {
            if (z.mindestbestand() == null || z.bestand().compareTo(z.mindestbestand()) >= 0) {
                continue;
            }
            BigDecimal fehlt = z.mindestbestand().subtract(z.bestand());
            BigDecimal schon = unterwegs.getOrDefault(z.artikelId(), BigDecimal.ZERO);
            BigDecimal menge = fehlt.subtract(schon);
            if (menge.signum() <= 0) {
                continue;
            }
            LieferantenArtikelPreise guenstigster = preise.findeAktuellePreise(z.artikelId()).stream().findFirst().orElse(null);
            String hinweis = "Bestand " + zahl(z.bestand()) + " " + z.einheit() + ", Mindestbestand " + zahl(z.mindestbestand()) + " " + z.einheit()
                    + (schon.signum() > 0 ? ", schon unterwegs " + zahl(schon) : "");
            ergebnis.add(new BestellVorschlagDto(z.artikelId(), z.artikelnummer(), z.bezeichnung(), z.einheit(), menge.setScale(3, RoundingMode.HALF_UP),
                    BestellHerkunft.LAGER, null, null, null, guenstigster == null ? null : guenstigster.getLieferant().getId(),
                    guenstigster == null ? null : guenstigster.getLieferant().getLieferantenname(), guenstigster == null ? null : guenstigster.getPreis(),
                    null, hinweis));
        }
        ergebnis.sort(Comparator.comparing(BestellVorschlagDto::bezeichnung, String.CASE_INSENSITIVE_ORDER));
        return ergebnis;
    }

    /** Material eines Projekts, das noch bestellt werden muss (nicht aus dem Lager, noch nicht bestellt). */
    @Transactional(readOnly = true)
    public List<BestellVorschlagDto> vorschlaegeProjekt(Long projektId) {
        Projekt projekt = projekte.findById(projektId).orElseThrow(() -> fehler(HttpStatus.NOT_FOUND, "Das Projekt gibt es nicht."));
        List<BestellVorschlagDto> ergebnis = new ArrayList<>();
        for (ArtikelInProjekt aip : projektPositionen.findByProjekt_Id(projektId)) {
            if (aip.isBestellt() || aip.isAusLager() || aip.getArtikel() == null) {
                continue;
            }
            boolean meter = aip.getMeter() != null && aip.getMeter().signum() > 0;
            BigDecimal menge = meter ? aip.getMeter() : BigDecimal.valueOf(aip.getStueckzahl() == null ? 0 : aip.getStueckzahl());
            if (menge.signum() <= 0) {
                continue;
            }
            Artikel a = aip.getArtikel();
            Lieferanten l = aip.getLieferant();
            BigDecimal preis = l == null ? null : preise.findByArtikel_IdAndLieferant_IdAndAktuellTrue(a.getId(), l.getId())
                    .map(LieferantenArtikelPreise::getPreis).orElse(null);
            ergebnis.add(new BestellVorschlagDto(a.getId(), a.getArtikelnummer(), LagerService.bezeichnung(a), meter ? "m" : "Stk",
                    menge, BestellHerkunft.PROJEKT, projekt.getId(), projektName(projekt), aip.getId(), l == null ? null : l.getId(),
                    l == null ? null : l.getLieferantenname(), preis, aip.getKommentar(), null));
        }
        ergebnis.sort(Comparator.comparing(BestellVorschlagDto::bezeichnung, String.CASE_INSENSITIVE_ORDER));
        return ergebnis;
    }

    // ------------------------------------------------------------------ intern

    private void uebernehme(LieferantenBestellung b, BestellungEingabe e) {
        if (e == null || e.lieferantId() == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte einen Lieferanten wählen.");
        }
        b.setLieferant(lieferanten.findById(e.lieferantId()).orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Den Lieferanten gibt es nicht.")));
        String notiz = e.notiz() == null ? "" : e.notiz().strip();
        b.setNotiz(notiz.isEmpty() ? null : (notiz.length() > 1000 ? notiz.substring(0, 1000) : notiz));
        if (e.positionen() == null || e.positionen().isEmpty()) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte mindestens eine Position angeben.");
        }
        if (e.positionen().size() > 500) {
            throw fehler(HttpStatus.BAD_REQUEST, "Eine Bestellung darf höchstens 500 Positionen haben.");
        }
        List<BestellPosition> neu = new ArrayList<>();
        Set<Long> verwendeteProjektPositionen = new LinkedHashSet<>();
        for (BestellungEingabe.Position pe : e.positionen()) {
            neu.add(position(b, pe, verwendeteProjektPositionen));
        }
        b.getPositionen().clear();
        b.getPositionen().addAll(neu);
    }

    private BestellPosition position(LieferantenBestellung b, BestellungEingabe.Position pe, Set<Long> verwendet) {
        if (pe == null || pe.artikelId() == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Jede Position braucht einen Artikel aus dem Artikelstamm.");
        }
        Artikel a = artikel.findById(pe.artikelId()).orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Einen der Artikel gibt es nicht (mehr)."));
        if (pe.menge() == null || pe.menge().signum() <= 0) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte bei \"" + LagerService.bezeichnung(a) + "\" eine Menge größer als 0 angeben.");
        }
        if (pe.menge().compareTo(MAX_MENGE) > 0) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Menge bei \"" + LagerService.bezeichnung(a) + "\" ist unrealistisch groß.");
        }
        if (pe.einzelpreis() != null && pe.einzelpreis().signum() < 0) {
            throw fehler(HttpStatus.BAD_REQUEST, "Ein Preis darf nicht negativ sein.");
        }
        BestellHerkunft herkunft = pe.herkunft() == null ? BestellHerkunft.FREI : pe.herkunft();
        Projekt projekt = null;
        Long aipId = null;
        if (herkunft == BestellHerkunft.PROJEKT) {
            if (pe.projektId() == null) {
                throw fehler(HttpStatus.BAD_REQUEST, "Eine Projektposition braucht ihr Projekt.");
            }
            projekt = projekte.findById(pe.projektId()).orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Das Projekt gibt es nicht."));
            if (pe.artikelInProjektId() != null) {
                ArtikelInProjekt aip = projektPositionen.findById(pe.artikelInProjektId())
                        .orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Die Materialposition im Projekt gibt es nicht (mehr)."));
                if (aip.getProjekt() == null || !aip.getProjekt().getId().equals(projekt.getId())) {
                    throw fehler(HttpStatus.BAD_REQUEST, "Die Materialposition gehört nicht zu diesem Projekt.");
                }
                if (!verwendet.add(aip.getId())) {
                    throw fehler(HttpStatus.BAD_REQUEST, "Die Materialposition \"" + LagerService.bezeichnung(a) + "\" steht doppelt in der Bestellung.");
                }
                aipId = aip.getId();
            }
        } else if (pe.projektId() != null) {
            // Auch lagerfremde Zeilen dürfen ein Projekt nennen (zum Beispiel "frei, aber für Projekt X").
            projekt = projekte.findById(pe.projektId()).orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Das Projekt gibt es nicht."));
        }
        BigDecimal preis = pe.einzelpreis();
        if (preis == null && b.getLieferant() != null) {
            preis = preise.findByArtikel_IdAndLieferant_IdAndAktuellTrue(a.getId(), b.getLieferant().getId()).map(LieferantenArtikelPreise::getPreis).orElse(null);
        }
        BestellPosition p = new BestellPosition();
        p.setBestellung(b);
        p.setArtikel(a);
        p.setArtikelnummer(a.getArtikelnummer());
        p.setBezeichnung(kuerze(LagerService.bezeichnung(a), 500));
        p.setMenge(pe.menge().setScale(3, RoundingMode.HALF_UP));
        p.setEinheit(einheit(pe.einheit(), a));
        p.setEinzelpreis(preis);
        p.setHerkunft(herkunft);
        p.setProjekt(projekt);
        p.setArtikelInProjektId(aipId);
        String k = pe.kommentar() == null ? "" : pe.kommentar().strip();
        p.setKommentar(k.isEmpty() ? null : kuerze(k, 500));
        return p;
    }

    /** Nächste Bestellnummer, Form "B-2026-10-0001": Jahr, Monat, laufende Nummer im Monat, ohne Lücken (Zähler unter Sperre). */
    private String naechsteNummer() {
        LocalDate heute = LocalDate.now(ZoneId.systemDefault());
        String schluessel = ZAEHLER_VORSATZ + DateTimeFormatter.ofPattern("yyyyMM").format(heute);
        DokumentnummerCounter z = zaehler.findByMonthKeyMitSperre(schluessel).orElseGet(() -> {
            DokumentnummerCounter neu = new DokumentnummerCounter();
            neu.setMonthKey(schluessel);
            neu.setCounter(0L);
            return neu;
        });
        long nr = z.getCounter() + 1;
        z.setCounter(nr);
        zaehler.save(z);
        return "B-%s-%s-%04d".formatted(DateTimeFormatter.ofPattern("yyyy").format(heute), DateTimeFormatter.ofPattern("MM").format(heute), nr);
    }

    BestellungDto dto(LieferantenBestellung b) {
        Map<Long, List<FertigungsunterlageDto>> anhaengeByPosition = positionAnhaenge.findByBestellungId(b.getId())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getPosition().getId(),
                        java.util.stream.Collectors.mapping(a -> new FertigungsunterlageDto(
                                a.getId(), a.getOriginalDateiname(), a.getErstelltAm(),
                                a.getDateigroesseBytes(), "/api/lieferanten-bestellungen/position-anhaenge/" + a.getId() + "/datei",
                                a.getQuellFertigungsunterlage() != null ? a.getQuellFertigungsunterlage().getId() : null),
                                java.util.stream.Collectors.toList())));
        List<BestellungDto.Position> ps = b.getPositionen().stream()
                .map(p -> positionDto(p, anhaengeByPosition.getOrDefault(p.getId(), List.of())))
                .toList();
        BigDecimal summe = ps.stream().map(BestellungDto.Position::summe).filter(s -> s != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        int geliefert = (int) ps.stream().filter(p -> p.geliefertAm() != null).count();
        String lieferstand = switch (b.getStatus()) {
            case ENTWURF -> "ENTWURF";
            case STORNIERT -> "STORNIERT";
            case BESTELLT -> geliefert == 0 ? "BESTELLT" : geliefert == ps.size() ? "GELIEFERT" : "TEILWEISE";
        };
        Integer tage = b.getBestelltAm() == null ? null : (int) ChronoUnit.DAYS.between(b.getBestelltAm().toLocalDate(), LocalDate.now());
        boolean ueberfaellig = b.getStatus() == BestellStatus.BESTELLT && !lieferstand.equals("GELIEFERT") && tage != null && tage >= UEBERFAELLIG_NACH_TAGEN;
        List<String> projektNamen = ps.stream().map(BestellungDto.Position::projektName).filter(n -> n != null).distinct().toList();
        Lieferanten l = b.getLieferant();
        return new BestellungDto(b.getId(), b.getBestellnummer(), b.getStatus(), lieferstand, l.getId(), l.getLieferantenname(),
                l.getKundenEmails() == null ? List.of() : List.copyOf(l.getKundenEmails()), b.getNotiz(), b.getErstelltAm(), b.getErstelltVon(),
                b.getBestelltAm(), b.getBestelltVon(), b.getBestelltPer(), b.getStorniertAm(), b.getStornoGrund(), summe, ps.size(), geliefert, tage,
                ueberfaellig, UEBERFAELLIG_NACH_TAGEN, projektNamen, ps);
    }

    private BestellungDto.Position positionDto(BestellPosition p, List<FertigungsunterlageDto> anhaenge) {
        BigDecimal summe = p.getEinzelpreis() == null ? null : p.getEinzelpreis().multiply(p.getMenge()).setScale(2, RoundingMode.HALF_UP);
        return new BestellungDto.Position(p.getId(), p.getArtikel() == null ? null : p.getArtikel().getId(), p.getArtikelnummer(), p.getBezeichnung(),
                p.getMenge(), p.getEinheit(), p.getEinzelpreis(), summe, p.getHerkunft(), p.getProjekt() == null ? null : p.getProjekt().getId(),
                p.getProjekt() == null ? null : projektName(p.getProjekt()), p.getArtikelInProjektId(), p.getKommentar(), p.getGeliefertAm(), anhaenge);
    }

    private LieferantenBestellung finde(Long id) {
        return bestellungen.findById(id).orElseThrow(() -> fehler(HttpStatus.NOT_FOUND, "Diese Bestellung gibt es nicht."));
    }

    private static void verlangeEntwurf(LieferantenBestellung b, String text) {
        if (b.getStatus() != BestellStatus.ENTWURF) {
            throw fehler(HttpStatus.CONFLICT, text);
        }
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

    private static String einheit(String gewuenscht, Artikel a) {
        if (gewuenscht == null || gewuenscht.isBlank()) {
            return LagerService.einheit(a);
        }
        String e = gewuenscht.strip();
        if (!EINHEITEN.contains(e)) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Einheit \"" + kuerze(e, 20) + "\" kennt das Programm nicht (erlaubt: Stk, m, m², kg, l).");
        }
        return e;
    }

    private static String zahl(BigDecimal z) {
        return z.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String kuerze(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String aktuellerBenutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || "anonymousUser".equals(name) ? null : kuerze(name, 120);
    }

    private static ResponseStatusException fehler(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
