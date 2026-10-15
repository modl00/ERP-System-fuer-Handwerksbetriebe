package org.example.kalkulationsprogramm.domain.planung;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/** Die gerechnete Zuteilung der Plantafel: Wer arbeitet an welchem Tag wie viele Stunden an einem Vorgang. Wird bei jeder Neuberechnung neu geschrieben. */
@Getter
@Setter
@Entity
@Table(name = "plan_zuteilung")
public class PlanZuteilung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vorgang_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlanVorgang vorgang;

    /** Leer = Standardwert, noch niemand zugewiesen. */
    @Column(name = "mitarbeiter_id")
    private Long mitarbeiterId;

    @Column(nullable = false)
    private LocalDate datum;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal stunden;
}
