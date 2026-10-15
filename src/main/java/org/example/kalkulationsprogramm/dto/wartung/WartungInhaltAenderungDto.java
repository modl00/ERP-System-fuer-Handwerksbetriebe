package org.example.kalkulationsprogramm.dto.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;

/**
 * Antwort auf eine einzelne Änderung im Protokoll (ein Prüfpunkt oder ein Datenfeld). Bewusst klein,
 * denn im Feldeinsatz wird bei jedem Antippen gespeichert. Genau eines von {@code pruefpunkt} und
 * {@code datenfeld} ist gesetzt. Der Status ist dabei, weil die erste Änderung ein offenes Protokoll
 * automatisch auf "In Arbeit" stellt.
 */
public record WartungInhaltAenderungDto(
        WartungStatus status,
        WartungProtokollDto.Statistik statistik,
        WartungProtokollDto.Pruefpunkt pruefpunkt,
        WartungProtokollDto.Datenfeld datenfeld
) { }
