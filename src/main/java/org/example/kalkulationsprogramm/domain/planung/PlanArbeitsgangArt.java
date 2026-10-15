package org.example.kalkulationsprogramm.domain.planung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Zu welcher Vorgangsart die gestempelte Zeit eines Arbeitsgangs zählt, wenn die Abteilung allein nicht reicht (mehrere Arten teilen eine Abteilung). */
@Getter
@Setter
@Entity
@Table(name = "plan_arbeitsgang_art")
public class PlanArbeitsgangArt {

    @Id
    @Column(name = "arbeitsgang_id")
    private Long arbeitsgangId;

    @Column(name = "typ_id", nullable = false)
    private Long typId;
}
