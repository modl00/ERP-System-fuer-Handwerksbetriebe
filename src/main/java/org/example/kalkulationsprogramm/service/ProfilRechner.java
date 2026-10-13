package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rechnet aus den Maßen eines Halbzeugs Querschnitt, Gewicht und Oberfläche aus. Die Formeln sind die der Geometrie; bei Hohlprofilen
 * gelten die Eckenradien nach EN 10219. Walzprofile (IPE, HEA, HEB, U) lassen sich nicht aus wenigen Maßen rechnen und kommen aus
 * Normtabellen. Alle Eingaben in mm, die Dichte in g/cm³ (= kg/dm³).
 *
 * <p>Welches Maß wohin gehört: Rundrohr {@code durchmesser}, {@code wandstaerke}; Quadratrohr {@code hoehe} (Kante), {@code wandstaerke};
 * Rechteckrohr {@code hoehe}, {@code breite}, {@code wandstaerke}; Rundstab {@code durchmesser}; Vierkantstab {@code hoehe};
 * Sechskantstab {@code hoehe} (Schlüsselweite); Flachstahl {@code breite}, {@code wandstaerke} (Dicke); Winkel {@code hoehe},
 * ({@code breite} bei ungleichschenkligen), {@code wandstaerke}; Blech {@code wandstaerke}; Walzprofile {@code hoehe} (Nenngröße, z. B. 200 für IPE 200).
 */
@Service
public class ProfilRechner {

    /** Dichte von Stahl, wenn der Artikel keinen Werkstoff mit Dichte hat. */
    public static final BigDecimal STAHL_DICHTE = new BigDecimal("7.85");

    private static final double PI = Math.PI;
    private static final double MM2_PRO_CM2 = 100.0;

    public enum Herkunft { BERECHNET, NORMTABELLE }

    /** Die Maße in mm; nicht benötigte bleiben leer. */
    public record Masse(BigDecimal durchmesser, BigDecimal hoehe, BigDecimal breite, BigDecimal wandstaerke) { }

    /**
     * @param querschnittCm2   Querschnittsfläche in cm² (bei Blech leer)
     * @param kgProMeter       Gewicht je laufendem Meter (bei Blech leer)
     * @param kgProQm          Gewicht je Quadratmeter (nur Blech)
     * @param mantelflaecheQm  Oberfläche in m² je laufendem Meter, wenn berechenbar
     * @param hinweis          Hinweis für die Anzeige, oder {@code null}
     */
    public record Ergebnis(BigDecimal querschnittCm2, BigDecimal kgProMeter, BigDecimal kgProQm, BigDecimal mantelflaecheQm, Herkunft herkunft,
            String hinweis) { }

    private static final Map<Integer, Double> IPE = tabelle(new double[][] {
        {80, 6.0}, {100, 8.1}, {120, 10.4}, {140, 12.9}, {160, 15.8}, {180, 18.8}, {200, 22.4}, {220, 26.2}, {240, 30.7}, {270, 36.1},
        {300, 42.2}, {330, 49.1}, {360, 57.1}, {400, 66.3}, {450, 77.6}, {500, 90.7}, {550, 106}, {600, 122}});
    private static final Map<Integer, Double> HEA = tabelle(new double[][] {
        {100, 16.7}, {120, 19.9}, {140, 24.7}, {160, 30.4}, {180, 35.5}, {200, 42.3}, {220, 50.5}, {240, 60.3}, {260, 68.2}, {280, 76.4},
        {300, 88.3}, {320, 97.6}, {340, 105}, {360, 112}, {400, 125}, {450, 140}, {500, 155}, {550, 166}, {600, 178}});
    private static final Map<Integer, Double> HEB = tabelle(new double[][] {
        {100, 20.4}, {120, 26.7}, {140, 33.7}, {160, 42.6}, {180, 51.2}, {200, 61.3}, {220, 71.5}, {240, 83.2}, {260, 93.0}, {280, 103},
        {300, 117}, {320, 127}, {340, 134}, {360, 142}, {400, 155}, {450, 171}, {500, 187}, {550, 199}, {600, 212}});
    private static final Map<Integer, Double> U_STAHL = tabelle(new double[][] {
        {80, 8.64}, {100, 10.6}, {120, 13.4}, {140, 16.0}, {160, 18.8}, {180, 22.0}, {200, 25.3}, {220, 29.4}, {240, 33.2}, {260, 37.9},
        {280, 41.8}, {300, 46.2}});

