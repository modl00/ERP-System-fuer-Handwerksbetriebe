package org.example.kalkulationsprogramm.repository.wartung;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.wartung.WartungNummernkreis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WartungNummernkreisRepository extends JpaRepository<WartungNummernkreis, Long> {

    /** Holt den Zähler und sperrt die Zeile bis zum Ende der Transaktion (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from WartungNummernkreis n where n.praefix = :praefix")
    Optional<WartungNummernkreis> findMitSperre(@Param("praefix") String praefix);
}
