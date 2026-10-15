package org.example.kalkulationsprogramm.domain.planung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Etwas, das Arbeit aufnimmt, aber kein Mitarbeiter ist: Werkstatt, Büro, Vor-Ort-Einsatz, Fremdfertigung, Fahrzeug, Maschine … (frei einstellbar). */
@Getter
@Setter
@Entity
@Table(name = "plan_ressource")
public class PlanRessource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /** ORT, EXTERN, FAHRZEUG, MASCHINE oder SONSTIGE. */
    @Column(nullable = false, length = 20)
    private String art = "SONSTIGE";

    @Column(nullable = false)
    private int sortierung = 0;

    @Column(nullable = false)
    private boolean aktiv = true;
}
