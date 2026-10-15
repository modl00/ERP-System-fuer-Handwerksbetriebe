package org.example.kalkulationsprogramm.repository.planung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanVorgangstypRepository extends JpaRepository<PlanVorgangstyp, Long> {
    List<PlanVorgangstyp> findAllByOrderBySortierungAscNameAsc();
}
