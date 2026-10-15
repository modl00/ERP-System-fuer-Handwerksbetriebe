package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;

/**
 * Bewertung eines Prüfpunkts. Ergebnis und Bemerkung werden immer zusammen gesendet;
 * {@code ergebnis = null} setzt den Punkt wieder auf "nicht bewertet" zurück.
 */
public record WartungPruefpunktErgebnisDto(
        WartungErgebnis ergebnis,
        @Size(max = 10000, message = "Die Bemerkung ist zu lang (höchstens 10.000 Zeichen).") String bemerkung
) { }
