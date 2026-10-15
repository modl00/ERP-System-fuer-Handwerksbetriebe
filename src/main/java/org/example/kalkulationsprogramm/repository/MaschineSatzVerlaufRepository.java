package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.MaschineSatzVerlauf;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaschineSatzVerlaufRepository extends JpaRepository<MaschineSatzVerlauf, Long> {

    List<MaschineSatzVerlauf> findByMaschineIdOrderByGeaendertAmDescIdDesc(Long maschineId);
}
