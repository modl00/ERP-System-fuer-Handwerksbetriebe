package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WartungModulUntertypRepository extends JpaRepository<WartungModulUntertyp, Long> {

    List<WartungModulUntertyp> findByModulIdInOrderBySortierungAscIdAsc(Collection<Long> modulIds);

    List<WartungModulUntertyp> findByModulIdOrderBySortierungAscIdAsc(Long modulId);
}
