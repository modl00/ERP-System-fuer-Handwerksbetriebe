package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Zähler für Wartungsnummern, eine Zeile je Präfix und Jahr (zum Beispiel WA / 2026). */
@Entity
@Getter
@Setter
public class WartungNummernkreis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String praefix;

    @Column(nullable = false)
    private int jahr;

    @Column(name = "letzter_wert", nullable = false)
    private int letzterWert;
}
