package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Eine Zeile der Kalkulation einer {@link Leistung}: ein Artikel (Material) oder ein Arbeitsgang (Arbeitszeit).
 * Die Menge gilt je <b>einer</b> Einheit der Leistung: Menge des Artikels in dessen Positionseinheit, bei Arbeit die Stunden.
 * Eine Arbeits-Zeile kann stattdessen mit Rüstzeit und Bearbeitungszeit rechnen (siehe {@link #ruestzeitMinuten}); {@code menge} ist dann die
 * errechnete Stundenzahl je Einheit.
 */
@Entity
@Table(name = "leistung_position")
@Getter
@Setter
public class LeistungPosition {

    public enum Typ { ARTIKEL, ARBEIT, BAUGRUPPE, FREMDLEISTUNG }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leistung_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Leistung leistung;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "typ", nullable = false, length = 20)
    private Typ typ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artikel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artikel artikel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbeitsgang_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Arbeitsgang arbeitsgang;

    /** Bei Typ BAUGRUPPE: die benutzte Baugruppe (eine Leistung mit {@code baugruppe = true}); {@code menge} ist dann die Menge je Einheit dieser Leistung. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baugruppe_id")
    private Leistung baugruppe;

    /** Bei Typ FREMDLEISTUNG: das Angebot des Veredlers; {@code menge} ist dann die Menge in dessen Abrechnungseinheit je Einheit der Leistung (zum Beispiel kg je lfm). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fremdleistung_id")
    private Fremdleistung fremdleistung;

    /** Bei Fremdleistung: Gewicht in kg je Einheit der Leistung, nur nötig für Transport je kg, wenn der Veredler nicht nach kg abrechnet. */
    @Column(name = "gewicht_kg_je_einheit", precision = 12, scale = 3)
    private BigDecimal gewichtKgJeEinheit;

    /** Bei Fremdleistung: woher die Menge kommt. Leer = von Hand ({@code menge}); {@code GEWICHT} = berechnetes Gesamtgewicht in kg, {@code OBERFLAECHE} = berechnete Oberfläche in m² der übrigen Zeilen. */
    @Column(name = "menge_aus", length = 20)
    private String mengeAus;

    @Column(name = "menge", nullable = false, precision = 12, scale = 3)
    private BigDecimal menge;

    /** Rüstzeit einer Arbeits-Zeile in Minuten, einmal je Los der Leistung. Beide Zeiten leer: {@code menge} sind die Stunden je Einheit. */
    @Column(name = "ruestzeit_minuten", precision = 8, scale = 2)
    private BigDecimal ruestzeitMinuten;

    /** Bearbeitungszeit einer Arbeits-Zeile in Minuten je Einheit der Leistung. */
    @Column(name = "bearbeitungszeit_minuten", precision = 8, scale = 2)
    private BigDecimal bearbeitungszeitMinuten;

    /** Bei Arbeit mit Zeiten: wie oft der Vorgang je Einheit vorkommt (4 Bohrungen je Stab). Die Bearbeitungszeit gilt je Vorgang. Leer = 1. */
    @Column(name = "anzahl_vorgaenge", precision = 12, scale = 3)
    private BigDecimal anzahlVorgaenge;

    /** Bei Material: Zuschlag auf die Menge für Verschnitt in Prozent (der Preis rechnet mit Menge × (1 + Verschnitt)). Leer = kein Verschnitt. */
    @Column(name = "verschnitt_prozent", precision = 6, scale = 2)
    private BigDecimal verschnittProzent;

    /** Bei Arbeit: Maschinenzeit in Prozent der Arbeitszeit dieser Zeile; leer = 100. Nur wirksam, wenn der Arbeitsgang eine Maschine hat. */
    @Column(name = "maschinenanteil_prozent", precision = 7, scale = 2)
    private BigDecimal maschinenanteilProzent;

    /** Werte der Eingabefelder, wenn der Arbeitsgang eine Formel hat (JSON, Feldname in Kleinbuchstaben zu Wert). */
    @Column(name = "variablen_json", length = 2000)
    private String variablenJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "notiz", length = 255)
    private String notiz;
}
