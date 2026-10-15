package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Nummernkreis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NummernkreisRepository extends JpaRepository<Nummernkreis, Long> {

    Optional<Nummernkreis> findByArt(String art);
}
