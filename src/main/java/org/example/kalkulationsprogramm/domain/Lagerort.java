package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ein Ort, an dem Ware lagert (Halle, Fahrzeug, Regal). Wird nie gelöscht, nur deaktiviert. */
@Entity
@Table(name = "lagerort")
@Getter
@Setter
public class Lagerort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120, unique = true)
    private String name;

    @Column(name = "beschreibung", length = 255)
    private String beschreibung;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
