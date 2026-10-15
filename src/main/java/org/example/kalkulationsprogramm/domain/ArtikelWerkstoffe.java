package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Halbzeug mit Abmessungen - Rohre, Stabstahl, Profile und Bleche.
 *
 * <p>Alle Masse in Millimetern. Welche Masse belegt sind, haengt an der
 * {@link Profilform}: ein Rundrohr hat Durchmesser und Wandstaerke, ein
 * Rechteckrohr Hoehe, Breite und Wandstaerke, ein Blech nur die Wandstaerke
 * (= Blechdicke).
 */
@Getter
@Setter
@Entity
public class ArtikelWerkstoffe extends Artikel
{
    /** Gewicht je laufendem Meter in kg/m. Bei Blechen stattdessen {@link #massePerQm}. */
    @Column(name = "masse_pro_meter", precision = 12, scale = 4)
    private BigDecimal masse;

    /** Gewicht je Quadratmeter in kg/m2 - nur bei Blechen belegt. */
    @Column(name = "masse_pro_qm", precision = 10, scale = 4)
    private BigDecimal massePerQm;

    /**
     * Abzuwickelnde Oberflaeche in m2 je laufendem Meter. Grundlage fuer die
     * Kosten von Pulverbeschichtung und Anstrich.
     */
    @Column(name = "mantelflaeche", precision = 12, scale = 4)
    private BigDecimal mantelflaeche;

    /**
     * Oberflaeche in m2 je m2 Blech. Leer = beide Seiten (2). Grundlage fuer Pulverbeschichten von Blechen
     * (ein Lochblech hat weniger, ein Riffelblech etwas mehr).
     */
    @Column(name = "flaeche_je_qm", precision = 10, scale = 4)
    private BigDecimal flaecheJeQm;

    /** Gewicht je Stueck in kg - fuer Artikel, die in Stueck gefuehrt werden (Zuschnitte, Konsolen). */
    @Column(name = "kg_je_stueck", precision = 12, scale = 4)
    private BigDecimal kgJeStueck;

    /** Aussenflaeche je Stueck in m2 (zum Pulverbeschichten). */
    @Column(name = "m2_je_stueck", precision = 12, scale = 4)
    private BigDecimal m2JeStueck;

    /** Woher das Gewicht kommt: {@code TABELLE}, {@code BERECHNET} oder {@code HAND}; leer = unbekannt (aeltere Artikel). */
    @Column(name = "gewicht_herkunft", length = 12)
    private String gewichtHerkunft;

    /** Dichte in kg/dm3, mit der ein berechnetes Gewicht entstand. Aendert sich der Werkstoff, ist das Gewicht veraltet. */
    @Column(name = "gewicht_dichte", precision = 8, scale = 4)
    private BigDecimal gewichtDichte;

    /** Zuschnitt-Formel (#228): Eingabefelder als JSON (Name, Bezeichnung, Einheit, Standardwert). */
    @Column(name = "formel_felder_json", length = 4000)
    private String formelFelderJson;

    /** Formel fuer das Gewicht in kg je Stueck (Verzinken); leer = keine Formel. */
    @Column(name = "formel_gewicht", length = 500)
    private String formelGewicht;

    /** Formel fuer die Aussenflaeche in m2 je Stueck (Pulverbeschichten); leer = keine Formel. */
    @Column(name = "formel_flaeche", length = 500)
    private String formelFlaeche;

    /** Oberflaeche geschliffen (z.B. Korn 240) - bei Edelstahl im Sichtbereich ueblich. */
    @Column(name = "geschliffen", nullable = false)
    private boolean geschliffen;

    /** Aussendurchmesser in mm - bei Rundrohr und Rundstab. */
    @Column(name = "durchmesser", precision = 10, scale = 2)
    private BigDecimal durchmesser;

    /** Hoehe in mm. Bei Winkeln der laengere Schenkel. */
    @Column(name = "hoehe", precision = 10, scale = 2)
    private BigDecimal hoehe;

    /** Breite in mm. Bei Winkeln der kuerzere Schenkel. */
    @Column(name = "breite", precision = 10, scale = 2)
    private BigDecimal breite;

    /** Wandstaerke in mm. Bei Blechen die Blechdicke, bei Winkeln die Schenkeldicke. */
    @Column(name = "wandstaerke", precision = 10, scale = 2)
    private BigDecimal wandstaerke;

    /** Stegdicke in mm - bei Traegern und U-Profilen. */
    @Column(name = "stegdicke", precision = 10, scale = 2)
    private BigDecimal stegdicke;

    /** Flanschdicke in mm - bei Traegern und U-Profilen. */
    @Column(name = "flanschdicke", precision = 10, scale = 2)
    private BigDecimal flanschdicke;

    /** Querschnittsflaeche in cm2. */
    @Column(name = "querschnittsflaeche", precision = 10, scale = 3)
    private BigDecimal querschnittsflaeche;

    /** Uebliche Lieferlaenge in mm, z.B. 6000. Basis fuer die Verschnittrechnung. */
    @Column(name = "standardlaenge_mm")
    private Integer standardlaenge;

    /**
     * Kurzform der Abmessung fuer die Anzeige, z.B. "42,4 x 2" oder "60 x 40 x 3".
     * Wird aus den belegten Massen zusammengesetzt.
     */
    public String getAbmessungText() {
        Profilform form = getProfilform();
        if (form != null && form.istRund() && durchmesser != null) {
            return wandstaerke != null
                    ? format(durchmesser) + " x " + format(wandstaerke)
                    : "Ø " + format(durchmesser);
        }
        if (form != null && form.istBlech() && wandstaerke != null) {
            return format(wandstaerke) + " mm";
        }
        StringBuilder sb = new StringBuilder();
        if (hoehe != null) {
            sb.append(format(hoehe));
        }
        if (breite != null) {
            sb.append(sb.isEmpty() ? "" : " x ").append(format(breite));
        }
        if (wandstaerke != null) {
            sb.append(sb.isEmpty() ? "" : " x ").append(format(wandstaerke));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** Zahl ohne ueberfluessige Nullen und mit Komma statt Punkt. */
    private static String format(BigDecimal wert) {
        return wert.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
