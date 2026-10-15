package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.Size;

/** Wert eines Datenfelds. Leer oder {@code null} löscht den Wert. */
public record WartungDatenfeldWertDto(
        @Size(max = 5000, message = "Der Wert ist zu lang (höchstens 5.000 Zeichen).") String wert
) { }
