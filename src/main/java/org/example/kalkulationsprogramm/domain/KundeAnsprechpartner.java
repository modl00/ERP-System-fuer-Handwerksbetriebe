package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

/**
 * Ein Ansprechpartner eines Kunden. Ein Kunde hat beliebig viele, höchstens einer ist der Hauptansprechpartner.
 * Dessen Name steht auch in {@code kunde.ansprechspartner}, das E-Mails, Mahnungen und Dokumente weiter lesen.
 */
@Entity
@Table(name = "kunde_ansprechpartner")
@Getter
@Setter
public class KundeAnsprechpartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kunde_id", nullable = false)
    private Kunde kunde;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 120)
    private String funktion;

    @Column(length = 60)
    private String telefon;

    @Column(length = 60)
    private String mobil;

    @Column(length = 200)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String notizen;

    @Column(nullable = false)
    private boolean haupt = false;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    @PrePersist
    void vorDemAnlegen() {
        if (angelegtAm == null) {
            angelegtAm = LocalDateTime.now();
        }
    }
}
