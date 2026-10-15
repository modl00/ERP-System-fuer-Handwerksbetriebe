package org.example.kalkulationsprogramm.domain;

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

/** Rechte einer Abteilung an einer {@link Funktion}: Ansehen, Anlegen, Ändern, Löschen. Ohne Zeile gilt "erlaubt". */
@Getter
@Setter
@Entity
@Table(name = "abteilung_funktionsrecht", uniqueConstraints = @UniqueConstraint(columnNames = {"abteilung_id", "funktion"}))
public class AbteilungFunktionsrecht {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abteilung_id", nullable = false)
    private Abteilung abteilung;

    @Column(name = "funktion", nullable = false, length = 40)
    private String funktion;

    @Column(nullable = false)
    private boolean ansehen = true;

    @Column(nullable = false)
    private boolean anlegen = true;

    @Column(nullable = false)
    private boolean aendern = true;

    @Column(nullable = false)
    private boolean loeschen = true;

    public boolean erlaubt(FunktionsAktion aktion) {
        return switch (aktion) {
            case ANSEHEN -> ansehen;
            case ANLEGEN -> ansehen && anlegen;
            case AENDERN -> ansehen && aendern;
            case LOESCHEN -> ansehen && loeschen;
        };
    }
}
