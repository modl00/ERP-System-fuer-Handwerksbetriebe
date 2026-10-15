package org.example.kalkulationsprogramm.repository.planung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanAenderung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanAenderungRepository extends JpaRepository<PlanAenderung, Long> {

    @Query("select a from PlanAenderung a order by a.id desc")
    List<PlanAenderung> neueste(Pageable seite);

    /** Die jüngste Änderung, die noch nicht zurückgenommen ist. */
    @Query("select a from PlanAenderung a where a.rueckgaengigAm is null order by a.id desc")
    List<PlanAenderung> offeneNeueste(Pageable seite);

    /** Alles, was älter ist als die angegebene Nummer: Die Liste behält nur die letzten Änderungen. */
    @Modifying
    @Query("delete from PlanAenderung a where a.id < :grenze")
    void loescheAelterAls(@Param("grenze") Long grenze);
}
