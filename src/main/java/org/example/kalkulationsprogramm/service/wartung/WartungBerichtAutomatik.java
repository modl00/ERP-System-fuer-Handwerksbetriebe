package org.example.kalkulationsprogramm.service.wartung;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Legt den Bericht einer fertig geprüften Tür automatisch im Projekt ab (#173, Schritt 3). Läuft erst, wenn der Abschluss gespeichert ist, in einer eigenen Transaktion:
 * Ein Fehler hier (zum Beispiel kein Projekt am Feldeinsatz) darf den Abschluss nie zurücknehmen. Der Bericht lässt sich weiterhin von Hand neu erzeugen.
 */
@Component
@RequiredArgsConstructor
public class WartungBerichtAutomatik {

    private static final Logger log = LoggerFactory.getLogger(WartungBerichtAutomatik.class);

    private final WartungBerichtService berichte;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void nachAbschluss(WartungProtokollAbgeschlossen ereignis) {
        try {
            berichte.erstelleAutomatischImProjekt(ereignis.protokollId());
        } catch (RuntimeException e) {
            // Nur ID und Fehlertyp (DSGVO); von Hand lässt sich der Bericht jederzeit erzeugen
            log.warn("Wartung: Der Bericht zu Protokoll {} konnte nicht automatisch abgelegt werden: {}", ereignis.protokollId(), e.getClass().getSimpleName());
        }
    }
}
