package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Kunde;

import java.time.LocalDateTime;

/**
 * Ein Gebäude oder Standort eines Kunden, an dem Türen und Tore gewartet werden.
 * Löschen ist weich ({@code aktiv = false}), damit die Wartungshistorie erhalten bleibt.
 */
@Entity
@Getter
@Setter
public class WartungObjekt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kunde_id", nullable = false)
    private Kunde kunde;

    @Column(nullable = false, length = 200)
    private String bezeichnung;

    @Column(length = 200)
    private String strasse;

    @Column(length = 20)
    private String plz;

    @Column(length = 120)
    private String ort;

    @Column(name = "ansprechpartner_name", length = 200)
    private String ansprechpartnerName;

    @Column(name = "ansprechpartner_telefon", length = 60)
    private String ansprechpartnerTelefon;

    @Column(columnDefinition = "TEXT")
    private String notizen;

    @Column(nullable = false)
    private boolean aktiv = true;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    @PrePersist
    void vorDemAnlegen() {
        if (angelegtAm == null) {
            angelegtAm = LocalDateTime.now();
        }
    }
}
