package org.example.kalkulationsprogramm.repository.planung;

import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanZuteilung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanZuteilungRepository extends JpaRepository<PlanZuteilung, Long> {

    @Modifying
    @Query("delete from PlanZuteilung z")
    void loescheAlle();

    @Query("select z from PlanZuteilung z join fetch z.vorgang v join fetch v.projekt where z.datum >= :von and z.datum <= :bis")
    List<PlanZuteilung> imZeitraum(@Param("von") LocalDate von, @Param("bis") LocalDate bis);

    @Query("select z from PlanZuteilung z where z.vorgang.id = :vorgangId order by z.datum asc, z.id asc")
    List<PlanZuteilung> jeVorgang(@Param("vorgangId") Long vorgangId);
}
