package org.example.kalkulationsprogramm.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AktivitaetEreignis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AktivitaetEreignisRepository extends JpaRepository<AktivitaetEreignis, Long> {

    List<AktivitaetEreignis> findByProfilIdAndZeitpunktGreaterThanEqualAndZeitpunktLessThanOrderByZeitpunktAsc(Long profilId, LocalDateTime von, LocalDateTime bis);

    List<AktivitaetEreignis> findByProfilIdOrderByZeitpunktAsc(Long profilId);

    /** Aktivitäten im System, neueste zuerst; jeder Filter ist optional. */
    @Query("""
            SELECT e FROM AktivitaetEreignis e
            WHERE e.zeitpunkt >= :ab
              AND (:objektTyp IS NULL OR e.objektTyp = :objektTyp)
              AND (:objektId IS NULL OR e.objektId = :objektId)
              AND (:art IS NULL OR e.art = :art)
            ORDER BY e.zeitpunkt DESC, e.id DESC
            """)
    List<AktivitaetEreignis> suche(@Param("ab") LocalDateTime ab, @Param("objektTyp") String objektTyp, @Param("objektId") Long objektId, @Param("art") String art, Pageable seite);

    @Modifying
    @Query("DELETE FROM AktivitaetEreignis e WHERE e.zeitpunkt < :grenze")
    int loescheAelterAls(@Param("grenze") LocalDateTime grenze);

    @Modifying
    @Query("DELETE FROM AktivitaetEreignis e WHERE e.profilId = :profilId")
    int loescheVon(@Param("profilId") Long profilId);

    long countByProfilId(Long profilId);
}
