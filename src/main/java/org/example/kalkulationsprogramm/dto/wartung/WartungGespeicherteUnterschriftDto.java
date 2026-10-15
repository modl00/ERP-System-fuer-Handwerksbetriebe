package org.example.kalkulationsprogramm.dto.wartung;

import java.time.LocalDateTime;

/** Die gespeicherte Unterschrift des angemeldeten Mitarbeiters (Bild als PNG-Data-URL). */
public record WartungGespeicherteUnterschriftDto(String bild, String name, LocalDateTime geaendertAm) { }
