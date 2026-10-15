package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld;

/** Formel eines Arbeitsgangs: Eingabefelder und Formeln für Bearbeitungs- und Rüstzeit (Minuten). Eine leere Bearbeitungsformel entfernt die Formel. */
public record ArbeitsgangFormelDto(List<Feld> felder, String bearbeitungFormel, String ruestFormel) {

    /** Für die Testrechnung: dieselbe Formel mit Werten für die Felder (leer = Standardwerte). */
    public record Test(List<Feld> felder, String bearbeitungFormel, String ruestFormel, Map<String, BigDecimal> werte) {
    }

    /** Für die Berechnung mit der gespeicherten Formel eines Arbeitsgangs. */
    public record Werte(Map<String, BigDecimal> werte) {
    }

    /** Ergebnis der Rechnung in Minuten. */
    public record Ergebnis(BigDecimal ruestzeitMinuten, BigDecimal bearbeitungszeitMinuten) {
    }
}
