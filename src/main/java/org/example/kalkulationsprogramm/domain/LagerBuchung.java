package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Buchung im Lagerjournal. Der Bestand ist immer die Summe der Buchungen. {@code menge} hat ein Vorzeichen:
 * Zugang positiv, Abgang negativ, Korrektur (Inventur) die Differenz zum bisherigen Bestand.
 * Die Menge steht in der Verrechnungseinheit des Artikels.
 */
@Entity
@Table(name = "lager_buchung", indexes = {
        @Index(name = "idx_lager_buchung_artikel", columnList = "artikel_id, lagerort_id"),
        @Index(name = "idx_lager_buchung_position", columnList = "artikel_in_projekt_id")
})
@Getter
@Setter
public class LagerBuchung {

    public enum Art { ZUGANG, ABGANG, KORREKTUR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artikel_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lagerort_id", nullable = false)
    private Lagerort lagerort;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "art", nullable = false, length = 20)
    private Art art;

    @Column(name = "menge", nullable = false, precision = 14, scale = 3)
    private BigDecimal menge;

    @Column(name = "gebucht_am", nullable = false)
    private LocalDateTime gebuchtAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Projekt projekt;

    /** Lose Verknüpfung zur Projektposition "aus Lager" (ohne Fremdschlüssel: die Position kann verschwinden). */
    @Column(name = "artikel_in_projekt_id")
    private Long artikelInProjektId;

    @Column(name = "notiz", length = 255)
    private String notiz;

    @Column(name = "benutzer", length = 120)
    private String benutzer;
}
