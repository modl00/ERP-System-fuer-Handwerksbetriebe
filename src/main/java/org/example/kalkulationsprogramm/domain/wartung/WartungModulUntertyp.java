package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Bauart eines Moduls (zum Beispiel 1-flügelig, 2-flügelig, Schiebetür). Der Schlüssel bleibt
 * stabil, auch wenn die Bezeichnung später umbenannt wird.
 */
@Entity
@Getter
@Setter
public class WartungModulUntertyp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "modul_id", nullable = false)
    private Long modulId;

    @Column(nullable = false, length = 30)
    private String schluessel;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private int sortierung;
}
