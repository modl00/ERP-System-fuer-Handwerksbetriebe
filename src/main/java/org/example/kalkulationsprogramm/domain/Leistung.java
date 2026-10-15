package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Leistung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String bezeichnung;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verrechnungseinheit einheit;

    @Column(precision = 19, scale = 2)
    private BigDecimal preis;

    @ManyToOne(fetch = FetchType.LAZY)
    private Produktkategorie kategorie;

    @Column(nullable = false)
    private boolean kalkuliert = false;

    @Column(name = "zuschlag_prozent", precision = 6, scale = 2)
    private BigDecimal zuschlagProzent;

    @Column(name = "losgroesse", nullable = false, precision = 12, scale = 3)
    private BigDecimal losgroesse = BigDecimal.ONE;

    @Column(name = "ist_baugruppe", nullable = false)
    private boolean baugruppe = false;

    @Column(name = "temporaer", nullable = false)
    private boolean temporaer = false;

    @OneToMany(mappedBy = "leistung", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<LeistungPosition> positionen = new ArrayList<>();
}
