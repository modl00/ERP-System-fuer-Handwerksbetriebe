package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Bestellung an einen Lieferanten (Issue #86). Sie darf Positionen aus mehreren Projekten, dem Lager und dem freien Artikelstamm enthalten.
 * Die Bestellnummer wird erst beim Bestellen vergeben; danach ändert sich die Bestellung nicht mehr (nur Lieferstand und Storno).
 */
@Entity
@Table(name = "lieferanten_bestellung")
@Getter
@Setter
public class LieferantenBestellung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "bestellnummer", length = 30, unique = true)
    private String bestellnummer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    @Enumerated(EnumType.STRING)
    // VARCHAR statt ENUM: Die Spalte ist per Flyway als VARCHAR angelegt, Hibernate 6 erwartet sonst auf MySQL den Typ ENUM und verweigert den Start.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BestellStatus status = BestellStatus.ENTWURF;

    @Column(length = 1000)
    private String notiz;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "erstellt_von", length = 120)
    private String erstelltVon;

    @Column(name = "bestellt_am")
    private LocalDateTime bestelltAm;

    @Column(name = "bestellt_von", length = 120)
    private String bestelltVon;

    /** EMAIL oder ANDERS (zum Beispiel telefonisch). */
    @Column(name = "bestellt_per", length = 20)
    private String bestelltPer;

    @Column(name = "storniert_am")
    private LocalDateTime storniertAm;

    @Column(name = "storno_grund", length = 500)
    private String stornoGrund;

    @OneToMany(mappedBy = "bestellung", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BestellPosition> positionen = new ArrayList<>();
}
