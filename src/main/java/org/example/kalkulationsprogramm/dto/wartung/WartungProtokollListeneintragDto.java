package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

import java.time.LocalDate;

/**
 * Ein Eintrag der Protokollliste. Bewusst ohne Unterschriften und Inhalte: Die Liste soll auch
 * bei vielen Protokollen schnell bleiben. Wird direkt per JPQL-Konstruktor gefüllt.
 */
public record WartungProtokollListeneintragDto(
        Long id,
        String nummer,
        String titel,
        String protokollTyp,
        LocalDate datum,
        LocalDate naechsteWartung,
        WartungStatus status,
        Long kundeId,
        String kundeName,
        Long objektId,
        String objektBezeichnung,
        Long tuerId,
        String tuerBezeichnung,
        long pruefpunkteGesamt,
        long pruefpunkteBewertet,
        long maengel
) { }
