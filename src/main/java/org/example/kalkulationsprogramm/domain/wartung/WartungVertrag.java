package org.example.kalkulationsprogramm.domain.wartung;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Projekt;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Der Wartungsvertrag eines Projekts: wie lange er läuft, in welchem Abstand gewartet wird und was die Wartung kostet.
 * Ein Projekt hat höchstens einen Vertrag.
 */
@Entity
@Table(name = "wartung_vertrag")
@Getter
@Setter
public class WartungVertrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Projekt projekt;

    @Column(name = "vertragsnummer", length = 60)
    private String vertragsnummer;

    @Column(name = "beginn", nullable = false)
    private LocalDate beginn;

    /** Ende der Laufzeit; leer = unbefristet. */
    @Column(name = "ende")
    private LocalDate ende;

    /** Abstand der Wartungen in Monaten (zum Beispiel 12 = jährlich). */
    @Column(name = "intervall_monate", nullable = false)
    private int intervallMonate = 12;

    /** Preis je Wartung, netto; leer = nicht festgelegt. */
    @Column(name = "preis_netto", precision = 12, scale = 2)
    private BigDecimal preisNetto;

    @Column(name = "notiz", length = 1000)
    private String notiz;

    @Column(name = "angelegt_am", nullable = false, updatable = false)
    private LocalDateTime angelegtAm;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    @PrePersist
    void beimAnlegen() {
        angelegtAm = LocalDateTime.now();
        geaendertAm = angelegtAm;
    }

    @PreUpdate
    void beimAendern() {
        geaendertAm = LocalDateTime.now();
    }
}
