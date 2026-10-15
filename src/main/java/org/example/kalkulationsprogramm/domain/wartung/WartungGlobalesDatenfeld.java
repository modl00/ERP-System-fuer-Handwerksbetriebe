package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Datenfeld, das in jedes neue Protokoll kopiert wird, egal welche Module gewählt sind. */
@Entity
@Getter
@Setter
public class WartungGlobalesDatenfeld {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WartungFeldtyp feldtyp = WartungFeldtyp.TEXT;

    @Column(columnDefinition = "TEXT")
    private String optionen;

    @Column(nullable = false)
    private int sortierung;
}
