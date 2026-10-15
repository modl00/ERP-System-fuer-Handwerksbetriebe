package org.example.kalkulationsprogramm.dto.wartung;

import java.time.LocalDateTime;

/** Der als PDF im Projekt abgelegte Bericht einer abgeschlossenen Prüfung. */
public record WartungBerichtDto(Long dokumentId, String dateiname, String url, LocalDateTime erstelltAm) { }
