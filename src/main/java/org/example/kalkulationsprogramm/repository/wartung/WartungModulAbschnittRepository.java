package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModulAbschnitt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WartungModulAbschnittRepository extends JpaRepository<WartungModulAbschnitt, Long> {

    List<WartungModulAbschnitt> findByModulIdInOrderBySortierungAscIdAsc(Collection<Long> modulIds);

    List<WartungModulAbschnitt> findByModulIdOrderBySortierungAscIdAsc(Long modulId);
}
