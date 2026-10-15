package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Maschine aus der Maschinenliste (Stufe 2 von #166). Aus Anschaffung, Nutzungsdauer, Zins, Wartung, Energie, Platz und Versicherung ergibt sich der
 * Kostensatz je Laufstunde ({@code MaschinenKostenRechner}); er lässt sich auch <b>von Hand</b> festlegen. {@code kostensatz} ist der Satz, mit dem die Kalkulation
 * rechnet: der Handsatz, sonst der errechnete. Mehrere Arbeitsgänge können dieselbe Maschine nutzen.
 */
@Entity
@Getter
@Setter
@Table(name = "maschine")
public class Maschine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "anschaffungswert", precision = 12, scale = 2)
    private BigDecimal anschaffungswert;

    @Column(name = "restwert", precision = 12, scale = 2)
    private BigDecimal restwert;

    /** Wie viele Jahre die Maschine genutzt wird. */
    @Column(name = "nutzungsdauer_jahre", precision = 5, scale = 2)
    private BigDecimal nutzungsdauerJahre;

    /** Zinssatz in Prozent auf das durchschnittlich gebundene Kapital. */
    @Column(name = "zinssatz_prozent", precision = 5, scale = 2)
    private BigDecimal zinssatzProzent;

    @Column(name = "wartung_je_jahr", precision = 12, scale = 2)
    private BigDecimal wartungJeJahr;

    @Column(name = "platz_je_jahr", precision = 12, scale = 2)
    private BigDecimal platzJeJahr;

    @Column(name = "versicherung_je_jahr", precision = 12, scale = 2)
    private BigDecimal versicherungJeJahr;

    @Column(name = "leistung_kw", precision = 8, scale = 2)
    private BigDecimal leistungKw;

    @Column(name = "strompreis_je_kwh", precision = 8, scale = 4)
    private BigDecimal strompreisJeKwh;

    /** Wie viel der Anschlussleistung im Schnitt wirklich gebraucht wird, in Prozent; leer = 100. */
    @Column(name = "auslastung_prozent", precision = 5, scale = 2)
    private BigDecimal auslastungProzent;

    /** Geplante Laufstunden je Jahr: Durch sie werden die Jahreskosten geteilt. */
    @Column(name = "laufstunden_je_jahr", precision = 8, scale = 1)
    private BigDecimal laufstundenJeJahr;

    /** Von Hand festgelegter Satz je Stunde; leer = der errechnete Satz gilt. */
    @Column(name = "satz_von_hand", precision = 10, scale = 2)
    private BigDecimal satzVonHand;

    /** Der Satz je Laufstunde, mit dem gerechnet wird (Handsatz, sonst errechnet). */
    @Column(name = "kostensatz", nullable = false, precision = 10, scale = 2)
    private BigDecimal kostensatz;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv = true;

    @Column(name = "bemerkung", length = 500)
    private String bemerkung;

    @Column(name = "angelegt_am", nullable = false)
    private LocalDateTime angelegtAm;

    @PrePersist
    void vorDemAnlegen() {
        if (angelegtAm == null) {
            angelegtAm = LocalDateTime.now();
        }
    }
}
