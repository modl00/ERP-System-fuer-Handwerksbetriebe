package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Gespeicherte Vorgaben der Kostenrechnung (#308): Wagnis, Gewinn (beide in Prozent) und interne Zeit (Prozent der Sollstunden). Sie füllen die Felder der Seite
 * „Kostensätze“, damit sie nicht jedes Mal neu eingegeben werden müssen. Gespeichert wird nur auf ausdrücklichen Wunsch; an den Sätzen der Arbeitsgänge ändert sich dadurch nichts.
 * Dazu eine Schätzung der internen Zeit aus den gebuchten Stunden eines abgeschlossenen Jahres.
 */
@Service
@RequiredArgsConstructor
public class KostenrechnungVorgaben {

    static final String SCHLUESSEL_WAGNIS = "kostenrechnung.wagnis";
    static final String SCHLUESSEL_GEWINN = "kostenrechnung.gewinn";
    static final String SCHLUESSEL_INTERNE_ZEIT = "kostenrechnung.interne-zeit";
    private static final BigDecimal HUNDERT = new BigDecimal("100");

    private final SystemSettingRepository einstellungen;
    private final VerrechnungslohnService verrechnungslohn;

    /** {@code null} heißt: nicht gespeichert (die Seite nimmt dann ihre eigene Vorgabe). */
    public record Vorgaben(BigDecimal wagnis, BigDecimal gewinn, Integer interneZeit) { }

    /**
     * {@code prozent} ist {@code null}, wenn sich nichts schätzen lässt; {@code hinweis} sagt dann warum (oder ergänzt die Schätzung).
     */
    public record Schaetzung(int jahr, Integer prozent, BigDecimal interneStunden, BigDecimal sollstunden, String hinweis) { }

    @Transactional(readOnly = true)
    public Vorgaben lese() {
        return new Vorgaben(zahl(SCHLUESSEL_WAGNIS), zahl(SCHLUESSEL_GEWINN), ganzzahl(SCHLUESSEL_INTERNE_ZEIT));
    }

    /** Speichert die Vorgaben; ein leerer Wert löscht die jeweilige Vorgabe. */
    @Transactional
    public Vorgaben speichere(BigDecimal wagnis, BigDecimal gewinn, Integer interneZeit) {
        if (wagnis != null && (wagnis.signum() < 0 || wagnis.compareTo(HUNDERT) > 0)) {
            throw new IllegalArgumentException("Das Wagnis muss zwischen 0 und 100 Prozent liegen.");
        }
        if (gewinn != null && (gewinn.signum() < 0 || gewinn.compareTo(new BigDecimal("200")) > 0)) {
            throw new IllegalArgumentException("Der Gewinn muss zwischen 0 und 200 Prozent liegen.");
        }
        if (interneZeit != null && (interneZeit < 0 || interneZeit > 100)) {
            throw new IllegalArgumentException("Die interne Zeit muss zwischen 0 und 100 Prozent liegen.");
        }
        setze(SCHLUESSEL_WAGNIS, wagnis == null ? null : wagnis.stripTrailingZeros().toPlainString());
        setze(SCHLUESSEL_GEWINN, gewinn == null ? null : gewinn.stripTrailingZeros().toPlainString());
        setze(SCHLUESSEL_INTERNE_ZEIT, interneZeit == null ? null : String.valueOf(interneZeit));
        return lese();
    }

    /**
     * Schätzt die interne Zeit aus den gebuchten Stunden eines abgeschlossenen Jahres: interne Stunden (Buchungen auf interne Projekte und Garantie) im Verhältnis zu den
     * Sollstunden aller Mitarbeiter, so wie die Hochrechnung sie später abzieht. Für das laufende oder ein künftiges Jahr gibt es keine Schätzung.
     */
    @Transactional(readOnly = true)
    public Schaetzung schaetze(int jahr) {
        if (jahr >= Year.now().getValue()) {
            return new Schaetzung(jahr, null, null, null, "Für das laufende Jahr gibt es noch keine vollständigen Buchungen. Bitte ein abgeschlossenes Jahr wählen.");
        }
        VerrechnungslohnErgebnisDto e = verrechnungslohn.berechne(jahr, null);
        BigDecimal interne = BigDecimal.ZERO;
        BigDecimal soll = BigDecimal.ZERO;
        BigDecimal verkaeuflich = BigDecimal.ZERO;
        for (MitarbeiterStundenZeile z : e.getStundenzeilen()) {
            interne = interne.add(nz(z.getInterneStunden()));
            soll = soll.add(nz(z.getSollstunden()));
            verkaeuflich = verkaeuflich.add(nz(z.getVerkaeuflicheStunden()));
        }
        if (interne.signum() == 0 && verkaeuflich.signum() == 0) {
            return new Schaetzung(jahr, null, interne, soll, "Für " + jahr + " sind keine Zeitbuchungen vorhanden.");
        }
        if (soll.signum() <= 0) {
            return new Schaetzung(jahr, null, interne, soll, "Für " + jahr + " sind keine Sollstunden hinterlegt.");
        }
        int prozent = interne.multiply(HUNDERT).divide(soll, 0, RoundingMode.HALF_UP).intValue();
        String hinweis = interne.signum() == 0
                ? "Für " + jahr + " sind keine internen Stunden gebucht. Falls intern nie gebucht wird, ist 0 Prozent zu niedrig geschätzt."
                : null;
        return new Schaetzung(jahr, Math.min(100, prozent), interne.setScale(2, RoundingMode.HALF_UP), soll.setScale(2, RoundingMode.HALF_UP), hinweis);
    }

    private static BigDecimal nz(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }

    private BigDecimal zahl(String schluessel) {
        try {
            String w = wert(schluessel);
            return w == null ? null : new BigDecimal(w);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer ganzzahl(String schluessel) {
        try {
            String w = wert(schluessel);
            return w == null ? null : Integer.valueOf(w);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String wert(String schluessel) {
        return einstellungen.findById(schluessel).map(SystemSetting::getValue).filter(w -> !w.isBlank()).orElse(null);
    }

    private void setze(String schluessel, String wert) {
        if (wert == null) {
            einstellungen.findById(schluessel).ifPresent(einstellungen::delete);
            return;
        }
        SystemSetting s = einstellungen.findById(schluessel).orElseGet(SystemSetting::new);
        s.setKey(schluessel);
        s.setValue(wert);
        einstellungen.save(s);
    }
}
