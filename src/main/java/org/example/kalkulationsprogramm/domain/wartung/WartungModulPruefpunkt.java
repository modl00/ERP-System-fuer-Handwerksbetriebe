package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Vorlage eines Prüfpunkts. {@code nurBeiUntertyp = null} heißt: gilt für alle Bauarten. */
@Entity
@Getter
@Setter
public class WartungModulPruefpunkt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "modul_id", nullable = false)
    private Long modulId;

    @Column(name = "abschnitt_id")
    private Long abschnittId;

    @Column(nullable = false, length = 500)
    private String label;

    @Column(name = "nur_bei_untertyp", length = 30)
    private String nurBeiUntertyp;

    @Column(nullable = false)
    private int sortierung;
}
