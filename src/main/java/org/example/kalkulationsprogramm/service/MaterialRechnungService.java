package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.MaterialRechnungZuordnung;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.Position;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.Rechnung;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.Zeile;
import org.example.kalkulationsprogramm.dto.Material.MaterialAbrechnungDto.ZeileEingabe;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.MaterialRechnungZuordnungRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Material "kommt per Rechnung" auflösen (Issue #99, Stufe 1): ordnet den Betrag einer Eingangsrechnung, den ein Projekt trägt, den geplanten
 * Materialpositionen des Projekts zu (oder als "Material ohne Plan" mit Beschreibung) und zeigt Plan gegen Rechnung mit Markierung bei Abweichung.
 *
 * <p><b>Die Projektkosten ändern sich dadurch nicht.</b> Sie kommen weiter aus den Rechnungsanteilen; geplantes Material, das nicht aus dem Lager kommt, zählt in der
 * Nachkalkulation nicht mit (siehe Projektansicht). Die Zuordnung macht nur sichtbar, welche Rechnung welches Material bezahlt hat und ob Plan und Rechnung zusammenpassen.
 * Beträge sind netto, wie der Anteil der Rechnung in der Projektansicht.
 */
@Service
@RequiredArgsConstructor
public class MaterialRechnungService {

    public static final String SCHWELLE_SCHLUESSEL = "material.abweichung.schwelle-prozent";
    public static final int SCHWELLE_STANDARD = 5;

    private static final BigDecimal TOLERANZ = new BigDecimal("0.01");
    private static final int MAX_ZEILEN = 200;

    private final MaterialRechnungZuordnungRepository zuordnungen;
    private final LieferantDokumentProjektAnteilRepository anteile;
    private final ArtikelInProjektRepository positionen;
    private final ProjektRepository projekte;
    private final ProjektMapper mapper;
    private final SystemSettingsService einstellungen;

    // ------------------------------------------------------------------ Lesen

    @Transactional(readOnly = true)
    public MaterialAbrechnungDto uebersicht(Long projektId) {
        verlangeProjekt(projektId);
        int schwelle = schwelleProzent();
        Map<Long, List<LieferantDokumentProjektAnteil>> proDokument = anteileJeDokument(projektId);
        List<MaterialRechnungZuordnung> links = zuordnungen.findByProjektId(projektId).stream()
                .filter(z -> proDokument.containsKey(z.getDokument().getId()))
                .toList();

        List<Rechnung> rechnungen = new ArrayList<>();
        for (List<LieferantDokumentProjektAnteil> liste : proDokument.values()) {
            LieferantDokument dok = liste.get(0).getDokument();
            BigDecimal anteil = liste.stream().map(MaterialRechnungService::anteilBetrag).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Zeile> zeilen = links.stream().filter(z -> z.getDokument().getId().equals(dok.getId()))
                    .map(z -> new Zeile(z.getId(), z.getArtikelInProjekt() == null ? null : z.getArtikelInProjekt().getId(),
                            z.getArtikelInProjekt() == null ? null : LagerService.bezeichnung(z.getArtikelInProjekt().getArtikel()), z.getBetrag(), z.getBeschreibung()))
                    .toList();
            BigDecimal zugeordnet = zeilen.stream().map(Zeile::betrag).reduce(BigDecimal.ZERO, BigDecimal::add);
            var gd = dok.getGeschaeftsdaten();
            rechnungen.add(new Rechnung(dok.getId(), gd == null ? null : gd.getDokumentNummer(), gd == null ? null : gd.getDokumentDatum(),
                    dok.getLieferant() == null ? null : dok.getLieferant().getId(), dok.getLieferant() == null ? null : dok.getLieferant().getLieferantenname(),
                    anteil, zugeordnet, anteil.subtract(zugeordnet), zeilen));
        }
        rechnungen.sort(Comparator.comparing((Rechnung r) -> r.dokumentDatum() == null ? LocalDate.MIN : r.dokumentDatum()).reversed());

        Map<Long, String> nummern = new LinkedHashMap<>();
        rechnungen.forEach(r -> nummern.put(r.dokumentId(), r.dokumentNummer() == null ? "ohne Nummer" : r.dokumentNummer()));
        List<Position> ps = new ArrayList<>();
        int offen = 0;
        BigDecimal offenGeplant = BigDecimal.ZERO;
        for (ArtikelInProjekt aip : positionen.findByProjekt_Id(projektId)) {
            if (aip.isAusLager() || aip.getArtikel() == null) {
                continue;
            }
            List<MaterialRechnungZuordnung> meine = links.stream().filter(z -> z.getArtikelInProjekt() != null && z.getArtikelInProjekt().getId().equals(aip.getId())).toList();
            BigDecimal geplant = mapper.geplanterGesamtpreis(aip);
            BigDecimal ist = meine.isEmpty() ? null : meine.stream().map(MaterialRechnungZuordnung::getBetrag).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prozent = null;
            String abweichung = null;
            if (ist != null && geplant != null && geplant.signum() > 0) {
                prozent = ist.subtract(geplant).multiply(BigDecimal.valueOf(100)).divide(geplant, 1, RoundingMode.HALF_UP);
                if (prozent.abs().compareTo(BigDecimal.valueOf(schwelle)) > 0) {
                    abweichung = prozent.signum() > 0 ? "TEURER" : "GUENSTIGER";
                }
            }
            if (ist == null) {
                offen++;
                offenGeplant = offenGeplant.add(geplant == null ? BigDecimal.ZERO : geplant);
            }
            ps.add(new Position(aip.getId(), aip.getArtikel().getId(), aip.getArtikel().getArtikelnummer(), LagerService.bezeichnung(aip.getArtikel()),
                    mapper.geplanteMenge(aip), LagerService.einheit(aip.getArtikel()), aip.getLieferant() == null ? null : aip.getLieferant().getId(),
                    aip.getLieferant() == null ? null : aip.getLieferant().getLieferantenname(), aip.isBestellt(), geplant, ist, prozent, abweichung,
                    meine.stream().map(z -> nummern.get(z.getDokument().getId())).distinct().toList()));
        }
        ps.sort(Comparator.comparing((Position p) -> p.ist() != null).thenComparing(p -> p.bezeichnung().toLowerCase(Locale.ROOT)));
        return new MaterialAbrechnungDto(schwelle, rechnungen, ps, offen, offenGeplant);
    }

    // ------------------------------------------------------------------ Schreiben

    /** Ersetzt die Zuordnung einer Rechnung zum Projektmaterial. Eine leere Liste hebt sie auf. */
    @Transactional
    public MaterialAbrechnungDto speichern(Long projektId, Long dokumentId, List<ZeileEingabe> eingabe) {
        Projekt projekt = verlangeProjekt(projektId);
        List<LieferantDokumentProjektAnteil> liste = anteileJeDokument(projektId).get(dokumentId);
        if (liste == null || liste.isEmpty()) {
            throw fehler(HttpStatus.BAD_REQUEST, "Diese Rechnung ist dem Projekt nicht zugeordnet.");
        }
        LieferantDokument dokument = liste.get(0).getDokument();
        BigDecimal anteil = liste.stream().map(MaterialRechnungService::anteilBetrag).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ZeileEingabe> zeilen = eingabe == null ? List.of() : eingabe;
        if (zeilen.size() > MAX_ZEILEN) {
            throw fehler(HttpStatus.BAD_REQUEST, "Eine Rechnung lässt sich auf höchstens " + MAX_ZEILEN + " Zeilen aufteilen.");
        }

        List<MaterialRechnungZuordnung> neu = new ArrayList<>();
        Set<Long> verwendet = new HashSet<>();
        BigDecimal summe = BigDecimal.ZERO;
        String benutzer = aktuellerBenutzer();
        for (ZeileEingabe z : zeilen) {
            if (z == null || z.betrag() == null || z.betrag().signum() <= 0) {
                throw fehler(HttpStatus.BAD_REQUEST, "Bei jeder Zeile muss ein Betrag größer als 0 stehen.");
            }
            BigDecimal betrag = z.betrag().setScale(2, RoundingMode.HALF_UP);
            MaterialRechnungZuordnung m = new MaterialRechnungZuordnung();
            m.setDokument(dokument);
            m.setProjekt(projekt);
            m.setBetrag(betrag);
            m.setZugeordnetAm(LocalDateTime.now());
            m.setZugeordnetVon(benutzer);
            if (z.artikelInProjektId() != null) {
                ArtikelInProjekt aip = positionen.findById(z.artikelInProjektId())
                        .orElseThrow(() -> fehler(HttpStatus.BAD_REQUEST, "Eine der Materialpositionen gibt es nicht (mehr)."));
                if (aip.getProjekt() == null || !aip.getProjekt().getId().equals(projektId)) {
                    throw fehler(HttpStatus.BAD_REQUEST, "Eine Materialposition gehört nicht zu diesem Projekt.");
                }
                if (aip.isAusLager()) {
                    throw fehler(HttpStatus.BAD_REQUEST, "\"" + LagerService.bezeichnung(aip.getArtikel()) + "\" kam aus dem Lager und wird nicht per Rechnung bezahlt.");
                }
                if (!verwendet.add(aip.getId())) {
                    throw fehler(HttpStatus.BAD_REQUEST, "Die Position \"" + LagerService.bezeichnung(aip.getArtikel()) + "\" steht doppelt in der Zuordnung.");
                }
                m.setArtikelInProjekt(aip);
            } else {
                String text = z.beschreibung() == null ? "" : z.beschreibung().strip();
                if (text.length() < 3) {
                    throw fehler(HttpStatus.BAD_REQUEST, "Bitte bei \"Material ohne Plan\" kurz beschreiben, was gekauft wurde.");
                }
                m.setBeschreibung(text.length() > 255 ? text.substring(0, 255) : text);
            }
            summe = summe.add(betrag);
            neu.add(m);
        }
        if (summe.compareTo(anteil.add(TOLERANZ)) > 0) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Zeilen ergeben " + euro(summe) + ", die Rechnung hat für dieses Projekt aber nur " + euro(anteil) + ".");
        }
        zuordnungen.deleteAll(zuordnungen.findByDokument_IdAndProjekt_Id(dokumentId, projektId));
        zuordnungen.flush();
        zuordnungen.saveAll(neu);
        return uebersicht(projektId);
    }

    // ------------------------------------------------------------------ Einstellung

    /** Ab wie vielen Prozent Abweichung zwischen Plan und Rechnung markiert wird (Vorgabe 5). */
    public int schwelleProzent() {
        String wert = einstellungen.get(SCHWELLE_SCHLUESSEL, String.valueOf(SCHWELLE_STANDARD));
        try {
            int p = Integer.parseInt(wert == null ? "" : wert.strip());
            return p >= 0 && p <= 100 ? p : SCHWELLE_STANDARD;
        } catch (NumberFormatException e) {
            return SCHWELLE_STANDARD;
        }
    }

    public int setzeSchwelle(int prozent) {
        if (prozent < 0 || prozent > 100) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Schwelle muss zwischen 0 und 100 Prozent liegen.");
        }
        einstellungen.save(SCHWELLE_SCHLUESSEL, String.valueOf(prozent), "Abweichung zwischen geplantem Materialpreis und Rechnung, ab der markiert wird (Prozent)");
        return prozent;
    }

    // ------------------------------------------------------------------ intern

    private Projekt verlangeProjekt(Long projektId) {
        return projekte.findById(projektId).orElseThrow(() -> fehler(HttpStatus.NOT_FOUND, "Das Projekt gibt es nicht."));
    }

    private Map<Long, List<LieferantDokumentProjektAnteil>> anteileJeDokument(Long projektId) {
        return anteile.findByProjektIdEager(projektId).stream()
                .filter(a -> a.getDokument() != null)
                .collect(Collectors.groupingBy(a -> a.getDokument().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Der Betrag (netto), den ein Projekt von der Rechnung trägt, genau wie die Projektansicht ihn zeigt: bei Prozent aus dem Nettobetrag der Rechnung,
     * sonst der gespeicherte Betrag.
     */
    static BigDecimal anteilBetrag(LieferantDokumentProjektAnteil a) {
        var gd = a.getDokument() == null ? null : a.getDokument().getGeschaeftsdaten();
        if (gd != null && gd.getBetragNetto() != null && a.getProzent() != null) {
            return gd.getBetragNetto().multiply(BigDecimal.valueOf(a.getProzent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return a.getBerechneterBetrag() == null ? BigDecimal.ZERO : a.getBerechneterBetrag();
    }

    private static String euro(BigDecimal b) {
        return String.format(Locale.GERMANY, "%,.2f €", b);
    }

    private static String aktuellerBenutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || "anonymousUser".equals(name) ? null : (name.length() > 120 ? name.substring(0, 120) : name);
    }

    private static ResponseStatusException fehler(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
