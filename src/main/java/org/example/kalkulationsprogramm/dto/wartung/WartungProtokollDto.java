package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ein Protokoll mit allen Inhalten, so wie Detailansicht und Feldeinsatz es brauchen. */
public record WartungProtokollDto(
        Long id,
        Long version,
        String nummer,
        Bezug kunde,
        Bezug objekt,
        Bezug tuer,
        Bezug projekt,
        Bezug techniker,
        String titel,
        String protokollTyp,
        LocalDate datum,
        int intervallMonate,
        LocalDate naechsteWartung,
        WartungStatus status,
        /** Prüfpunkte, Datenfelder und Unterschriften dürfen nur bei true geändert werden. */
        boolean bearbeitbar,
        List<WartungStatus> moeglicheStatus,
        String bemerkungen,
        String ansprechpartnerName,
        Unterschrift unterschriftTechniker,
        Unterschrift unterschriftKunde,
        LocalDateTime abgeschlossenAm,
        /** Aus welcher früheren Prüfung dieser Feldeinsatz entstand, oder null. */
        Long vorlageProtokollId,
        /** Der im Projekt abgelegte PDF-Bericht, oder null. */
        Bericht bericht,
        LocalDateTime angelegtAm,
        LocalDateTime geaendertAm,
        List<Modul> module,
        List<Abschnitt> abschnitte,
        List<Pruefpunkt> pruefpunkte,
        List<Datenfeld> datenfelder,
        Statistik statistik
) {

    /** Verweis auf einen anderen Datensatz mit Anzeigename. */
    public record Bezug(Long id, String name) { }

    public record Bericht(Long dokumentId, String url, LocalDateTime erstelltAm) { }

    /** Bild ist eine PNG-Data-URL oder null, wenn (noch) nicht unterschrieben wurde. */
    public record Unterschrift(String bild, String name) { }

    public record Modul(Long modulId, String modulKey, String name, String untertypSchluessel, String untertypLabel) { }

    public record Abschnitt(Long id, WartungBereich bereich, String label, int sortierung) { }

    public record Pruefpunkt(
            Long id,
            Long abschnittId,
            String modulKey,
            String label,
            WartungErgebnis ergebnis,
            String bemerkung,
            WartungErgebnis vorherigesErgebnis,
            int sortierung
    ) { }

    public record Datenfeld(
            Long id,
            Long abschnittId,
            String modulKey,
            String label,
            WartungFeldtyp feldtyp,
            List<String> optionen,
            String wert,
            int sortierung
    ) { }

    /** Zählwerte für Fortschrittsbalken und Mängelzähler. */
    public record Statistik(int gesamt, int bewertet, int inOrdnung, int nichtInOrdnung, int nichtAnwendbar) { }
}
