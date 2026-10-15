package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Gewicht und Außenfläche im Artikelstamm in einem Zug ergänzen (Issue #252). Betroffen sind nur Artikel mit gewählter Form, <b>ohne jede Gewichtsangabe</b>, deren Maße
 * für den Rechner ({@link ProfilRechner}) reichen. Erst die Vorschau (nichts wird geschrieben), dann die Auswahl; beim Übernehmen rechnet der Server noch einmal und
 * überschreibt <b>nie</b> einen Artikel, der inzwischen ein Gewicht hat. Die Herkunft wird als „berechnet“ mit der benutzten Dichte vermerkt.
 */
@Service
@RequiredArgsConstructor
public class ArtikelGewichtErgaenzenService {

    static final int MAX_ZEILEN = 500;
    private static final BigDecimal GRENZE_DICHTE = new BigDecimal("30");

    private final ArtikelRepository artikel;

    /** {@code status}: BERECHENBAR oder MASSE_FEHLEN (dann sagt {@code hinweis} was fehlt). Beim Blech sind die Werte je m², sonst je Meter. */
    public record Zeile(Long id, String bezeichnung, String form, String status, String hinweis, BigDecimal kg, BigDecimal flaeche, String einheit, BigDecimal dichte) { }

    public record Vorschau(long gesamt, List<Zeile> zeilen, boolean gekuerzt) { }

    public record Ergebnis(int ergaenzt, int uebersprungen) { }

    @Transactional(readOnly = true)
    public Vorschau vorschau() {
        long gesamt = artikel.zaehleOhneGewicht();
        List<Zeile> zeilen = new ArrayList<>();
        for (ArtikelWerkstoffe aw : artikel.findeOhneGewicht(PageRequest.of(0, MAX_ZEILEN))) {
            zeilen.add(zeile(aw));
        }
        // Berechenbare zuerst: Das sind die, mit denen etwas geschieht
        zeilen.sort((a, b) -> a.status().equals(b.status()) ? 0 : a.status().equals("BERECHENBAR") ? -1 : 1);
        return new Vorschau(gesamt, zeilen, gesamt > MAX_ZEILEN);
    }

    /** @return wie viele Artikel ergänzt und wie viele übersprungen wurden (hatten inzwischen ein Gewicht oder reichen die Maße nicht mehr) */
    @Transactional
    public Ergebnis uebernehme(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Artikel auswählen.");
        }
        if (ids.size() > MAX_ZEILEN) {
            throw new IllegalArgumentException("Bitte höchstens " + MAX_ZEILEN + " Artikel auf einmal übernehmen.");
        }
        int ok = 0;
        int uebersprungen = 0;
        for (Long id : ids.stream().distinct().toList()) {
            if (!(artikel.findById(id).orElse(null) instanceof ArtikelWerkstoffe aw) || hatGewicht(aw) || aw.getProfilform() == null) {
                uebersprungen++;
                continue;
            }
            Zeile z = zeile(aw);
            if (!"BERECHENBAR".equals(z.status())) {
                uebersprungen++;
                continue;
            }
            if (aw.getProfilform().istBlech()) {
                aw.setMassePerQm(z.kg());
                aw.setFlaecheJeQm(z.flaeche());
            } else {
                aw.setMasse(z.kg());
                aw.setMantelflaeche(z.flaeche());
            }
            aw.setGewichtHerkunft("BERECHNET");
            aw.setGewichtDichte(z.dichte().setScale(4, java.math.RoundingMode.HALF_UP));
            artikel.save(aw);
            ok++;
        }
        return new Ergebnis(ok, uebersprungen);
    }

    private static boolean hatGewicht(ArtikelWerkstoffe aw) {
        return aw.getMasse() != null || aw.getMassePerQm() != null || aw.getKgJeStueck() != null;
    }

    private static Zeile zeile(ArtikelWerkstoffe aw) {
        String name = aw.getProduktname() != null && !aw.getProduktname().isBlank() ? aw.getProduktname() : (aw.getExterneArtikelnummer() != null ? aw.getExterneArtikelnummer() : "Artikel " + aw.getId());
        BigDecimal dichte = aw.getWerkstoff() != null && aw.getWerkstoff().getDichte() != null && aw.getWerkstoff().getDichte().signum() > 0 && aw.getWerkstoff().getDichte().compareTo(GRENZE_DICHTE) <= 0
                ? aw.getWerkstoff().getDichte() : ProfilRechner.STAHL;
        String form = aw.getProfilform().getAnzeigename();
        try {
            ProfilRechner.Ergebnis e = ProfilRechner.rechne(aw.getProfilform(), aw.getDurchmesser(), aw.getHoehe(), aw.getBreite(), aw.getWandstaerke(), dichte);
            boolean blech = aw.getProfilform().istBlech();
            BigDecimal kg = blech ? e.kgJeQm() : e.kgJeMeter();
            BigDecimal flaeche = blech ? e.m2JeQm() : e.m2JeMeter();
            if (kg == null || kg.signum() <= 0) {
                return new Zeile(aw.getId(), name, form, "MASSE_FEHLEN", "Aus den Maßen ergibt sich kein Gewicht.", null, null, null, null);
            }
            return new Zeile(aw.getId(), name, form, "BERECHENBAR", null, kg, flaeche, blech ? "je m²" : "je Meter", dichte);
        } catch (IllegalArgumentException ex) {
            return new Zeile(aw.getId(), name, form, "MASSE_FEHLEN", ex.getMessage(), null, null, null, null);
        }
    }
}
