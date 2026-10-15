package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import java.math.BigDecimal;

/**
 * Die Maschine eines Arbeitsgangs: Name und Kosten je Stunde in Euro. Ohne Stundensatz nutzt der Arbeitsgang keine Maschine (die Angabe wird entfernt).
 */
public record ArbeitsgangMaschineDto(String bezeichnung, BigDecimal stundensatz) { }
