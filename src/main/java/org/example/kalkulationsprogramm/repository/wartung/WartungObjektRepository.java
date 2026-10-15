package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WartungObjektRepository extends JpaRepository<WartungObjekt, Long> {

    List<WartungObjekt> findByKundeIdOrderByBezeichnungAscIdAsc(Long kundeId);
}
