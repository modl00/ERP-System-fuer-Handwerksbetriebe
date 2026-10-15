package org.example.kalkulationsprogramm.repository.planung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanRessource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRessourceRepository extends JpaRepository<PlanRessource, Long> {

    List<PlanRessource> findAllByOrderBySortierungAscNameAsc();
}
