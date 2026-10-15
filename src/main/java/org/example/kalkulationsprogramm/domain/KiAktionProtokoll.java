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

/** Ein Eintrag im Protokoll der KI-Aktionen: wer, wann, welcher Anbieter und welches Modell, welches Werkzeug mit welchen Angaben, und was daraus wurde. */
@Getter
@Setter
@Entity
@Table(name = "ki_aktion_protokoll")
public class KiAktionProtokoll {

    public static final String STATUS_AUSGEFUEHRT = "AUSGEFUEHRT";
    public static final String STATUS_VORGESCHLAGEN = "VORGESCHLAGEN";
    public static final String STATUS_BESTAETIGT = "BESTAETIGT";
    public static final String STATUS_VERWORFEN = "VERWORFEN";
    public static final String STATUS_FEHLER = "FEHLER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime zeit;

    @Column(name = "profil_id")
    private Long profilId;

    @Column(length = 120)
    private String benutzer;

    @Column(nullable = false, length = 40)
    private String funktion;

    @Column(nullable = false, length = 20)
    private String anbieter;

    @Column(length = 120)
    private String modell;

    @Column(nullable = false, length = 80)
    private String werkzeug;

    @Column(columnDefinition = "TEXT")
    private String parameter;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 2000)
    private String ergebnis;
}
