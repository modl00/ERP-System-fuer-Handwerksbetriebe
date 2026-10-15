package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.AbteilungFunktionsrecht;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AbteilungFunktionsrechtRepository extends JpaRepository<AbteilungFunktionsrecht, Long> {

    List<AbteilungFunktionsrecht> findByAbteilungId(Long abteilungId);

    List<AbteilungFunktionsrecht> findByAbteilungIdIn(java.util.Collection<Long> abteilungIds);
}
