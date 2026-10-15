package org.example.kalkulationsprogramm.dto.Lager;

/** Ein Lagerort in der Liste. */
public record LagerortDto(Long id, String name, String beschreibung, boolean aktiv, int sortOrder) {
}
