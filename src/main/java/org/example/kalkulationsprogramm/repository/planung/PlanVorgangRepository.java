package org.example.kalkulationsprogramm.repository.planung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanVorgangRepository extends JpaRepository<PlanVorgang, Long> {

    List<PlanVorgang> findByProjektIdOrderBySortierungAscIdAsc(Long projektId);

    boolean existsByTypId(Long typId);

    /** Die Aufträge, die überhaupt Vorgänge haben. */
    @Query("select distinct v.projekt.id from PlanVorgang v")
    List<Long> projektIdsMitVorgaengen();

    /** Vorgänge, deren Zeitraum den Bereich berührt (nur gerechnete, also mit Start und Ende). */
    @Query("select v from PlanVorgang v join fetch v.projekt where v.startDatum is not null and v.endeDatum is not null and v.endeDatum >= :von and v.startDatum <= :bis")
    List<PlanVorgang> beruehrenZeitraum(@Param("von") java.time.LocalDate von, @Param("bis") java.time.LocalDate bis);

    @Query("select coalesce(max(v.sortierung), 0) from PlanVorgang v where v.projekt.id = :projektId")
    int hoechsteSortierung(@Param("projektId") Long projektId);
}
