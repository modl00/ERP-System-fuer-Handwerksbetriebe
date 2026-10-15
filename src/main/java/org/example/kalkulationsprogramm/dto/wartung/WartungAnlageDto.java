package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Eine Wartung (Anlage, Tür oder Tor) mit allem, was die Detailseite braucht: Zuordnung, Wartungsplan und die
 * Historie der bisherigen Prüfungen (Feldeinsätze). Die Historie ist nur lesend; alte Prüfungen werden nie verändert.
 */
public record WartungAnlageDto(
        Long id,
        String bezeichnung,
        String notizen,
        Integer intervallMonate,
        LocalDate naechsteFaelligkeit,
        boolean aktiv,
        Bezug kunde,
        Objekt objekt,
        /** Projekt der Wartung oder null. */
        Bezug projekt,
        List<Modul> module,
        /** Ein noch nicht abgeschlossener Feldeinsatz, falls es einen gibt: Dann wird kein zweiter gestartet. */
        Long offenerFeldeinsatzId,
        String offenerFeldeinsatzNummer,
        /** Die neueste abgeschlossene Prüfung: die Vorlage für die nächste. */
        Long letztePruefungId,
        List<Pruefung> historie
) {

    public record Bezug(Long id, String name) { }

    public record Objekt(Long id, String bezeichnung, String strasse, String plz, String ort) { }

    /** {@code bauartSchluessel} braucht der Start eines Feldeinsatzes ohne Vorlage, {@code bauart} ist die Anzeige. */
    public record Modul(Long modulId, String name, String bauart, String bauartSchluessel) { }

    /** Eine Prüfung der Historie. {@code pruefung} ist die laufende Nummer ab 1 (älteste zuerst). */
    public record Pruefung(
            Long id,
            int pruefung,
            String nummer,
            LocalDate datum,
            WartungStatus status,
            String technikerName,
            long pruefpunkteGesamt,
            long pruefpunkteBewertet,
            long maengel,
            Long vorlageProtokollId,
            Long berichtDokumentId,
            String berichtUrl
    ) { }
}
