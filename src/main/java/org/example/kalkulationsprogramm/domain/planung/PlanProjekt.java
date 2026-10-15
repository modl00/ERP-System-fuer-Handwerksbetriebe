package org.example.kalkulationsprogramm.domain.planung;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Start- und Zieltermin eines Auftrags in der Plantafel. Der Zieltermin ist der versprochene Liefertermin; daran misst sich die Ampel. */
@Getter
@Setter
@Entity
@Table(name = "plan_projekt")
public class PlanProjekt {

    @Id
    @Column(name = "projekt_id")
    private Long projektId;

    @Column(name = "start_datum")
    private LocalDate startDatum;

    @Column(name = "ziel_datum")
    private LocalDate zielDatum;

    /** Neu, noch nicht eingeplant: Das Fenster bei der Auftragsbestätigung wurde mit „Später“ übersprungen, alle Werte sind geschätzt. */
    @Column(name = "planung_offen", nullable = false)
    private boolean planungOffen = false;
}
