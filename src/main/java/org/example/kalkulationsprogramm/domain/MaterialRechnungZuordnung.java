package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

/**
 * Verknüpfung "Rechnung bezahlt dieses Projektmaterial" (Issue #99). Hängt am Dokument und am Projekt (nicht am Rechnungsanteil, der beim Bearbeiten der
 * Zuordnung neu angelegt wird). Ohne {@link #artikelInProjekt} ist es "Material ohne Plan" mit Beschreibung.
 */
@Entity
@Table(name = "material_rechnung_zuordnung")
@Getter
@Setter
public class MaterialRechnungZuordnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dokument_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LieferantDokument dokument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_in_projekt_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ArtikelInProjekt artikelInProjekt;

    /** Der tatsächlich bezahlte Betrag (netto) für diese Position. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal betrag;

    @Column(length = 255)
    private String beschreibung;

    @Column(name = "zugeordnet_am", nullable = false)
    private LocalDateTime zugeordnetAm = LocalDateTime.now();

    @Column(name = "zugeordnet_von", length = 120)
    private String zugeordnetVon;
}
