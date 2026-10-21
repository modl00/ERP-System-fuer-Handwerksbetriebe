package org.example.kalkulationsprogramm.dto.Bestellwesen;

import java.time.LocalDateTime;

/**
 * Anhang an einer Bestellposition oder Fertigungsunterlage am Artikelstamm.
 *
 * @param id                  Datenbank-ID des Anhangs
 * @param originalDateiname   Original-Dateiname wie hochgeladen
 * @param erstelltAm          Zeitstempel
 * @param dateigroesseBytes   Groesse der Datei in Bytes, kann null sein
 * @param url                 Download-URL
 * @param quellId             Bei aus Artikelstamm uebernommenen Anhaengen die ID der ArtikelFertigungsunterlage, sonst null
 */
public record FertigungsunterlageDto(Long id, String originalDateiname, LocalDateTime erstelltAm,
        Long dateigroesseBytes, String url, Long quellId) {
}
