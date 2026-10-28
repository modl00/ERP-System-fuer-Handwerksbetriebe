package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.BestellPositionAnhang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BestellPositionAnhangRepository extends JpaRepository<BestellPositionAnhang, Long> {

    List<BestellPositionAnhang> findByPositionIdOrderByErstelltAmAscIdAsc(Long positionId);

    /** Alle Anhaenge aller Positionen einer Bestellung - fuer den E-Mail-Versand. */
    @Query("SELECT a FROM BestellPositionAnhang a WHERE a.position.bestellung.id = :bestellungId ORDER BY a.position.id ASC, a.id ASC")
    List<BestellPositionAnhang> findByBestellungId(@Param("bestellungId") Long bestellungId);
}
