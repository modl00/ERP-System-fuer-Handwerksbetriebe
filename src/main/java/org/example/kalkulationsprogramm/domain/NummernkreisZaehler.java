package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Der letzte vergebene Wert eines Nummernkreises in einem Zeitraum ({@code alle}, {@code 2026} oder {@code 2026-10}). */
@Getter
@Setter
@Entity
@Table(name = "nummernkreis_zaehler", uniqueConstraints = @UniqueConstraint(columnNames = { "art", "periode" }))
public class NummernkreisZaehler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "art", nullable = false, length = 40)
    private String art;

    @Column(name = "periode", nullable = false, length = 20)
    private String periode;

    @Column(name = "letzte", nullable = false)
    private long letzte;
}
