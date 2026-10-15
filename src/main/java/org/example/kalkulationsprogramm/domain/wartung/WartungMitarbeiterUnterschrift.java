package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Die einmal erfasste Unterschrift eines Mitarbeiters. Sie wird beim Anlegen eines Feldeinsatzes in das
 * Protokoll <b>kopiert</b>: Eine spätere Änderung wirkt nie auf bestehende oder abgeschlossene Protokolle.
 * Der Schlüssel ist die Mitarbeiter-Nummer, ein Mitarbeiter hat höchstens eine.
 */
@Entity
@Table(name = "wartung_mitarbeiter_unterschrift")
@Getter
@Setter
@NoArgsConstructor
public class WartungMitarbeiterUnterschrift {

    @Id
    @Column(name = "mitarbeiter_id")
    private Long mitarbeiterId;

    /** PNG-Data-URL, siehe WartungUnterschriftPruefer. */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String bild;

    @Column(length = 200)
    private String name;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;
}
