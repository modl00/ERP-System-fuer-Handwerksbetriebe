package org.example.kalkulationsprogramm.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Das Nummernformat einer Dokumentart (Issue #62). Gibt es für eine Art keine Zeile, gilt das bisherige Format. */
@Getter
@Setter
@Entity
@Table(name = "nummernkreis", uniqueConstraints = @UniqueConstraint(columnNames = "art"))
public class Nummernkreis {

    /** Welcher Teil des Datums in der Nummer steht. */
    public enum Datumsteil { KEINER, JAHR, JAHR_MONAT }

    /** Wann der Zähler wieder bei der Startnummer beginnt. */
    public enum Ruecksetzung { NIE, JAEHRLICH, MONATLICH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "art", nullable = false, length = 40)
    private String art;

    @Column(name = "praefix", nullable = false, length = 10)
    private String praefix;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "datumsteil", nullable = false, length = 20)
    private Datumsteil datumsteil;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ruecksetzung", nullable = false, length = 20)
    private Ruecksetzung ruecksetzung;

    @Column(name = "stellen", nullable = false)
    private int stellen;

    @Column(name = "startnummer", nullable = false)
    private long startnummer;
}
