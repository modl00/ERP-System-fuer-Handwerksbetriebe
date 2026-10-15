package org.example.kalkulationsprogramm.domain.lieferschein;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ein Lieferschein an einen Kunden. Mit der Unterschrift des Empfängers ist er fertig und nicht mehr änderbar. */
@Entity
@Getter
@Setter
@Table(name = "lieferschein")
public class Lieferschein {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nummer", nullable = false, unique = true, length = 60)
    private String nummer;

    /** Vom Gerät vergebene Kennung: ein wiederholtes Senden legt keinen zweiten Lieferschein an. */
    @Column(name = "client_id", unique = true, length = 64)
    private String clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kunde_id")
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @Column(name = "empfaenger_name", nullable = false, length = 200)
    private String empfaengerName;

    @Column(name = "datum", nullable = false)
    private LocalDate datum;

    @Column(name = "bemerkung", columnDefinition = "TEXT")
    private String bemerkung;

    @OneToMany(mappedBy = "lieferschein", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortierung ASC, id ASC")
    private List<LieferscheinPosition> positionen = new ArrayList<>();

    /** PNG als Data-URL (base64). */
    @Column(name = "unterschrift_bild", columnDefinition = "LONGTEXT")
    private String unterschriftBild;

    @Column(name = "unterschrift_name", length = 200)
    private String unterschriftName;

    @Column(name = "unterschrieben_am")
    private LocalDateTime unterschriebenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erstellt_von_id")
    private Mitarbeiter erstelltVon;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    @Column(name = "kopie_an", length = 255)
    private String kopieAn;

    @Column(name = "kopie_gesendet_am")
    private LocalDateTime kopieGesendetAm;

    public boolean istUnterschrieben() {
        return unterschriebenAm != null;
    }
}
