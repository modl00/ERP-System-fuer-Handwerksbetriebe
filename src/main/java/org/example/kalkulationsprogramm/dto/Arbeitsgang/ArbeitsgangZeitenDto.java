package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import java.math.BigDecimal;

/**
 * Normzeiten eines Arbeitsgangs in Minuten.
 *
 * @param ruestzeitMinuten        einmalige Rüstzeit je Los (Maschine einrichten, Material holen); leer = keine Angabe
 * @param bearbeitungszeitMinuten Bearbeitungszeit je Einheit (je Stück, je lfm ...); leer = keine Angabe
 */
public record ArbeitsgangZeitenDto(BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten) {
}
