package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ein persönliches Postfach eines Benutzers (Issue #272). Das Passwort liegt nur verschlüsselt in der Datenbank. */
@Entity
@Table(name = "benutzer_postfach")
@Getter
@Setter
@NoArgsConstructor
public class BenutzerPostfach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profil_id", nullable = false)
    private Long profilId;

    @Column(nullable = false, length = 120)
    private String bezeichnung;

    @Column(name = "email_adresse", nullable = false, length = 255)
    private String emailAdresse;

    @Column(name = "imap_host", nullable = false, length = 255)
    private String imapHost;

    @Column(name = "imap_port", nullable = false)
    private int imapPort;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(nullable = false, length = 255)
    private String benutzername;

    @Column(name = "passwort_verschluesselt", nullable = false, length = 1000)
    private String passwortVerschluesselt;

    @Column(nullable = false)
    private boolean aktiv = true;

    @Column(name = "ordner_auswahl", length = 4000)
    private String ordnerAuswahl;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;
}
