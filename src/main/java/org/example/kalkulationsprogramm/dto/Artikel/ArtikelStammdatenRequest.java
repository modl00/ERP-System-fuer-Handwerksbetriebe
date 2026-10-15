package org.example.kalkulationsprogramm.dto.Artikel;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Fertigungszustand;
import org.example.kalkulationsprogramm.domain.Herstellverfahren;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

import lombok.Getter;
import lombok.Setter;

/**
 * Alle Stammdaten eines Artikels zum Ändern. Es ist ein vollständiges Ersetzen: Was fehlt, wird geleert, nur der Produktname ist Pflicht.
 * Die Abmessungen gelten nur für Halbzeug ({@code ArtikelWerkstoffe}), bei anderen Artikeln werden sie ignoriert. Texte, Aufschlag,
 * Lieferanten und Preise haben eigene Endpunkte.
 */
@Getter
@Setter
public class ArtikelStammdatenRequest {

    /** Stand des Artikels beim Laden. Wurde er inzwischen von jemand anderem geändert, wird das Speichern abgelehnt. */
    private Long version;

    private String produktname;
    private String produktlinie;
    private String produkttext;
    private String artikelnummer;
    private String ean;
    private Long verpackungseinheit;
    private String preiseinheit;
    private Verrechnungseinheit verrechnungseinheit;
    private Long kategorieId;
    private Long werkstoffId;

    private Profilform profilform;
    private Herstellverfahren herstellverfahren;
    private Fertigungszustand fertigungszustand;
    private String massnorm;
    private String werkstoffnorm;

    private BigDecimal durchmesser;
    private BigDecimal hoehe;
    private BigDecimal breite;
    private BigDecimal wandstaerke;
    private BigDecimal stegdicke;
    private BigDecimal flanschdicke;
    private Integer standardlaenge;

    /** Gewicht je Meter in kg/m und Oberfläche je Meter in m²/m (nur Halbzeug); leer = keine Angabe. */
    private BigDecimal kgProMeter;
    private BigDecimal mantelflaeche;

    /** Bleche: Gewicht je m² in kg/m² und Fläche je m² Blech in m²/m² (leer = beide Seiten, 2). */
    private BigDecimal kgProQm;
    private BigDecimal flaecheJeQm;

    /** Artikel in Stück: Gewicht je Stück in kg und Außenfläche je Stück in m². */
    private BigDecimal kgJeStueck;
    private BigDecimal m2JeStueck;

    /** Zuschnitt-Formel (#228): Eingabefelder mit Standardwert und je eine Formel für Gewicht (kg) und Außenfläche (m²) je Stück; beide leer = keine Formel. */
    private java.util.List<org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld> formelFelder;
    private String formelGewicht;
    private String formelFlaeche;

    /** Woher das Gewicht kommt: TABELLE, BERECHNET oder HAND; bei BERECHNET die Dichte (kg/dm³), mit der gerechnet wurde. */
    private String gewichtHerkunft;
    private BigDecimal gewichtDichte;

    /** Eignung für Feuerverzinken nur für diesen Artikel festlegen; leer = vom Werkstoff übernehmen. */
    private Boolean verzinkungsgeeignet;
    /** Eignung für Pulverbeschichten nur für diesen Artikel festlegen; leer = vom Werkstoff übernehmen. */
    private Boolean pulverbeschichtungsgeeignet;
}
