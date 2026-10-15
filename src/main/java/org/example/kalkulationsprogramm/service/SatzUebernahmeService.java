package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Übernahme des errechneten Verrechnungssatzes in die Arbeitsgänge, <b>mit Vorschau</b> (Stufe 3b von #166). Nichts ändert sich ohne Vorschau und Auswahl:
 * Die Vorschau zeigt je Arbeitsgang den bisherigen und den neuen Satz des gewählten Jahres; übernommen werden nur die angekreuzten Arbeitsgänge. Der neue Satz wird beim
 * Übernehmen auf dem Server <b>noch einmal</b> berechnet und muss dem entsprechen, was die Vorschau zeigte, sonst wird nichts geschrieben.
 */
@Service
@RequiredArgsConstructor
public class SatzUebernahmeService {

    private static final Logger log = LoggerFactory.getLogger(SatzUebernahmeService.class);

    private final KostensatzService kostensaetze;
    private final ArbeitsgangRepository arbeitsgaenge;
    private final ArbeitsgangStundensatzRepository stundensaetze;

    /** Eine Zeile der Vorschau; {@code alt} ist {@code null}, wenn der Arbeitsgang für dieses Jahr noch keinen Satz hat. */
    public record Zeile(Long arbeitsgangId, String name, String abteilung, BigDecimal alt, BigDecimal neu, BigDecimal differenz, String art, String quelle, String gruppe) { }

    public record Vorschau(int jahr, BigDecimal neuerSatz, List<Zeile> zeilen) { }

    /** Die Zahlen haben sich seit der Vorschau geändert oder sind nicht berechenbar. */
    public static class UebernahmeFehler extends RuntimeException {
        public UebernahmeFehler(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public Vorschau vorschau(int jahr, Integer interneQuote, BigDecimal wagnis, BigDecimal gewinn) {
        KostensatzService.Ergebnis ergebnis = kostensaetze.berechne(jahr, interneQuote, wagnis, gewinn);
        BigDecimal neu = betriebsSatz(ergebnis);
        List<Zeile> zeilen = new ArrayList<>();
        for (Arbeitsgang ag : arbeitsgaenge.findAll()) {
            if (ag.isArchiviert()) {
                continue;
            }
            BigDecimal alt = stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(ag.getId(), jahr).map(ArbeitsgangStundensatz::getSatz).orElse(null);
            BigDecimal satz = satzFuer(ag, ergebnis, neu);
            BigDecimal differenz = alt == null || satz == null ? null : satz.subtract(alt);
            String art = satz == null ? "KEIN_SATZ" : alt == null ? "NEU" : differenz.signum() == 0 ? "GLEICH" : "AENDERT";
            zeilen.add(new Zeile(ag.getId(), ag.getBeschreibung(), ag.getAbteilung() == null ? null : ag.getAbteilung().getName(), alt, satz, differenz, art, ag.getSatzQuelle(), ag.getSatzGruppe()));
        }
        zeilen.sort(Comparator.comparing(Zeile::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new Vorschau(jahr, neu, zeilen);
    }

    /**
     * Schreibt den neuen Satz für die gewählten Arbeitsgänge. {@code erwarteterSatz} ist der Satz aus der Vorschau; weicht die neue Rechnung davon ab,
     * wird nichts geschrieben.
     *
     * @return Anzahl der geschriebenen Sätze (unveränderte werden übersprungen)
     */
    @Transactional
    public int uebernehme(int jahr, Integer interneQuote, BigDecimal wagnis, BigDecimal gewinn, BigDecimal erwarteterSatz, List<Long> arbeitsgangIds) {
        if (arbeitsgangIds == null || arbeitsgangIds.isEmpty()) {
            throw new UebernahmeFehler("Bitte mindestens einen Arbeitsgang auswählen.");
        }
        if (erwarteterSatz == null) {
            throw new UebernahmeFehler("Bitte zuerst die Vorschau laden.");
        }
        KostensatzService.Ergebnis ergebnis = kostensaetze.berechne(jahr, interneQuote, wagnis, gewinn);
        BigDecimal neu = betriebsSatz(ergebnis);
        if (neu.compareTo(erwarteterSatz.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new UebernahmeFehler("Die Zahlen haben sich seit der Vorschau geändert (jetzt " + neu + " € statt " + erwarteterSatz.setScale(2, RoundingMode.HALF_UP)
                    + " €). Bitte die Vorschau neu laden.");
        }
        Set<Long> gewaehlt = new HashSet<>(arbeitsgangIds);
        int geschrieben = 0;
        for (Arbeitsgang ag : arbeitsgaenge.findAllById(gewaehlt)) {
            if (ag.isArchiviert()) {
                continue;
            }
            BigDecimal satz = satzFuer(ag, ergebnis, neu);
            if (satz == null) {
                log.info("Kostenrechnung: Arbeitsgang {} übersprungen, für die gewählte Qualifikation gibt es keinen Satz", ag.getId());
                continue;
            }
            Optional<ArbeitsgangStundensatz> bisher = stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(ag.getId(), jahr);
            if (bisher.isPresent() && bisher.get().getSatz() != null && bisher.get().getSatz().compareTo(satz) == 0) {
                continue;
            }
            ArbeitsgangStundensatz eintrag = bisher.orElseGet(ArbeitsgangStundensatz::new);
            eintrag.setArbeitsgang(ag);
            eintrag.setJahr(jahr);
            eintrag.setSatz(satz);
            stundensaetze.save(eintrag);
            geschrieben++;
        }
        log.info("Kostenrechnung: Verrechnungssätze für {} Arbeitsgänge im Jahr {} übernommen (Betrieb {} €)", geschrieben, jahr, neu);
        return geschrieben;
    }

    /**
     * Der Satz für einen Arbeitsgang: folgt er einer Qualifikation (Auszubildender, Facharbeiter, Meister), gilt deren Verrechnungssatz, sonst der des ganzen Betriebs.
     * {@code null}, wenn es für die gewählte Qualifikation keinen Satz gibt (kein Mitarbeiter mit verkäuflichen Stunden); dann wird nichts geschrieben.
     */
    static BigDecimal satzFuer(Arbeitsgang ag, KostensatzService.Ergebnis ergebnis, BigDecimal betriebsSatz) {
        if (!ag.folgtVerrechnungssatz() || ag.getSatzGruppe() == null || ag.getSatzGruppe().isBlank()) {
            return betriebsSatz;
        }
        String name;
        try {
            name = org.example.kalkulationsprogramm.domain.Qualifikation.valueOf(ag.getSatzGruppe()).getBezeichnung();
        } catch (IllegalArgumentException e) {
            return null;
        }
        return ergebnis.gruppen().stream()
                .filter(z -> name.equals(z.name()) && z.verrechnungssatz() != null && z.verrechnungssatz().signum() > 0)
                .map(z -> z.verrechnungssatz().setScale(2, RoundingMode.HALF_UP))
                .findFirst().orElse(null);
    }

    private BigDecimal betriebsSatz(KostensatzService.Ergebnis ergebnis) {
        BigDecimal satz = ergebnis.betrieb().verrechnungssatz();
        if (satz == null || satz.signum() <= 0) {
            throw new UebernahmeFehler("Für dieses Jahr lässt sich kein Verrechnungssatz berechnen (keine verkäuflichen Stunden oder keine Kosten).");
        }
        return satz.setScale(2, RoundingMode.HALF_UP);
    }
}
