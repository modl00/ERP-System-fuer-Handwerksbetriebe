package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHinweis;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ArtikelPositionsPreisService {

    @Autowired(required = false)
    private AufschlagService aufschlaege;

    private static final BigDecimal HUNDERT = new BigDecimal("100");

    /** Vorschlag fuer eine Dokumentposition. {@code einzelpreis} ist {@code null}, wenn er nicht ermittelbar war. */
    public record ArtikelPositionsVorschlag(String einheit, BigDecimal einzelpreis, ArtikelPreisHinweis hinweis) {}

    /**
     * Vorschlag ohne Preiskontext: Grundlage ist der guenstigste aktuelle
     * Lieferantenpreis ueber alle Lieferanten.
     *
     * <p>Fuer Aufrufer, die selbst nicht wissen, welcher Preis gilt. Wer den
     * massgeblichen Preis bereits ermittelt hat - etwa weil nach einem
     * Lieferanten gefiltert wurde -, nimmt
     * {@link #berechne(Artikel, LieferantenArtikelPreise)}.
     */
    public ArtikelPositionsVorschlag berechne(Artikel artikel) {
        return berechne(artikel, artikel == null
                ? null
                : artikel.getGuenstigsterPreis().orElse(null));
    }

    /**
     * Vorschlag auf Basis eines bereits ermittelten Preises.
     *
     * <p>Der Aufrufer bestimmt, welcher Preis gilt - und nur der. Das ist keine
     * Bequemlichkeit, sondern der Kern: Filtert die Suche nach einem
     * Lieferanten, darf der Vorschlag nicht heimlich den (immer guenstigeren)
     * Preis eines abgewaehlten Lieferanten heranziehen. Sonst wird die Marge auf
     * einem verbindlichen Angebot zu niedrig angesetzt. Dasselbe gilt fuer
     * Preiszeilen ohne Lieferant: Wer sie aus der Anzeige ausschliesst, muss sie
     * auch aus dem Vorschlag ausschliessen.
     *
     * @param preis der massgebliche Preis, oder {@code null}, wenn es fuer
     *              diese Zeile keinen gibt - dann {@link ArtikelPreisHinweis#KEIN_PREIS}
     */
    public ArtikelPositionsVorschlag berechne(Artikel artikel, LieferantenArtikelPreise preis) {
        if (artikel == null) {
            return new ArtikelPositionsVorschlag("Stk", null, ArtikelPreisHinweis.KEIN_PREIS);
        }

        Verrechnungseinheit einheit = artikel.getVerrechnungseinheit();
        BigDecimal einkauf = preis != null ? preis.getPreis() : null;

        // Ohne Preis ist jede Umrechnung gegenstandslos. Die Einheit bestimmen
        // wir trotzdem, damit die Position mit der richtigen Einheit einsteigt
        // und der Bediener nur noch den Preis nachtraegt.
        if (einkauf == null) {
            return new ArtikelPositionsVorschlag(
                    positionsEinheit(artikel, einheit), null, ArtikelPreisHinweis.KEIN_PREIS);
        }

        BigDecimal faktor = umrechnungsfaktor(artikel, einheit);
        if (faktor == null) {
            // Nur bei KILOGRAMM ohne jedes Gewicht erreichbar.
            return new ArtikelPositionsVorschlag("lfm", null, ArtikelPreisHinweis.KEIN_GEWICHT);
        }

        BigDecimal basis = einkauf.multiply(faktor);
        BigDecimal aufschlag = artikel.getVerkaufsaufschlagProzent();
        if (aufschlag == null) {
            return new ArtikelPositionsVorschlag(
                    positionsEinheit(artikel, einheit),
                    basis.setScale(2, RoundingMode.HALF_UP),
                    ArtikelPreisHinweis.KEIN_AUFSCHLAG);
        }

        BigDecimal endpreis = basis
                .multiply(BigDecimal.ONE.add(aufschlag.divide(HUNDERT, 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
        return new ArtikelPositionsVorschlag(
                positionsEinheit(artikel, einheit), endpreis, ArtikelPreisHinweis.OK);
    }

    /** Einheit, in der die Position gefuehrt wird - unabhaengig davon, ob ein Preis ermittelbar war. */
    private String positionsEinheit(Artikel artikel, Verrechnungseinheit einheit) {
        if (einheit == null) return "Stk";
        return switch (einheit) {
            case LAUFENDE_METER -> "lfm";
            case QUADRATMETER -> "m²";
            case KILOGRAMM -> istBlech(artikel) ? "m²" : "lfm";
            case STUECK -> "Stk";
            case LITER -> "l";
        };
    }

    /**
     * Womit der gespeicherte Preis multipliziert wird, um auf die
     * Positionseinheit zu kommen. {@code null} bedeutet: nicht ermittelbar.
     */
    private BigDecimal umrechnungsfaktor(Artikel artikel, Verrechnungseinheit einheit) {
        if (einheit != Verrechnungseinheit.KILOGRAMM) {
            return BigDecimal.ONE;
        }
        if (!(artikel instanceof ArtikelWerkstoffe werkstoff)) {
            return null;
        }
        if (werkstoff.getMasse() != null && werkstoff.getMasse().signum() > 0) {
            return werkstoff.getMasse();
        }
        if (werkstoff.getMassePerQm() != null && werkstoff.getMassePerQm().signum() > 0) {
            return werkstoff.getMassePerQm();
        }
        return null;
    }

    /** Ein Blech traegt kein Gewicht je Meter, sondern nur eines je Quadratmeter. */
    private boolean istBlech(Artikel artikel) {
        if (!(artikel instanceof ArtikelWerkstoffe werkstoff)) return false;
        boolean ohneMeterGewicht = werkstoff.getMasse() == null || werkstoff.getMasse().signum() <= 0;
        boolean mitQmGewicht = werkstoff.getMassePerQm() != null && werkstoff.getMassePerQm().signum() > 0;
        return ohneMeterGewicht && mitQmGewicht;
    }

    public ArtikelPositionsVorschlag berechneZuschnitt(Artikel artikel, java.math.BigDecimal kgJeStueck, java.math.BigDecimal m2JeStueck) {
        return berechneZuschnittIntern(artikel, artikel == null ? null : artikel.getGuenstigsterPreis().orElse(null), kgJeStueck, m2JeStueck);
    }

    private ArtikelPositionsVorschlag berechneZuschnittIntern(Artikel artikel, LieferantenArtikelPreise preis, java.math.BigDecimal kgJeStueck, java.math.BigDecimal m2JeStueck) {
        if (artikel == null) return new ArtikelPositionsVorschlag("Stk", null, ArtikelPreisHinweis.KEIN_PREIS);
        Verrechnungseinheit einheit = artikel.getVerrechnungseinheit();
        java.math.BigDecimal einkauf = preis != null ? preis.getPreis() : null;
        if (einkauf == null) return new ArtikelPositionsVorschlag("Stk", null, ArtikelPreisHinweis.KEIN_PREIS);
        java.math.BigDecimal faktor = zuschnittFaktor(einheit, kgJeStueck, m2JeStueck);
        if (faktor == null || faktor.signum() <= 0) return new ArtikelPositionsVorschlag("Stk", null, ArtikelPreisHinweis.KEIN_GEWICHT);
        java.math.BigDecimal basis = einkauf.multiply(faktor);
        java.math.BigDecimal aufschlag = aufschlaege == null ? artikel.getVerkaufsaufschlagProzent() : aufschlaege.wirksamer(artikel).prozent();
        if (aufschlag == null) return new ArtikelPositionsVorschlag("Stk", basis.setScale(2, java.math.RoundingMode.HALF_UP), ArtikelPreisHinweis.KEIN_AUFSCHLAG);
        java.math.BigDecimal endpreis = basis.multiply(java.math.BigDecimal.ONE.add(aufschlag.divide(HUNDERT, 6, java.math.RoundingMode.HALF_UP))).setScale(2, java.math.RoundingMode.HALF_UP);
        return new ArtikelPositionsVorschlag("Stk", endpreis, ArtikelPreisHinweis.OK);
    }

    private static java.math.BigDecimal zuschnittFaktor(Verrechnungseinheit einheit, java.math.BigDecimal kgJeStueck, java.math.BigDecimal m2JeStueck) {
        if (einheit == null) return java.math.BigDecimal.ONE;
        return switch (einheit) {
            case KILOGRAMM -> kgJeStueck;
            case QUADRATMETER -> m2JeStueck;
            case STUECK -> java.math.BigDecimal.ONE;
            default -> null;
        };
    }
}
