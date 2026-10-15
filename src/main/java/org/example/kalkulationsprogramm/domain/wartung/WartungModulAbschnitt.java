package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Überschrift, unter der Prüfpunkte oder Datenfelder eines Moduls zusammengefasst sind. */
@Entity
@Getter
@Setter
public class WartungModulAbschnitt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "modul_id", nullable = false)
    private Long modulId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WartungBereich bereich;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false)
    private int sortierung;
}
