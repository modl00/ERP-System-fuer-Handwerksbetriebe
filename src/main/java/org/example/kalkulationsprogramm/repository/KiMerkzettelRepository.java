package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.KiMerkzettel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KiMerkzettelRepository extends JpaRepository<KiMerkzettel, Long> {

    List<KiMerkzettel> findAllByOrderByIdAsc();
}
