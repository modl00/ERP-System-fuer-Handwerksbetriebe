package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.example.kalkulationsprogramm.domain.Profilform;

/**
 * Gewicht und Oberfläche je Meter aus den Maßen (#168), für Formen, die sich mit einer Formel genau berechnen lassen: Rohre, Stäbe, Flachstahl und (angenähert) Winkel.
 * Gewicht = Querschnittsfläche × Dichte; Oberfläche = Umfang (Außenfläche) je Meter. Walzträger (IPE, HEA, HEB, UPN) stehen in der {@link ProfilTabelle}.
 */
public final class ProfilRechner {

    /** Dichte von Stahl in kg/dm³, wenn der Werkstoff keine hat. */
    public static final BigDecimal STAHL = new BigDecimal("7.85");
    private static final BigDecimal TAUSEND = new BigDecimal("1000");
    private static final BigDecimal PI = new BigDecimal("3.14159265358979");
    private static final BigDecimal ZWEI = new BigDecimal("2");
    private static final BigDecimal VIER = new BigDecimal("4");

    private ProfilRechner() {
    }

    /**
     * Das Ergebnis; {@code hinweis} erklärt, woher es kommt oder wie genau es ist. Meterware hat {@code kgJeMeter} und {@code m2JeMeter}, ein Blech
     * {@code kgJeQm} und {@code m2JeQm}; was nicht zur Form gehört, ist {@code null}. {@code dichte} ist die Dichte (kg/dm³), mit der gerechnet wurde.
     */
    public record Ergebnis(BigDecimal kgJeMeter, BigDecimal m2JeMeter, String hinweis, BigDecimal kgJeQm, BigDecimal m2JeQm, BigDecimal dichte) {

        public Ergebnis(BigDecimal kgJeMeter, BigDecimal m2JeMeter, String hinweis) {
            this(kgJeMeter, m2JeMeter, hinweis, null, null, null);
        }
    }

