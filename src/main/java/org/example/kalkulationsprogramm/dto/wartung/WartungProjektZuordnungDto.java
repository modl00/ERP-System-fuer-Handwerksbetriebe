package org.example.kalkulationsprogramm.dto.wartung;

/** Ordnet eine Wartung einem Projekt zu; {@code projektId = null} trennt sie vom Projekt. */
public record WartungProjektZuordnungDto(Long projektId) { }
