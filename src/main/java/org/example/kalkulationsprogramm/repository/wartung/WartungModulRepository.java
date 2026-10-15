package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WartungModulRepository extends JpaRepository<WartungModul, Long> {

    List<WartungModul> findAllByOrderBySortierungAscIdAsc();

    List<WartungModul> findByAktivTrueOrderBySortierungAscIdAsc();

    Optional<WartungModul> findByModulKey(String modulKey);

    boolean existsByModulKey(String modulKey);
}
