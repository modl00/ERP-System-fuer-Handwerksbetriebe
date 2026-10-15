package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Eine Änderung am Kostensatz einer Maschine: der neue Satz, ab wann er gilt, wer ihn geändert hat und ob er errechnet oder von Hand festgelegt war. */
@Entity
@Getter
@Setter
@Table(name = "maschine_satz_verlauf")
public class MaschineSatzVerlauf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maschine_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Maschine maschine;

    @Column(name = "satz", nullable = false, precision = 10, scale = 2)
    private BigDecimal satz;

    @Column(name = "von_hand", nullable = false)
    private boolean vonHand;

    @Column(name = "gueltig_ab", nullable = false)
    private LocalDate gueltigAb;

    @Column(name = "geaendert_von", length = 120)
    private String geaendertVon;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;
}
