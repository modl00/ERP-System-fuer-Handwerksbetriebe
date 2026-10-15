package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.NummernkreisZaehler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface NummernkreisZaehlerRepository extends JpaRepository<NummernkreisZaehler, Long> {

    /** Mit Sperre, damit zwei gleichzeitige Vergaben nicht dieselbe Nummer ziehen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT z FROM NummernkreisZaehler z WHERE z.art = :art AND z.periode = :periode")
    Optional<NummernkreisZaehler> findeMitSperre(@Param("art") String art, @Param("periode") String periode);

    Optional<NummernkreisZaehler> findByArtAndPeriode(String art, String periode);
}
