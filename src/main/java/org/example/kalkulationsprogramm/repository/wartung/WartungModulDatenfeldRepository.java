package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulDatenfeld;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WartungModulDatenfeldRepository extends JpaRepository<WartungModulDatenfeld, Long> {

    List<WartungModulDatenfeld> findByModulIdInOrderBySortierungAscIdAsc(Collection<Long> modulIds);

    List<WartungModulDatenfeld> findByModulIdOrderBySortierungAscIdAsc(Long modulId);
}
