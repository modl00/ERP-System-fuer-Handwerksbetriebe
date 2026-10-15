package org.example.kalkulationsprogramm.dto.wartung;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

/** Eine Zeile im Verlauf eines Protokolls (#238). */
public record WartungVerlaufDto(LocalDateTime zeit, String benutzer, WartungStatus vonStatus, WartungStatus nachStatus, String grund) { }
