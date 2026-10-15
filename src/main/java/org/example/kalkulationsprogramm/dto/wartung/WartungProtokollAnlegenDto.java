package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Eingabe zum Anlegen eines Protokolls. Ohne {@code datum} gilt heute, ohne
 * {@code intervallMonate} das Intervall der Tür bzw. das kürzeste Standard-Intervall der Module.
 */
public record WartungProtokollAnlegenDto(
        @NotNull(message = "Bitte einen Kunden wählen.") Long kundeId,
        Long objektId,
        Long tuerId,
        Long projektId,
        Long technikerId,
        @Size(max = 200, message = "Der Titel ist zu lang (höchstens 200 Zeichen).") String titel,
        LocalDate datum,
        @Min(value = 1, message = "Das Prüfintervall muss mindestens 1 Monat betragen.")
        @Max(value = 120, message = "Das Prüfintervall darf höchstens 120 Monate betragen.") Integer intervallMonate,
        /** Pflicht, außer es gibt eine Vorlage: Dann kommen die Module aus der früheren Prüfung. */
        @Valid List<WartungModulAuswahlDto> module,
        @Size(max = 10000, message = "Die Bemerkung ist zu lang (höchstens 10.000 Zeichen).") String bemerkungen,
        @Size(max = 200, message = "Der Name des Ansprechpartners ist zu lang.") String ansprechpartnerName,
        /** Eine frühere, abgeschlossene Prüfung derselben Wartung als Vorlage (optional). Sie bleibt unverändert. */
        Long vorlageProtokollId,
        /** Bei einer Vorlage: Ergebnisse, Bemerkungen und Datenfeld-Werte übernehmen (ohne Angabe: ja). */
        Boolean werteUebernehmen
) { }
