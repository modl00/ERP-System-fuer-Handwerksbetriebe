package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Datei-Anhang einer {@link BestellPosition} (Issue #510).
 * Quelle 1: aus dem Artikelstamm uebernommen ({@code quellFertigungsunterlage} != null).
 * Quelle 2: direkt fuer diese Bestellposition hochgeladen ({@code quellFertigungsunterlage} == null).
 */
@Entity
@Table(name = "bestell_position_anhang")
@Getter
@Setter
public class BestellPositionAnhang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BestellPosition position;

    @Column(name = "original_dateiname", nullable = false, length = 500)
    private String originalDateiname;

    @Column(name = "gespeicherter_dateiname", nullable = false, length = 500)
    private String gespeicherterDateiname;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "dateigroesse_bytes")
    private Long dateigroesseBytes;

    /** Nicht null = aus Artikelstamm uebernommen; null = direkt hochgeladen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quell_fertigungsunterlage_id")
    private ArtikelFertigungsunterlage quellFertigungsunterlage;
}
