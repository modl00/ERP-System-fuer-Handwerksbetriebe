package org.example.kalkulationsprogramm.repository.planung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanAbhaengigkeit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanAbhaengigkeitRepository extends JpaRepository<PlanAbhaengigkeit, Long> {

    @Query("select a from PlanAbhaengigkeit a where a.vorgang.projekt.id = :projektId")
    List<PlanAbhaengigkeit> findeJeProjekt(@Param("projektId") Long projektId);

    boolean existsByVorgangIdAndVorgaengerId(Long vorgangId, Long vorgaengerId);
}
