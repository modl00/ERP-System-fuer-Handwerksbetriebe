package org.example.kalkulationsprogramm.domain.planung;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Der Vorgang beginnt erst nach dem Ende des Vorgängers (plus Puffer in Arbeitstagen). */
@Getter
@Setter
@Entity
@Table(name = "plan_abhaengigkeit", uniqueConstraints = @UniqueConstraint(columnNames = { "vorgang_id", "vorgaenger_id" }))
public class PlanAbhaengigkeit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vorgang_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlanVorgang vorgang;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vorgaenger_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlanVorgang vorgaenger;

    @Column(name = "versatz_tage", nullable = false)
    private int versatzTage = 0;
}
