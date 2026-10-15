package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lagerort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LagerortRepository extends JpaRepository<Lagerort, Long> {

    List<Lagerort> findAllByOrderBySortOrderAscNameAsc();

    Optional<Lagerort> findFirstByAktivTrueOrderBySortOrderAscIdAsc();

    long countByAktivTrue();

    boolean existsByNameIgnoreCase(String name);

    Optional<Lagerort> findByNameIgnoreCase(String name);
}
