package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ein Wartungsprotokoll: eine Prüfung einer Tür oder eines Tors an einem Datum.
 *
 * <p>Die Inhalte (Abschnitte, Prüfpunkte, Datenfelder) sind eine Kopie des Vorlagenkatalogs zum
 * Zeitpunkt der Anlage und liegen in eigenen Tabellen mit {@code protokoll_id}.</p>
 */
@Entity
@Getter
@Setter
public class WartungProtokoll {

    public static final String STANDARD_TYP = "Wartungsprotokoll";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 80)
    private String nummer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kunde_id", nullable = false)
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "objekt_id")
    private WartungObjekt objekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tuer_id")
    private WartungTuer tuer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id")
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "techniker_id")
    private Mitarbeiter techniker;

    @Column(length = 200)
    private String titel;

    @Column(name = "protokoll_typ", nullable = false, length = 100)
    private String protokollTyp = STANDARD_TYP;

    @Column(nullable = false)
    private LocalDate datum;

    @Column(name = "intervall_monate", nullable = false)
    private int intervallMonate = 12;

    @Column(name = "naechste_wartung")
    private LocalDate naechsteWartung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WartungStatus status = WartungStatus.OFFEN;

    @Column(columnDefinition = "TEXT")
    private String bemerkungen;

    @Column(name = "ansprechpartner_name", length = 200)
    private String ansprechpartnerName;

    /** PNG als Data-URL (base64). */
    @Column(name = "unterschrift_techniker", columnDefinition = "LONGTEXT")
    private String unterschriftTechniker;

    @Column(name = "unterschrift_techniker_name", length = 200)
    private String unterschriftTechnikerName;

    @Column(name = "unterschrift_kunde", columnDefinition = "LONGTEXT")
    private String unterschriftKunde;

    @Column(name = "unterschrift_kunde_name", length = 200)
    private String unterschriftKundeName;

    @Column(name = "abgeschlossen_am")
    private LocalDateTime abgeschlossenAm;

    /** Aus welcher früheren Prüfung dieser Feldeinsatz entstand (nur zur Anzeige; die Inhalte sind kopiert). */
    @Column(name = "vorlage_protokoll_id")
    private Long vorlageProtokollId;

    /** Der als PDF im Projekt abgelegte Bericht (Nummer des Projekt-Dokuments). */
    @Column(name = "bericht_dokument_id")
    private Long berichtDokumentId;

    @Column(name = "bericht_erstellt_am")
    private LocalDateTime berichtErstelltAm;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    @Column(name = "geaendert_am", nullable = false)
    private LocalDateTime geaendertAm;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "wartung_protokoll_modul", joinColumns = @JoinColumn(name = "protokoll_id"))
    private Set<WartungProtokollModul> module = new LinkedHashSet<>();

    @PrePersist
    void vorDemAnlegen() {
        LocalDateTime jetzt = LocalDateTime.now();
        if (angelegtAm == null) {
            angelegtAm = jetzt;
        }
        geaendertAm = jetzt;
    }

    @PreUpdate
    void vorDemAendern() {
        geaendertAm = LocalDateTime.now();
    }
}
