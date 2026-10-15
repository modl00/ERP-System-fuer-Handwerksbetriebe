package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Der Kostensatz einer Maschine je Laufstunde (Stufe 2 von #166), als reine Rechnung ohne Datenbank:
 * <ul>
 *   <li>Abschreibung = (Anschaffung − Restwert) ÷ Nutzungsdauer ÷ Laufstunden je Jahr</li>
 *   <li>Zins = ((Anschaffung + Restwert) ÷ 2) × Zinssatz ÷ Laufstunden je Jahr</li>
 *   <li>Wartung, Platz, Versicherung = Jahreskosten ÷ Laufstunden je Jahr</li>
 *   <li>Energie = kW × Auslastung × Strompreis (je Stunde, braucht keine Laufstunden)</li>
 * </ul>
 * Fehlende Angaben zählen als 0. Brauchen Teile die Laufstunden oder die Nutzungsdauer und fehlen sie, ist der Satz nicht berechenbar; der Hinweis sagt, was fehlt.
 */
public final class MaschinenKostenRechner {

    private static final BigDecimal HUNDERT = new BigDecimal("100");
    private static final BigDecimal ZWEI = new BigDecimal("2");

    private MaschinenKostenRechner() {
    }

    /** Die Angaben zur Rechnung; alles darf fehlen. */
    public record Eingabe(BigDecimal anschaffungswert, BigDecimal restwert, BigDecimal nutzungsdauerJahre, BigDecimal zinssatzProzent, BigDecimal wartungJeJahr,
            BigDecimal platzJeJahr, BigDecimal versicherungJeJahr, BigDecimal leistungKw, BigDecimal strompreisJeKwh, BigDecimal auslastungProzent, BigDecimal laufstundenJeJahr) { }

    /** Die einzelnen Teile je Laufstunde (auf Cent) und ihre Summe; {@code summe} ist {@code null}, wenn nicht berechenbar, dann steht in {@code hinweis} warum. */
    public record Aufstellung(BigDecimal abschreibung, BigDecimal zins, BigDecimal wartung, BigDecimal platz, BigDecimal versicherung, BigDecimal energie, BigDecimal summe, String hinweis) { }

    public static Aufstellung rechne(Eingabe e) {
        BigDecimal anschaffung = nz(e.anschaffungswert());
        BigDecimal rest = nz(e.restwert());
        BigDecimal stunden = nz(e.laufstundenJeJahr());
        BigDecimal energie = nz(e.leistungKw()).multiply(auslastung(e.auslastungProzent())).multiply(nz(e.strompreisJeKwh())).setScale(2, RoundingMode.HALF_UP);

        boolean abschreibungNoetig = anschaffung.compareTo(rest) > 0;
        boolean jahreskosten = abschreibungNoetig || nz(e.zinssatzProzent()).signum() > 0 && anschaffung.add(rest).signum() > 0 || nz(e.wartungJeJahr()).signum() > 0
                || nz(e.platzJeJahr()).signum() > 0 || nz(e.versicherungJeJahr()).signum() > 0;
        String hinweis = null;
        if (jahreskosten && stunden.signum() <= 0) {
            hinweis = "Die Laufstunden je Jahr fehlen: Ohne sie lassen sich die Jahreskosten nicht auf eine Stunde verteilen.";
        } else if (abschreibungNoetig && nz(e.nutzungsdauerJahre()).signum() <= 0) {
            hinweis = "Die Nutzungsdauer fehlt: Ohne sie lässt sich die Abschreibung nicht berechnen.";
        }
        if (hinweis != null) {
            return new Aufstellung(null, null, null, null, null, energie, null, hinweis);
        }
        BigDecimal abschreibung = abschreibungNoetig
                ? anschaffung.subtract(rest).divide(e.nutzungsdauerJahre(), 10, RoundingMode.HALF_UP).divide(stunden, 10, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal zins = anschaffung.add(rest).divide(ZWEI, 10, RoundingMode.HALF_UP).multiply(nz(e.zinssatzProzent())).divide(HUNDERT, 10, RoundingMode.HALF_UP)
                .divide(stunden.signum() > 0 ? stunden : BigDecimal.ONE, 10, RoundingMode.HALF_UP);
        BigDecimal geteilt = stunden.signum() > 0 ? stunden : BigDecimal.ONE;
        BigDecimal wartung = nz(e.wartungJeJahr()).divide(geteilt, 10, RoundingMode.HALF_UP);
        BigDecimal platz = nz(e.platzJeJahr()).divide(geteilt, 10, RoundingMode.HALF_UP);
        BigDecimal versicherung = nz(e.versicherungJeJahr()).divide(geteilt, 10, RoundingMode.HALF_UP);
        // Die Summe wird aus den ungerundeten Teilen gebildet und erst dann auf Cent gerundet; die Teile werden einzeln gerundet gezeigt
        BigDecimal summe = abschreibung.add(zins).add(wartung).add(platz).add(versicherung).add(energie).setScale(2, RoundingMode.HALF_UP);
        return new Aufstellung(cent(abschreibung), cent(zins), cent(wartung), cent(platz), cent(versicherung), energie, summe, null);
    }

    private static BigDecimal auslastung(BigDecimal prozent) {
        return prozent == null ? BigDecimal.ONE : prozent.divide(HUNDERT, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal cent(BigDecimal wert) {
        return wert.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }
}
