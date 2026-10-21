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
 * Fertigungsunterlage (DXF, STEP, DWG, STP, DXB, PDF) eines Artikels.
 * Wird beim Bestellen des Artikels als Vorlage fuer Bestellpositions-Anhaenge vorgeschlagen.
 */
@Entity
@Table(name = "artikel_fertigungsunterlage")
@Getter
@Setter
public class ArtikelFertigungsunterlage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @Column(name = "original_dateiname", nullable = false, length = 500)
    private String originalDateiname;

    @Column(name = "gespeicherter_dateiname", nullable = false, length = 500)
    private String gespeicherterDateiname;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "dateigroesse_bytes")
    private Long dateigroesseBytes;
}
