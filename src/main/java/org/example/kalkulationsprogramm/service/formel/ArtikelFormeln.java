package org.example.kalkulationsprogramm.service.formel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Zuschnitt-Formeln am Artikel (#228): Für Teile mit wechselnden Maßen (Blechzuschnitt, Abschnitt von Meterware) gibt es bis zu {@value ArbeitsgangFormeln#MAX_FELDER}
 * Eingabefelder (zum Beispiel Länge, Breite, Dicke in mm) und je eine Formel für das <b>Gewicht in kg</b> (Verzinken) und die <b>Außenfläche in m²</b> (Pulverbeschichten)
 * je Stück. In der Leistung trägt man die Maße ein, das Programm rechnet Gewicht, Fläche und, bei Preis je kg oder je m², den Preis je Stück.
 *
 * <p>Eingebaute Werte, die in den Formeln ohne Eingabe stehen: {@code dichte} (kg/dm³ des Werkstoffs, ohne Werkstoff Stahl 7,85) sowie {@code kg_je_meter} und
 * {@code m2_je_meter} (aus den Stammdaten des Artikels, falls vorhanden). Gerechnet wird wie überall mit dem sicheren {@link FormelRechner}: Es wird nie Code ausgeführt.</p>
 */
public final class ArtikelFormeln {

    public static final String DICHTE = "dichte";
    public static final String KG_JE_METER = "kg_je_meter";
    public static final String M2_JE_METER = "m2_je_meter";
    /** Die Namen, die als eigenes Feld nicht vergeben werden dürfen. */
    static final Set<String> EINGEBAUT = Set.of(DICHTE, KG_JE_METER, M2_JE_METER);

    private static final BigDecimal STAHL = new BigDecimal("7.85");
    private static final BigDecimal MAX_ERGEBNIS = new BigDecimal("1000000");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Felder und die beiden Formeln; {@code flaeche} darf fehlen (dann ist die Fläche unbekannt). */
    public record Formel(List<Feld> felder, String gewicht, String flaeche) {
    }

    /** Gewicht in kg und Außenfläche in m² je Stück; {@code m2} ist {@code null} ohne Flächenformel, {@code kg} ohne Gewichtsformel. */
    public record Ergebnis(BigDecimal kg, BigDecimal m2) {
    }

    private ArtikelFormeln() {
    }

    /** Hat der Artikel eine Formel (für Gewicht oder Fläche)? */
    public static boolean hat(ArtikelWerkstoffe a) {
        return a != null && (nichtLeer(a.getFormelGewicht()) || nichtLeer(a.getFormelFlaeche()));
    }

    public static Formel lese(ArtikelWerkstoffe a) {
        if (a == null) {
            return new Formel(List.of(), null, null);
        }
        List<Feld> felder = List.of();
        if (nichtLeer(a.getFormelFelderJson())) {
            try {
                felder = JSON.readValue(a.getFormelFelderJson(), new TypeReference<List<Feld>>() {
                });
            } catch (Exception e) {
                felder = List.of();
            }
        }
        return new Formel(felder, a.getFormelGewicht(), a.getFormelFlaeche());
    }

    /**
     * Prüft die Eingaben und gibt sie bereinigt zurück. Fehler kommen als {@link FormelException} im Klartext. Ohne beide Formeln gilt der Artikel als ohne Formel
     * (Felder werden verworfen).
     */
    public static Formel pruefe(Formel f) {
        String gewicht = leerZuNull(f == null ? null : f.gewicht());
        String flaeche = leerZuNull(f == null ? null : f.flaeche());
        if (gewicht == null && flaeche == null) {
            return new Formel(List.of(), null, null);
        }
        Set<String> namen = new LinkedHashSet<>();
        List<Feld> felder = ArbeitsgangFormeln.pruefeFelder(f.felder(), namen, EINGEBAUT);
        Set<String> bekannt = new LinkedHashSet<>(namen);
        bekannt.addAll(EINGEBAUT);
        if (gewicht != null) {
            FormelRechner.pruefe(gewicht, bekannt);
        }
        if (flaeche != null) {
            FormelRechner.pruefe(flaeche, bekannt);
        }
        return new Formel(felder, gewicht, flaeche);
    }

    /** Die Felder als JSON für die Spalte am Artikel ({@code null} ohne Felder). */
    public static String felderAlsJson(List<Feld> felder) {
        return ArbeitsgangFormeln.felderAlsJson(felder);
    }

    /**
     * Rechnet Gewicht und Fläche je Stück. Fehlende Werte nehmen den Standardwert des Feldes; hat ein Feld weder Wert noch Standard, kommt ein Klartext. Die eingebauten Werte
     * kommen aus {@code eingebaut} (Schlüssel siehe Konstanten). Ergebnisse dürfen nicht negativ sein.
     */
    public static Ergebnis berechne(Formel f, Map<String, BigDecimal> eingabe, Map<String, BigDecimal> eingebaut) {
        Map<String, BigDecimal> werte = new LinkedHashMap<>(ArbeitsgangFormeln.wirksameWerte(new ArbeitsgangFormeln.Formel(f.felder(), f.gewicht(), null), eingabe));
        for (Feld x : f.felder()) {
            if (werte.get(x.name().toLowerCase(Locale.ROOT)) == null) {
                throw new FormelException("Bitte einen Wert für „" + x.bezeichnung() + "“ eintragen.");
            }
        }
        if (eingebaut != null) {
            werte.putAll(eingebaut);
        }
        BigDecimal kg = nichtLeer(f.gewicht()) ? ergebnis(f.gewicht(), werte, "Das Gewicht", 4) : null;
        BigDecimal m2 = nichtLeer(f.flaeche()) ? ergebnis(f.flaeche(), werte, "Die Fläche", 4) : null;
        return new Ergebnis(kg, m2);
    }

    /** Die eingebauten Werte eines Artikels: Dichte (Werkstoff, sonst Stahl) und, falls bekannt, Gewicht und Außenfläche je Meter. */
    public static Map<String, BigDecimal> eingebaut(BigDecimal dichte, BigDecimal kgJeMeter, BigDecimal m2JeMeter) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put(DICHTE, dichte != null && dichte.signum() > 0 ? dichte : STAHL);
        if (kgJeMeter != null && kgJeMeter.signum() > 0) {
            m.put(KG_JE_METER, kgJeMeter);
        }
        if (m2JeMeter != null && m2JeMeter.signum() > 0) {
            m.put(M2_JE_METER, m2JeMeter);
        }
        return m;
    }

    private static BigDecimal ergebnis(String formel, Map<String, BigDecimal> werte, String was, int stellen) {
        BigDecimal wert;
        try {
            wert = FormelRechner.rechne(formel, werte);
        } catch (FormelException e) {
            // Ein eingebauter Wert, der am Artikel fehlt, ist die häufigste Ursache: den Namen verständlich machen
            String text = e.getMessage();
            if (text != null && (text.contains(KG_JE_METER) || text.contains(M2_JE_METER))) {
                throw new FormelException(was + " braucht das Gewicht bzw. die Außenfläche je Meter, die am Artikel nicht eingetragen sind. Bitte dort zuerst eintragen.");
            }
            throw e;
        }
        if (wert.signum() < 0) {
            throw new FormelException(was + " wird negativ. Bitte die Werte prüfen.");
        }
        if (wert.compareTo(MAX_ERGEBNIS) > 0) {
            throw new FormelException(was + " ist unrealistisch groß. Bitte die Werte prüfen (Maße in mm).");
        }
        return wert.setScale(stellen, RoundingMode.HALF_UP);
    }

    private static boolean nichtLeer(String s) {
        return s != null && !s.isBlank();
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
