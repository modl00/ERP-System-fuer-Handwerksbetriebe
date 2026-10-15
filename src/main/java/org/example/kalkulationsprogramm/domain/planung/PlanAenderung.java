package org.example.kalkulationsprogramm.domain.planung;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Eine Änderung an der Plantafel von Hand, mit dem Zustand davor (für „Rückgängig“). Ohne {@code vorher} lässt sie sich nicht zurücknehmen. */
@Getter
@Setter
@Entity
@Table(name = "plan_aenderung")
public class PlanAenderung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime zeitpunkt;

    @Column(length = 100)
    private String benutzer;

    @Column(name = "vorgang_id")
    private Long vorgangId;

    @Column(name = "projekt_id")
    private Long projektId;

    @Column(nullable = false, length = 500)
    private String beschreibung;

    /** Der Zustand des Vorgangs vor der Änderung als JSON; leer = nicht rückgängig zu machen. */
    @Column(columnDefinition = "TEXT")
    private String vorher;

    @Column(name = "rueckgaengig_am")
    private LocalDateTime rueckgaengigAm;
}
