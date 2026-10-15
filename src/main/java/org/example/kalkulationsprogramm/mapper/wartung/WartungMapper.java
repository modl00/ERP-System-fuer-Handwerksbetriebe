package org.example.kalkulationsprogramm.mapper.wartung;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulAuswahlDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungObjektDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungTuerDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Wandelt die Wartungs-Entities in DTOs um. Die Methoden greifen auf Lazy-Beziehungen zu und müssen
 * deshalb innerhalb der Transaktion des aufrufenden Service laufen.
 */
@Component
public class WartungMapper {

    public WartungModulDto toDto(WartungModul modul, List<WartungModulUntertyp> untertypen, int anzahlPruefpunkte) {
        return new WartungModulDto(
                modul.getId(),
                modul.getModulKey(),
                modul.getName(),
                modul.getBeschreibung(),
                modul.getIcon(),
                modul.getKategorie(),
                modul.isSystemModul(),
                modul.getStandardIntervallMonate(),
                modul.getAndockbarAnBasisModulIds().stream().sorted().toList(),
                untertypen.stream().map(u -> new WartungModulDto.Untertyp(u.getSchluessel(), u.getLabel())).toList(),
                anzahlPruefpunkte);
    }

    public WartungTuerDto toDto(WartungTuer tuer) {
        List<WartungModulAuswahlDto> module = tuer.getModule().stream()
                .sorted(Comparator.comparing(WartungModulAuswahl::getModulId))
                .map(m -> new WartungModulAuswahlDto(m.getModulId(), m.getUntertypSchluessel()))
                .toList();
        return new WartungTuerDto(
                tuer.getId(),
                tuer.getObjekt().getId(),
                tuer.getBezeichnung(),
                tuer.getNotizen(),
                tuer.getIntervallMonate(),
                tuer.getNaechsteFaelligkeit(),
                tuer.isAktiv(),
                module,
                tuer.getProjekt() == null ? null : tuer.getProjekt().getId(),
                tuer.getProjekt() == null ? null : projektName(tuer.getProjekt()));
    }

    public WartungObjektDto toDto(WartungObjekt objekt, List<WartungTuerDto> tueren) {
        return new WartungObjektDto(
                objekt.getId(),
                objekt.getKunde().getId(),
                objekt.getKunde().getName(),
                objekt.getBezeichnung(),
                objekt.getStrasse(),
                objekt.getPlz(),
                objekt.getOrt(),
                objekt.getAnsprechpartnerName(),
                objekt.getAnsprechpartnerTelefon(),
                objekt.getNotizen(),
                objekt.isAktiv(),
                tueren);
    }

    public WartungProtokollDto toDto(
            WartungProtokoll protokoll,
            List<WartungProtokollAbschnitt> abschnitte,
            List<WartungProtokollPruefpunkt> pruefpunkte,
            List<WartungProtokollDatenfeld> datenfelder) {
        return toDto(protokoll, abschnitte, pruefpunkte, datenfelder, null);
    }

