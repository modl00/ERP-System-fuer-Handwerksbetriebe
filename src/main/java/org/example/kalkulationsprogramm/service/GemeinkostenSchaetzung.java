package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Schätzung der Gemeinkosten (Jahresbetrag), damit die Kostenrechnung (#166, #237) auch bei einer frischen Installation einen Satz liefert: Echte Gemeinkosten kommen nur aus
 * Belegen auf Fixkosten-Kostenstellen und stehen anfangs auf null. <b>Regel:</b> Für das laufende und spätere Jahre gilt der <b>höhere</b> Wert aus echten Kosten und
 * Schätzung; für vergangene Jahre zählen nur die echten Kosten. So wird die Schätzung von selbst abgelöst, sobald die Belege sie übersteigen, und ein Satz ist nie zu niedrig, nur weil
 * noch Belege fehlen. Gespeichert in {@code system_setting} (kein Datenbank-Umbau), leer = keine Schätzung.
 */
@Service
public class GemeinkostenSchaetzung {

    public static final String SCHLUESSEL = "kostenrechnung.gemeinkosten.schaetzung";
    public static final BigDecimal MAXIMUM = new BigDecimal("100000000");
    private static final String BESCHREIBUNG = "Geschätzte Gemeinkosten pro Jahr (Euro), gilt bis echte Belege sie übersteigen";

    /** Ergebnis der Auswahl: der verwendete Betrag und ob dafür die Schätzung gilt. */
    public record Wahl(BigDecimal verwendet, BigDecimal ist, BigDecimal schaetzung, boolean schaetzungGilt) { }

    private final SystemSettingsService einstellungen;

    @Autowired
    public GemeinkostenSchaetzung(SystemSettingsService einstellungen) {
        this.einstellungen = einstellungen;
    }

    /** Die gespeicherte Schätzung; {@code null}, wenn keine eingetragen ist oder der Wert nicht lesbar ist. */
    public BigDecimal lese() {
        return parse(einstellungen.get(SCHLUESSEL, ""));
    }

    /** Speichert den Jahresbetrag; {@code null} löscht die Schätzung. Prüft Betrag ≥ 0 und die Obergrenze. */
    public void speichere(BigDecimal betrag) {
        if (betrag == null) {
            einstellungen.save(SCHLUESSEL, "", BESCHREIBUNG);
            return;
        }
        if (betrag.signum() < 0) {
            throw new IllegalArgumentException("Die geschätzten Gemeinkosten dürfen nicht negativ sein.");
        }
        if (betrag.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("Die geschätzten Gemeinkosten sind zu hoch (höchstens 100.000.000 Euro im Jahr).");
        }
        einstellungen.save(SCHLUESSEL, betrag.setScale(2, RoundingMode.HALF_UP).toPlainString(), BESCHREIBUNG);
    }

    /** Wählt für {@code jahr} zwischen echten Kosten und der Schätzung. Vergangene Jahre: immer nur die echten Kosten. */
    public Wahl waehle(int jahr, BigDecimal ist) {
        return waehle(jahr, ist, lese(), Year.now().getValue());
    }

    static Wahl waehle(int jahr, BigDecimal ist, BigDecimal schaetzung, int aktuellesJahr) {
        BigDecimal echt = ist == null ? BigDecimal.ZERO : ist;
        if (schaetzung == null || jahr < aktuellesJahr || schaetzung.compareTo(echt) <= 0) {
            return new Wahl(echt, echt, schaetzung, false);
        }
        return new Wahl(schaetzung, echt, schaetzung, true);
    }

    static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal wert = new BigDecimal(text.strip().replace(',', '.'));
            return wert.signum() < 0 ? null : wert;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
