package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Maschine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaschineRepository extends JpaRepository<Maschine, Long> {

    List<Maschine> findAllByOrderByNameAsc();

    Optional<Maschine> findByNameIgnoreCase(String name);
}
