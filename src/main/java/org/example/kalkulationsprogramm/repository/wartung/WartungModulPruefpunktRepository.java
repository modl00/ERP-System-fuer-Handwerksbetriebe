package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WartungModulPruefpunktRepository extends JpaRepository<WartungModulPruefpunkt, Long> {

    List<WartungModulPruefpunkt> findByModulIdInOrderBySortierungAscIdAsc(Collection<Long> modulIds);

    List<WartungModulPruefpunkt> findByModulIdOrderBySortierungAscIdAsc(Long modulId);
}
