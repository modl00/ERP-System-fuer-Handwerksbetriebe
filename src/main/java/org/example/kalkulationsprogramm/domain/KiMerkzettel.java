package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ein Satz, den sich die KI merken soll (zum Beispiel „Lieferant X liefert nur freitags“). Entsteht nur durch die Bestätigung eines Administrators. */
@Getter
@Setter
@Entity
@Table(name = "ki_merkzettel")
public class KiMerkzettel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "satz", nullable = false, length = 500)
    private String text;

    @Column(name = "angelegt_von", length = 120)
    private String angelegtVon;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;
}
