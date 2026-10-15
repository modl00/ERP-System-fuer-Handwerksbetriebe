package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein Ereignis im Aktivitätsprotokoll (Issue #93): wer hat wann was an welchem Objekt getan. Bewusst <b>ohne Inhalte</b>. Die Zeile ist nach dem Anlegen
 * unveränderlich; sie wird nur durch die Aufbewahrungsfrist oder auf Wunsch der Person (Auskunft, Löschung) entfernt.
 */
@Entity
@Table(name = "aktivitaet_ereignis")
@Getter
@Setter
public class AktivitaetEreignis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profil_id")
    private Long profilId;

    @Column(length = 120)
    private String benutzer;

    @Column(nullable = false)
    private LocalDateTime zeitpunkt;

    /** ANGELEGT, GEAENDERT, GELOESCHT, VERSENDET, GEBUCHT, ZUGEORDNET oder ABGESCHLOSSEN. */
    @Column(nullable = false, length = 20)
    private String art;

    @Column(name = "objekt_typ", length = 30)
    private String objektTyp;

    @Column(name = "objekt_id")
    private Long objektId;

    @Column(name = "objekt_name", length = 200)
    private String objektName;

    /** Der Satz für die Anzeige, zum Beispiel "Projekt geändert". */
    @Column(nullable = false, length = 160)
    private String beschreibung;
}
