package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Zeile einer {@link LieferantenBestellung}. Bezeichnung, Artikelnummer und Einheit sind als Abschrift gespeichert, damit die Bestellung auch
 * lesbar bleibt, wenn der Artikel später umbenannt wird.
 */
@Entity
@Table(name = "bestell_position")
@Getter
@Setter
public class BestellPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bestellung_id", nullable = false)
    private LieferantenBestellung bestellung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id")
    private Artikel artikel;

    @Column(length = 64)
    private String artikelnummer;

    @Column(nullable = false, length = 500)
    private String bezeichnung;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal menge;

    @Column(nullable = false, length = 20)
    private String einheit;

    /** Preis je Einheit laut Lieferant; leer, wenn keiner bekannt ist. */
    @Column(precision = 19, scale = 4)
    private BigDecimal einzelpreis;

    @Enumerated(EnumType.STRING)
    // VARCHAR statt ENUM: Die Spalte ist per Flyway als VARCHAR angelegt, Hibernate 6 erwartet sonst auf MySQL den Typ ENUM und verweigert den Start.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private BestellHerkunft herkunft = BestellHerkunft.FREI;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    /** Die Materialposition im Projekt, aus der diese Zeile entstand (wird beim Bestellen als bestellt markiert). */
    @Column(name = "artikel_in_projekt_id")
    private Long artikelInProjektId;

    @Column(length = 500)
    private String kommentar;

    @Column(name = "geliefert_am")
    private LocalDate geliefertAm;
}
