package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Eine Tür oder ein Tor in einem Objekt. {@link #module} legt fest, was an ihr geprüft wird
 * (Vorbelegung für neue Protokolle). Intervall und nächste Fälligkeit bilden den Wartungsplan
 * der Tür; die Fälligkeit wird beim Abschluss eines Protokolls fortgeschrieben.
 */
@Entity
@Table(name = "wartung_tuer")
@Getter
@Setter
public class WartungTuer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objekt_id", nullable = false)
    private WartungObjekt objekt;

    /** Projekt, zu dem die Wartung gehört. Neue Feldeinsätze erben es, ein bestehendes Protokoll behält sein eigenes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @Column(nullable = false, length = 200)
    private String bezeichnung;

    @Column(columnDefinition = "TEXT")
    private String notizen;

    @Column(name = "intervall_monate")
    private Integer intervallMonate;

    @Column(name = "naechste_faelligkeit")
    private LocalDate naechsteFaelligkeit;

    @Column(nullable = false)
    private boolean aktiv = true;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    // Gebündeltes Nachladen: Ein Kunde mit vielen Türen löst sonst pro Tür eine eigene Abfrage für die Module aus.
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "wartung_tuer_modul", joinColumns = @JoinColumn(name = "tuer_id"))
    private Set<WartungModulAuswahl> module = new HashSet<>();

    @PrePersist
    void vorDemAnlegen() {
        if (angelegtAm == null) {
            angelegtAm = LocalDateTime.now();
        }
    }
}
