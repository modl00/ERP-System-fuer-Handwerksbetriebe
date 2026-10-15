package org.example.kalkulationsprogramm.repository.planung;

import org.example.kalkulationsprogramm.domain.planung.PlanProjekt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanProjektRepository extends JpaRepository<PlanProjekt, Long> {

    /** Aufträge, deren Planung mit „Später“ übersprungen wurde. */
    java.util.List<PlanProjekt> findByPlanungOffenTrue();
}
