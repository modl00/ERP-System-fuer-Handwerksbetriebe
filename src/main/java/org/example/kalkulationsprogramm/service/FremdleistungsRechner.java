package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Fremdleistung.TransportArt;

/**
 * Rechnet die Kosten einer Fremdleistung für eine Auftragsmenge aus (Stufe 4 von #135). Reihenfolge: Einheitspreis (mindestens der Mindestpreis), einmalige
 * Kosten, anteilige Kosten, Transport, sonstige Kosten, danach der Prozentzuschlag auf die Summe all dieser Kosten. Jede Kostenart wird auf Cent gerundet;
 * die Summe ist die Summe der gerundeten Beträge, damit die Aufstellung aufgeht.
 */
public final class FremdleistungsRechner {

    private static final BigDecimal HUNDERT = new BigDecimal("100");

    private FremdleistungsRechner() {
    }

    /** Eine Kostenart der Aufstellung, zum Beispiel {@code EINHEITSPREIS}: „120 kg × 1,20 €“ = 144,00. */
    public record Kostenart(String art, String text, BigDecimal betrag) { }

    /** {@code gewichtFehlt}: Transport je kg, aber ohne bekanntes Gewicht: Die Summe ist dann zu niedrig. */
    public record Ergebnis(List<Kostenart> kostenarten, BigDecimal summe, boolean gewichtFehlt) { }

    /**
     * @param menge     Menge dieses Auftrags in der Einheit der Fremdleistung (kg, m², m, Stück)
     * @param gewichtKg Gesamtgewicht des Auftrags in kg (für Transport je kg); {@code null} = unbekannt
     */
    public static Ergebnis rechne(Fremdleistung f, BigDecimal menge, BigDecimal gewichtKg) {
        List<Kostenart> arten = new ArrayList<>();
        BigDecimal summe = BigDecimal.ZERO;
        boolean gewichtFehlt = false;

        if (f.getPreisJeEinheit() != null) {
            BigDecimal preis = geld(f.getPreisJeEinheit().multiply(menge));
            String text = zahl(menge) + " " + f.getEinheit().kurz() + " × " + zahl(f.getPreisJeEinheit()) + " €";
            if (f.getMindestpreis() != null && preis.compareTo(f.getMindestpreis()) < 0) {
                preis = geld(f.getMindestpreis());
                text += ", angehoben auf den Mindestpreis";
                arten.add(new Kostenart("MINDESTPREIS", text, preis));
            } else {
                arten.add(new Kostenart("EINHEITSPREIS", text, preis));
            }
            summe = summe.add(preis);
        } else if (f.getMindestpreis() != null) {
            BigDecimal preis = geld(f.getMindestpreis());
            arten.add(new Kostenart("MINDESTPREIS", "Mindestpreis je Auftrag", preis));
            summe = summe.add(preis);
        }
        if (f.getEinmalBetrag() != null) {
            BigDecimal b = geld(f.getEinmalBetrag());
            arten.add(new Kostenart("EINMALIG", "einmal je Auftrag", b));
            summe = summe.add(b);
        }
        if (f.getAnteilBetrag() != null && f.getAnteilChargenmenge() != null && f.getAnteilChargenmenge().signum() > 0) {
            BigDecimal b = geld(f.getAnteilBetrag().multiply(menge).divide(f.getAnteilChargenmenge(), 6, RoundingMode.HALF_UP));
            arten.add(new Kostenart("ANTEILIG", zahl(f.getAnteilBetrag()) + " € je Charge à " + zahl(f.getAnteilChargenmenge()) + " " + f.getEinheit().kurz() + ", verteilt auf " + zahl(menge) + " " + f.getEinheit().kurz(), b));
            summe = summe.add(b);
        }
        TransportArt t = f.getTransportArt() == null ? TransportArt.KEINE : f.getTransportArt();
        if (t != TransportArt.KEINE && f.getTransportBetrag() != null) {
            BigDecimal b = null;
            String text = null;
            BigDecimal fahrten = BigDecimal.valueOf(Math.max(f.getTransportFahrten(), 1));
            switch (t) {
                case PAUSCHAL -> {
                    b = geld(f.getTransportBetrag());
                    text = "Transport pauschal";
                }
                case JE_KG -> {
                    BigDecimal kg = f.getEinheit() == Fremdleistung.Einheit.KG ? menge : gewichtKg;
                    if (kg == null) {
                        gewichtFehlt = true;
                    } else {
                        b = geld(f.getTransportBetrag().multiply(kg));
                        text = "Transport " + zahl(kg) + " kg × " + zahl(f.getTransportBetrag()) + " €";
                    }
                }
                case JE_FAHRT -> {
                    b = geld(f.getTransportBetrag().multiply(fahrten));
                    text = "Transport " + zahl(fahrten) + " Fahrt(en) × " + zahl(f.getTransportBetrag()) + " €";
                }
                case JE_KM -> {
                    BigDecimal km = f.getTransportKm() == null ? BigDecimal.ZERO : f.getTransportKm();
                    b = geld(f.getTransportBetrag().multiply(km).multiply(new BigDecimal("2")).multiply(fahrten));
                    text = "Transport " + zahl(km) + " km hin und zurück × " + zahl(fahrten) + " Fahrt(en) × " + zahl(f.getTransportBetrag()) + " €/km";
                }
                default -> { }
            }
            if (b != null) {
                arten.add(new Kostenart("TRANSPORT", text, b));
                summe = summe.add(b);
            }
        }
        if (f.getSonstigeBetrag() != null) {
            BigDecimal b = geld(f.getSonstigeBetrag());
            String name = f.getSonstigeBezeichnung() == null || f.getSonstigeBezeichnung().isBlank() ? "Sonstige Kosten" : f.getSonstigeBezeichnung().strip();
            arten.add(new Kostenart("SONSTIGE", name, b));
            summe = summe.add(b);
        }
        if (f.getProzentZuschlag() != null && f.getProzentZuschlag().signum() > 0) {
            BigDecimal b = geld(summe.multiply(f.getProzentZuschlag()).divide(HUNDERT, 6, RoundingMode.HALF_UP));
            arten.add(new Kostenart("PROZENT", zahl(f.getProzentZuschlag()) + " % auf " + zahl(summe) + " €", b));
            summe = summe.add(b);
        }
        return new Ergebnis(List.copyOf(arten), summe.setScale(2, RoundingMode.HALF_UP), gewichtFehlt);
    }

    private static BigDecimal geld(BigDecimal wert) {
        return wert.setScale(2, RoundingMode.HALF_UP);
    }

    private static String zahl(BigDecimal wert) {
        DecimalFormat format = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return format.format(wert.stripTrailingZeros());
    }
}
