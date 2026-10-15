package org.example.kalkulationsprogramm.service.wartung;

/** Eine Prüfung wurde abgeschlossen (nicht: aus dem Archiv wiederhergestellt). Anlass, den Bericht automatisch im Projekt abzulegen. */
public record WartungProtokollAbgeschlossen(Long protokollId) { }