    /** Maße in mm; was die Form nicht braucht, darf fehlen. Wirft {@link IllegalArgumentException} mit Klartext, wenn Maße fehlen oder die Form nicht berechenbar ist. */
    public static Ergebnis rechne(Profilform form, BigDecimal durchmesser, BigDecimal hoehe, BigDecimal breite, BigDecimal wand, BigDecimal dichte) {
        if (form == null) {
            throw new IllegalArgumentException("Bitte zuerst die Form wählen.");
        }
        BigDecimal rho = dichte != null && dichte.signum() > 0 ? dichte : STAHL;
        if (form == Profilform.BLECH) {
            return blech(wand, rho);
        }
        BigDecimal flaeche;   // Querschnitt in mm²
        BigDecimal umfang;    // Außenumfang in mm
        String hinweis = "Berechnet aus den Maßen mit der Dichte " + rho.stripTrailingZeros().toPlainString() + " kg/dm³; die Oberfläche ist die Außenfläche.";
        switch (form) {
            case RUNDROHR -> {
                BigDecimal d = pflicht(durchmesser, "den Durchmesser");
                BigDecimal t = pflicht(wand, "die Wandstärke");
                pruefeWand(t, d);
                flaeche = PI.multiply(d.subtract(t)).multiply(t);
                umfang = PI.multiply(d);
            }
            case QUADRATROHR -> {
                BigDecimal a = erstes(hoehe, breite, "die Höhe (gleich der Breite)");
                BigDecimal t = pflicht(wand, "die Wandstärke");
                pruefeWand(t, a);
                flaeche = VIER.multiply(t).multiply(a.subtract(t));
                umfang = VIER.multiply(a);
            }
            case RECHTECKROHR -> {
                BigDecimal h = pflicht(hoehe, "die Höhe");
                BigDecimal b = pflicht(breite, "die Breite");
                BigDecimal t = pflicht(wand, "die Wandstärke");
                pruefeWand(t, h.min(b));
                flaeche = ZWEI.multiply(t).multiply(h.add(b).subtract(ZWEI.multiply(t)));
                umfang = ZWEI.multiply(h.add(b));
            }
            case RUNDSTAB -> {
                BigDecimal d = pflicht(durchmesser, "den Durchmesser");
                flaeche = PI.multiply(d).multiply(d).divide(VIER, MathContext.DECIMAL64);
                umfang = PI.multiply(d);
            }
            case VIERKANTSTAB -> {
                BigDecimal a = erstes(hoehe, breite, "die Höhe (gleich der Breite)");
                flaeche = a.multiply(a);
                umfang = VIER.multiply(a);
            }
            case FLACHSTAB, BREITFLACHSTAHL -> {
                BigDecimal h = pflicht(hoehe, "die Höhe oder Dicke");
                BigDecimal b = pflicht(breite, "die Breite");
                flaeche = h.multiply(b);
                umfang = ZWEI.multiply(h.add(b));
            }
            case WINKEL_GLEICHSCHENKLIG, WINKEL_UNGLEICHSCHENKLIG -> {
                BigDecimal h = pflicht(hoehe, "die Schenkellänge (Höhe)");
                BigDecimal b = form == Profilform.WINKEL_GLEICHSCHENKLIG ? h : pflicht(breite, "die zweite Schenkellänge (Breite)");
                BigDecimal t = pflicht(wand, "die Schenkeldicke (Wandstärke)");
                pruefeWand(t, h.min(b));
                flaeche = t.multiply(h.add(b).subtract(t));
                umfang = ZWEI.multiply(h.add(b));
                hinweis = "Angenähert, ohne die Rundungen am Schenkel: Das Gewicht in den Normtabellen ist bis zu 2 % höher. Mit der Dichte "
                        + rho.stripTrailingZeros().toPlainString() + " kg/dm³; die Oberfläche ist die Außenfläche.";
            }
            case RIFFELBLECH, LOCHBLECH -> throw new IllegalArgumentException("Riffel- und Lochblech haben keine einfache Formel (Riffelung, Lochanteil). Bitte das Gewicht je m² aus dem Datenblatt eintragen.");
            default -> throw new IllegalArgumentException("Für diese Form gibt es keine Formel. Bei IPE, HEA, HEB und UPN die Profiltabelle benutzen, sonst die Werte von Hand eintragen.");
        }
        BigDecimal kg = flaeche.multiply(rho).divide(TAUSEND, 4, RoundingMode.HALF_UP);
        BigDecimal m2 = umfang.divide(TAUSEND, 4, RoundingMode.HALF_UP);
        return new Ergebnis(kg, m2, hinweis, null, null, rho);
    }

    /** Glattes Blech: 1 mm Dicke bei 1 m² sind 1 dm³; das Gewicht je m² ist also Dicke (mm) × Dichte (kg/dm³). Außenfläche: beide Seiten (2 m² je m²). */
    private static Ergebnis blech(BigDecimal dicke, BigDecimal rho) {
        BigDecimal t = pflicht(dicke, "die Blechdicke (bei Wandstärke)");
        if (t.compareTo(new BigDecimal("500")) > 0) {
            throw new IllegalArgumentException("Die Blechdicke ist höher als 500 mm. Bitte prüfen (Angabe in mm).");
        }
        BigDecimal kg = t.multiply(rho).setScale(4, RoundingMode.HALF_UP);
        return new Ergebnis(null, null, "Berechnet aus Dicke × Dichte (" + rho.stripTrailingZeros().toPlainString() + " kg/dm³), glattes Blech ohne Toleranz. "
                + "Die Fläche rechnet beide Seiten mit (2 m² je m²); die Kanten zählen nicht.", kg, new BigDecimal("2.0000"), rho);
    }

    private static BigDecimal pflicht(BigDecimal wert, String was) {
        if (wert == null || wert.signum() <= 0) {
            throw new IllegalArgumentException("Bitte " + was + " eintragen.");
        }
        return wert;
    }

    private static BigDecimal erstes(BigDecimal a, BigDecimal b, String was) {
        if (a != null && a.signum() > 0) {
            return a;
        }
        return pflicht(b, was);
    }

    private static void pruefeWand(BigDecimal wand, BigDecimal aussenmass) {
        if (wand.multiply(ZWEI).compareTo(aussenmass) >= 0) {
            throw new IllegalArgumentException("Die Wandstärke ist zu groß für das Maß (mindestens eine halbe Seite).");
        }
    }
}
