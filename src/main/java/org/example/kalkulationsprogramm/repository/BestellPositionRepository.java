package org.example.kalkulationsprogramm.repository;

import java.util.Collection;
import java.util.List;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellPosition;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BestellPositionRepository extends JpaRepository<BestellPosition, Long> {

    /** Noch nicht gelieferte Positionen aus Bestellungen in den angegebenen Ständen, zum Beispiel für "schon unterwegs". */
    List<BestellPosition> findByBestellung_StatusInAndHerkunftAndGeliefertAmIsNull(Collection<BestellStatus> staende, BestellHerkunft herkunft);

    /** Positionen, die aus einer Projektposition entstanden sind (für die Prüfung, ob sie schon in einer Bestellung steckt). */
    List<BestellPosition> findByArtikelInProjektIdAndBestellung_StatusIn(Long artikelInProjektId, Collection<BestellStatus> staende);
}
