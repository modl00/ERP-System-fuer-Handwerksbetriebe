package org.example.kalkulationsprogramm.dto.Lager;

/** Kennzahlen für Hinweise: wie viele Artikel im Lager geführt werden und wo etwas zu tun ist. */
public record LagerZusammenfassungDto(int anzahlArtikel, int unterMindestbestand, int negativ) {
}