    private static Map<Integer, Double> tabelle(double[][] zeilen) {
        Map<Integer, Double> m = new TreeMap<>();
        for (double[] z : zeilen) {
            m.put((int) z[0], z[1]);
        }
        return Map.copyOf(m);
    }

    /** Ob für die Form eine Berechnung vorgesehen ist. */
    public boolean kannBerechnen(Profilform form) {
        return form != null && (form.istHohlprofil() || form.istBlech() && form != Profilform.RIFFELBLECH && form != Profilform.LOCHBLECH
                || tabelleFuer(form) != null
                || switch (form) {
                    case RUNDSTAB, VIERKANTSTAB, SECHSKANTSTAB, FLACHSTAB, BREITFLACHSTAHL, WINKEL_GLEICHSCHENKLIG, WINKEL_UNGLEICHSCHENKLIG -> true;
                    default -> false;
                });
    }

    /**
     * @param dichte Dichte in g/cm³; {@code null} = Stahl (7,85)
     * @throws ResponseStatusException 400 mit verständlichem Satz, wenn Maße fehlen oder unmöglich sind oder die Form nicht berechenbar ist
     */
    public Ergebnis berechne(Profilform form, Masse masse, BigDecimal dichte) {
        if (form == null) {
            throw ungueltig("Bitte zuerst die Form des Teils wählen.");
        }
        double rho = (dichte != null && dichte.signum() > 0 ? dichte : STAHL_DICHTE).doubleValue();
        Map<Integer, Double> tab = tabelleFuer(form);
        if (tab != null) {
            return ausTabelle(form, tab, wert(masse.hoehe(), "die Höhe (Nenngröße)", form), rho);
        }
        double flaeche;
        double umfang;
        String hinweis = null;
        switch (form) {
            case RUNDROHR -> {
                double d = wert(masse.durchmesser(), "den Durchmesser", form);
                double t = wert(masse.wandstaerke(), "die Wandstärke", form);
                if (2 * t >= d) {
                    throw ungueltig("Die Wandstärke ist zu groß für den Durchmesser.");
                }
                flaeche = PI * t * (d - t);
                umfang = PI * d;
            }
            case QUADRATROHR, RECHTECKROHR -> {
                double a = wert(masse.hoehe(), form == Profilform.QUADRATROHR ? "die Kantenlänge (Höhe)" : "die Höhe", form);
                double b = form == Profilform.QUADRATROHR ? a : wert(masse.breite(), "die Breite", form);
                double t = wert(masse.wandstaerke(), "die Wandstärke", form);
                if (2 * t >= Math.min(a, b)) {
                    throw ungueltig("Die Wandstärke ist zu groß für die Kantenlänge.");
                }
                double ro = aussenradius(t);
                double ri = ro - t;
                flaeche = 2 * t * (a + b) - 4 * t * t - (4 - PI) * (ro * ro - ri * ri);
                umfang = 2 * (a + b) - 8 * ro + 2 * PI * ro;
                hinweis = "Mit Eckenradien nach EN 10219.";
            }
            case RUNDSTAB -> {
                double d = wert(masse.durchmesser(), "den Durchmesser", form);
                flaeche = PI * d * d / 4;
                umfang = PI * d;
            }
            case VIERKANTSTAB -> {
                double a = wert(masse.hoehe(), "die Kantenlänge (Höhe)", form);
                flaeche = a * a;
                umfang = 4 * a;
            }
            case SECHSKANTSTAB -> {
                double s = wert(masse.hoehe(), "die Schlüsselweite (Höhe)", form);
                flaeche = Math.sqrt(3) / 2 * s * s;
                umfang = 6 * s / Math.sqrt(3);
            }
            case FLACHSTAB, BREITFLACHSTAHL -> {
                double b = wert(masse.breite(), "die Breite", form);
                double t = wert(masse.wandstaerke(), "die Dicke (Wandstärke)", form);
                flaeche = b * t;
                umfang = 2 * (b + t);
            }
            case WINKEL_GLEICHSCHENKLIG, WINKEL_UNGLEICHSCHENKLIG -> {
                double a = wert(masse.hoehe(), "die Schenkellänge (Höhe)", form);
                double b = form == Profilform.WINKEL_GLEICHSCHENKLIG ? a : wert(masse.breite(), "die kürzere Schenkellänge (Breite)", form);
                double t = wert(masse.wandstaerke(), "die Schenkeldicke (Wandstärke)", form);
                if (t >= Math.min(a, b)) {
                    throw ungueltig("Die Schenkeldicke ist zu groß für die Schenkellänge.");
                }
                flaeche = t * (a + b - t);
                umfang = 2 * (a + b);
                hinweis = "Näherung ohne Abrundungen; Tabellenwerte liegen etwa 1 bis 2 % höher.";
            }
            case BLECH -> {
                double t = wert(masse.wandstaerke(), "die Blechdicke (Wandstärke)", form);
                return new Ergebnis(null, null, runde(t * rho, 3), null, Herkunft.BERECHNET, "Gewicht je Quadratmeter bei glattem Blech.");
            }
            default -> throw ungueltig("Für " + form.getAnzeigename() + " gibt es keine Berechnung. Das Gewicht bitte von Hand eintragen.");
        }
        return new Ergebnis(runde(flaeche / MM2_PRO_CM2, 3), runde(flaeche * rho / 1000.0, 4), null, runde(umfang / 1000.0, 4), Herkunft.BERECHNET, hinweis);
    }

