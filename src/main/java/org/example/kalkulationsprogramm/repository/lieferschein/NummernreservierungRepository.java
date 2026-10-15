package org.example.kalkulationsprogramm.repository.lieferschein;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung;
import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface NummernreservierungRepository extends JpaRepository<Nummernreservierung, Long> {

    List<Nummernreservierung> findByArtAndMitarbeiterIdAndStatusOrderByIdAsc(String art, Long mitarbeiterId, Status status);

    boolean existsByArtAndNummer(String art, String nummer);

    List<Nummernreservierung> findByArtOrderByIdDesc(String art);

    /** Mit Sperre: zwei gleichzeitige Verwendungen derselben Nummer scheitern. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Nummernreservierung r WHERE r.art = :art AND r.nummer = :nummer")
    Optional<Nummernreservierung> findeMitSperre(@Param("art") String art, @Param("nummer") String nummer);
}
