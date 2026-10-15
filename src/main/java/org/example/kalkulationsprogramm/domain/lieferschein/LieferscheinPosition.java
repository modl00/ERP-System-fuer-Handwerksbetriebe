package org.example.kalkulationsprogramm.domain.lieferschein;

import java.math.BigDecimal;

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

@Entity
@Getter
@Setter
@Table(name = "lieferschein_position")
public class LieferscheinPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferschein_id", nullable = false)
    private Lieferschein lieferschein;

    @Column(name = "sortierung", nullable = false)
    private int sortierung;

    @Column(name = "bezeichnung", nullable = false, length = 300)
    private String bezeichnung;

    @Column(name = "menge", nullable = false, precision = 14, scale = 3)
    private BigDecimal menge;

    @Column(name = "einheit", length = 20)
    private String einheit;
}