    private Ergebnis ausTabelle(Profilform form, Map<Integer, Double> tab, double hoehe, double rho) {
        Double kg = tab.get((int) Math.round(hoehe));
        if (kg == null || Math.abs(hoehe - Math.rint(hoehe)) > 1e-9) {
            throw ungueltig("Für " + form.getAnzeigename() + " gibt es die Größe " + zahl(hoehe) + " nicht. Bitte die Nenngröße eintragen, zum Beispiel 200 für "
                    + kurz(form) + " 200.");
        }
        double kgMeter = kg * rho / STAHL_DICHTE.doubleValue();
        return new Ergebnis(runde(kgMeter / rho * 10, 3), runde(kgMeter, 4), null, null, Herkunft.NORMTABELLE,
                "Normwert aus der Tabelle (bitte mit dem Datenblatt des Lieferanten vergleichen); Oberfläche wird nicht berechnet.");
    }

    private static Map<Integer, Double> tabelleFuer(Profilform form) {
        return switch (form) {
            case IPE_PROFIL -> IPE;
            case HEA_PROFIL -> HEA;
            case HEB_PROFIL -> HEB;
            case U_PROFIL -> U_STAHL;
            default -> null;
        };
    }

    private static String kurz(Profilform form) {
        return switch (form) {
            case IPE_PROFIL -> "IPE";
            case HEA_PROFIL -> "HEA";
            case HEB_PROFIL -> "HEB";
            default -> "U";
        };
    }

    /** Äußerer Eckenradius kaltgefertigter Hohlprofile nach EN 10219: 2·t bis 6 mm, 2,5·t bis 10 mm, darüber 3·t. */
    private static double aussenradius(double t) {
        return t <= 6 ? 2 * t : t <= 10 ? 2.5 * t : 3 * t;
    }

    private static double wert(BigDecimal w, String was, Profilform form) {
        if (w == null || w.signum() <= 0) {
            throw ungueltig("Für " + form.getAnzeigename() + " fehlt " + was + ". Bitte bei den Maßen eintragen.");
        }
        return w.doubleValue();
    }

    private static BigDecimal runde(double wert, int stellen) {
        return BigDecimal.valueOf(wert).setScale(stellen, RoundingMode.HALF_UP);
    }

    private static String zahl(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d).replace('.', ',');
    }

    private static ResponseStatusException ungueltig(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