    public WartungProtokollDto toDto(
            WartungProtokoll protokoll,
            List<WartungProtokollAbschnitt> abschnitte,
            List<WartungProtokollPruefpunkt> pruefpunkte,
            List<WartungProtokollDatenfeld> datenfelder,
            WartungProtokollDto.Bericht bericht) {
        Kunde kunde = protokoll.getKunde();
        Projekt projekt = protokoll.getProjekt();
        Mitarbeiter techniker = protokoll.getTechniker();
        WartungStatus status = protokoll.getStatus();

        List<WartungProtokollDto.Modul> module = protokoll.getModule().stream()
                .sorted(Comparator.comparing(WartungProtokollModul::getModulId))
                .map(m -> new WartungProtokollDto.Modul(
                        m.getModulId(), m.getModulKey(), m.getModulName(), m.getUntertypSchluessel(), m.getUntertypLabel()))
                .toList();

        return new WartungProtokollDto(
                protokoll.getId(),
                protokoll.getVersion(),
                protokoll.getNummer(),
                new WartungProtokollDto.Bezug(kunde.getId(), kunde.getName()),
                protokoll.getObjekt() == null ? null
                        : new WartungProtokollDto.Bezug(protokoll.getObjekt().getId(), protokoll.getObjekt().getBezeichnung()),
                protokoll.getTuer() == null ? null
                        : new WartungProtokollDto.Bezug(protokoll.getTuer().getId(), protokoll.getTuer().getBezeichnung()),
                projekt == null ? null : new WartungProtokollDto.Bezug(projekt.getId(), projektName(projekt)),
                techniker == null ? null : new WartungProtokollDto.Bezug(techniker.getId(), mitarbeiterName(techniker)),
                protokoll.getTitel(),
                protokoll.getProtokollTyp(),
                protokoll.getDatum(),
                protokoll.getIntervallMonate(),
                protokoll.getNaechsteWartung(),
                status,
                status.istInhaltBearbeitbar(),
                status.erlaubteZiele().stream().sorted().toList(),
                protokoll.getBemerkungen(),
                protokoll.getAnsprechpartnerName(),
                new WartungProtokollDto.Unterschrift(protokoll.getUnterschriftTechniker(), protokoll.getUnterschriftTechnikerName()),
                new WartungProtokollDto.Unterschrift(protokoll.getUnterschriftKunde(), protokoll.getUnterschriftKundeName()),
                protokoll.getAbgeschlossenAm(),
                protokoll.getVorlageProtokollId(),
                bericht,
                protokoll.getAngelegtAm(),
                protokoll.getGeaendertAm(),
                module,
                abschnitte.stream()
                        .map(a -> new WartungProtokollDto.Abschnitt(a.getId(), a.getBereich(), a.getLabel(), a.getSortierung()))
                        .toList(),
                pruefpunkte.stream()
                        .map(p -> new WartungProtokollDto.Pruefpunkt(p.getId(), p.getAbschnittId(), p.getModulKey(), p.getLabel(),
                                p.getErgebnis(), p.getBemerkung(), p.getVorherigesErgebnis(), p.getSortierung()))
                        .toList(),
                datenfelder.stream()
                        .map(d -> new WartungProtokollDto.Datenfeld(d.getId(), d.getAbschnittId(), d.getModulKey(), d.getLabel(),
                                d.getFeldtyp(), optionenListe(d.getOptionen()), d.getWert(), d.getSortierung()))
                        .toList(),
                statistik(pruefpunkte));
    }

    /** Zählt die Bewertungen der Prüfpunkte für Fortschrittsbalken und Mängelzähler. */
    public WartungProtokollDto.Statistik statistik(List<WartungProtokollPruefpunkt> pruefpunkte) {
        int io = 0;
        int nio = 0;
        int na = 0;
        for (WartungProtokollPruefpunkt punkt : pruefpunkte) {
            WartungErgebnis ergebnis = punkt.getErgebnis();
            if (ergebnis == WartungErgebnis.IO) {
                io++;
            } else if (ergebnis == WartungErgebnis.NIO) {
                nio++;
            } else if (ergebnis == WartungErgebnis.NA) {
                na++;
            }
        }
        return new WartungProtokollDto.Statistik(pruefpunkte.size(), io + nio + na, io, nio, na);
    }

    /** Optionen einer Auswahl liegen zeilenweise in einem Textfeld. */
    public static List<String> optionenListe(String optionen) {
        if (optionen == null || optionen.isBlank()) {
            return List.of();
        }
        return optionen.lines().map(String::trim).filter(zeile -> !zeile.isEmpty()).toList();
    }

    private static String mitarbeiterName(Mitarbeiter mitarbeiter) {
        String vorname = mitarbeiter.getVorname() == null ? "" : mitarbeiter.getVorname().trim();
        String nachname = mitarbeiter.getNachname() == null ? "" : mitarbeiter.getNachname().trim();
        return (vorname + " " + nachname).trim();
    }

    /** Anzeigename eines Projekts: "Auftragsnummer – Bauvorhaben", sonst was vorhanden ist. */
    public static String projektName(Projekt projekt) {
        String bauvorhaben = projekt.getBauvorhaben();
        String nummer = projekt.getAuftragsnummer();
        if (bauvorhaben != null && !bauvorhaben.isBlank()) {
            return nummer == null || nummer.isBlank() ? bauvorhaben : nummer + " – " + bauvorhaben;
        }
        return nummer == null ? "Projekt " + projekt.getId() : nummer;
    }
}
