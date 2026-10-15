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

/** Ein ausgefülltes Datenfeld der Datenaufnahme im Protokoll. */
@Entity
@Getter
@Setter
public class WartungProtokollDatenfeld {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "protokoll_id", nullable = false)
    private Long protokollId;

    @Column(name = "abschnitt_id")
    private Long abschnittId;

    /** null bei globalen oder von Hand ergänzten Feldern. */
    @Column(name = "modul_key", length = 60)
    private String modulKey;

    @Column(name = "vorlage_id")
    private Long vorlageId;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WartungFeldtyp feldtyp = WartungFeldtyp.TEXT;

    @Column(columnDefinition = "TEXT")
    private String optionen;

    @Column(columnDefinition = "TEXT")
    private String wert;

    @Column(nullable = false)
    private int sortierung;
}
