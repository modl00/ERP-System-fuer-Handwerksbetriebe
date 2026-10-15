package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungNummernkreis;
import org.example.kalkulationsprogramm.repository.wartung.WartungNummernkreisRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Vergibt Wartungsnummern im Format {@code WA-2026-1001}. Der Zähler beginnt jedes Kalenderjahr bei
 * 1001. Die Nummer folgt dem Jahr der Erstellung, nicht dem Datum des Protokolls: Ein nachgetragenes
 * altes Protokoll bekommt so keine Nummer aus der Vergangenheit, die die Reihenfolge durcheinanderbringt.
 *
 * <p>Es gibt genau eine Zählerzeile (Migration V900). Sie wird beim Hochzählen gesperrt
 * ({@code SELECT ... FOR UPDATE}), ein Jahreswechsel setzt den Zähler unter derselben Sperre zurück.
 * Zwei gleichzeitige Anlagen bekommen deshalb nie dieselbe Nummer, und es braucht keine zweite
 * Datenbankverbindung (eine verschachtelte Transaktion würde den Verbindungspool unter Last leerlaufen
 * lassen).</p>
 *
 * <p>Die Nummer ist erst vergeben, wenn die aufrufende Transaktion abgeschlossen ist. Bricht sie ab,
 * wird der Zähler zurückgerollt und die Nummer bleibt frei: Es entstehen keine Lücken.</p>
 */
@Service
@AllArgsConstructor
public class WartungNummernService {

    static final String PRAEFIX = "WA";
    static final int ERSTER_WERT = 1001;
    /** Obergrenze fürs Überspringen vergebener Nummern (zum Beispiel nach einer Übernahme mit vielen alten Nummern). */
    static final int MAX_VERSUCHE = 100_000;

    private final WartungNummernkreisRepository repository;
    private final WartungProtokollRepository protokolle;

    /**
     * Muss innerhalb einer Transaktion laufen, damit die Sperre bis zu deren Ende hält.
     *
     * @param heute das aktuelle Datum; bestimmt das Jahr der Nummer
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String naechsteNummer(LocalDate heute) {
        int jahr = heute.getYear();
        WartungNummernkreis kreis = repository.findMitSperre(PRAEFIX)
                .orElseGet(() -> legeKreisAn(jahr));
        if (kreis.getJahr() < jahr) {
            kreis.setJahr(jahr);
            kreis.setLetzterWert(ERSTER_WERT - 1);
        }
        // Schon vergebene Nummern überspringen: Die Übernahme aus HandwerkPro bringt eigene Nummern im gleichen Format mit (zum Beispiel WA-2026-1005),
        // der Zähler kennt sie nicht. Ohne Überspringen scheitert jede neue Prüfung an der Eindeutigkeit, und weil die Anlage zurückgerollt wird, bleibt
        // der Zähler stehen und es scheitert immer wieder dieselbe Nummer.
        String nummer;
        int versuche = 0;
        do {
            kreis.setLetzterWert(kreis.getLetzterWert() + 1);
            nummer = PRAEFIX + "-" + kreis.getJahr() + "-" + kreis.getLetzterWert();
            if (++versuche > MAX_VERSUCHE) {
                throw new IllegalStateException("Es ließ sich keine freie Wartungsnummer finden.");
            }
        } while (protokolle.existsByNummer(nummer));
        return nummer;
    }

    /**
     * Notfall, falls die Zählerzeile fehlt (sie wird von der Migration angelegt). Passiert nur, wenn
     * jemand sie von Hand gelöscht hat.
     */
    private WartungNummernkreis legeKreisAn(int jahr) {
        WartungNummernkreis neu = new WartungNummernkreis();
        neu.setPraefix(PRAEFIX);
        neu.setJahr(jahr);
        neu.setLetzterWert(ERSTER_WERT - 1);
        return repository.saveAndFlush(neu);
    }
}
