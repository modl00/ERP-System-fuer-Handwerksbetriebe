package org.example.kalkulationsprogramm.domain.lieferschein;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine vorab reservierte Dokumentnummer für das Arbeiten ohne Netz. {@code RESERVIERT} heißt noch nicht verwendet und bleibt als Lücke dokumentiert, bis sie
 * einem Dokument zugeordnet ({@code VERWENDET}) ist.
 */
@Entity
@Getter
@Setter
@Table(name = "nummernreservierung", uniqueConstraints = @UniqueConstraint(columnNames = { "art", "nummer" }))
public class Nummernreservierung {

    public enum Status { RESERVIERT, VERWENDET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "art", nullable = false, length = 40)
    private String art;

    @Column(name = "nummer", nullable = false, length = 60)
    private String nummer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(name = "reserviert_am", nullable = false)
    private LocalDateTime reserviertAm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.RESERVIERT;

    @Column(name = "verwendet_am")
    private LocalDateTime verwendetAm;
}
