package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Lager-Einstellungen eines Artikels (derzeit der Mindestbestand, in der Verrechnungseinheit des Artikels). */
@Entity
@Table(name = "lager_artikel")
@Getter
@Setter
public class LagerArtikel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @Column(name = "mindestbestand", precision = 14, scale = 3)
    private BigDecimal mindestbestand;
}
