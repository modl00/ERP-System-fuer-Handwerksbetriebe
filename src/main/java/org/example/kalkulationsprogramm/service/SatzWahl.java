package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;

/**
 * Die eine Regel, nach der eine Zeitbuchung ihren Satz bekommt (Kostenrechnung Stufe 1, Issue #166). Vorher gab es fünf Wege (Handy-Start, Nachtrag, Zeitverwaltung am PC,
 * Projekt anlegen, Plan-Zeiten) mit verschiedener Jahres- und Ersatzlogik; jetzt gilt überall dieselbe Kette:
 * <ol>
 *   <li>der Satz für das <b>Jahr der Buchung</b>,</li>
 *   <li>sonst der nächste vorhandene Satz eines <b>späteren</b> Jahres,</li>
 *   <li>sonst der neueste Satz überhaupt (ein früheres Jahr).</li>
 * </ol>
 * Die Buchung hält den Betrag danach <b>selbst fest</b> ({@link Zeitbuchung#getVerrechnungssatzWert()}): Eine spätere Änderung am Satz verändert sie nicht mehr.
 */
public final class SatzWahl {

    private SatzWahl() {
    }

    /** Der Satz für diesen Arbeitsgang im Jahr der Buchung, mit den Ersatzstufen; leer, wenn der Arbeitsgang gar keinen Satz hat. */
    public static Optional<ArbeitsgangStundensatz> finde(ArbeitsgangStundensatzRepository repository, Long arbeitsgangId, int jahr) {
        if (arbeitsgangId == null) {
            return Optional.empty();
        }
        return repository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, jahr)
                .or(() -> repository.findTopByArbeitsgangIdAndJahrGreaterThanEqualOrderByJahrAsc(arbeitsgangId, jahr))
                .or(() -> repository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgangId));
    }

    /**
     * Beim Ändern einer vorhandenen Buchung: Muss der Satz neu gewählt werden? Ja, wenn der Arbeitsgang gewechselt hat, die Buchung noch keinen Satz hat oder inzwischen ein
     * Satz für das genaue Jahr der Buchung vorhanden ist, die Buchung aber mit dem eines anderen Jahres bewertet wurde. Sonst bleibt der festgehaltene Satz, auch wenn
     * sich die Zeiten ändern. Leer = behalten (oder es gibt keinen Satz).
     */
    public static Optional<ArbeitsgangStundensatz> neuFuerAenderung(ArbeitsgangStundensatzRepository repository, Zeitbuchung buchung, Long arbeitsgangId, int jahr) {
        ArbeitsgangStundensatz alt = buchung.getArbeitsgangStundensatz();
        boolean gewechselt = alt == null || alt.getArbeitsgang() == null || !alt.getArbeitsgang().getId().equals(arbeitsgangId) || buchung.getVerrechnungssatzWert() == null;
        if (gewechselt) {
            return finde(repository, arbeitsgangId, jahr);
        }
        if (alt.getJahr() != jahr) {
            return repository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgangId, jahr);
        }
        return Optional.empty();
    }

    /** Hängt den Satz an die Buchung und hält seinen Betrag fest. */
    public static void anwenden(Zeitbuchung buchung, ArbeitsgangStundensatz satz) {
        buchung.setArbeitsgangStundensatz(satz);
        buchung.setVerrechnungssatzWert(satz == null ? null : satz.getSatz());
    }

    /** Der Satz, mit dem die Buchung bewertet wird: der festgehaltene Betrag, sonst (ältere Buchungen) der Betrag der Satzzeile, auf die sie zeigt; {@code null} ohne beides. */
    public static BigDecimal wertVon(Zeitbuchung buchung) {
        if (buchung.getVerrechnungssatzWert() != null) {
            return buchung.getVerrechnungssatzWert();
        }
        return buchung.getArbeitsgangStundensatz() == null ? null : buchung.getArbeitsgangStundensatz().getSatz();
    }
}
