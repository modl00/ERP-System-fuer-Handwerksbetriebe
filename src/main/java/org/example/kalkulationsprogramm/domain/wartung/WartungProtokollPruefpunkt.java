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

/**
 * Ein Prüfpunkt im Protokoll. {@code ergebnis = null} heißt: noch nicht bewertet.
 * {@code vorherigesErgebnis} ist die Referenz aus dem letzten abgeschlossenen Protokoll derselben
 * Tür und dient nur der Anzeige ("Letztes Mal: i.O.").
 */
@Entity
@Getter
@Setter
public class WartungProtokollPruefpunkt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "protokoll_id", nullable = false)
    private Long protokollId;

    @Column(name = "abschnitt_id")
    private Long abschnittId;

    /** null bei Punkten, die von Hand ergänzt wurden. */
    @Column(name = "modul_key", length = 60)
    private String modulKey;

    /** Katalogeintrag, aus dem die Zeile kopiert wurde (ohne Fremdschlüssel). */
    @Column(name = "vorlage_id")
    private Long vorlageId;

    @Column(nullable = false, length = 500)
    private String label;

    @Enumerated(EnumType.STRING)
    private WartungErgebnis ergebnis;

    @Column(columnDefinition = "TEXT")
    private String bemerkung;

    @Enumerated(EnumType.STRING)
    @Column(name = "vorheriges_ergebnis")
    private WartungErgebnis vorherigesErgebnis;

    @Column(nullable = false)
    private int sortierung;
}
