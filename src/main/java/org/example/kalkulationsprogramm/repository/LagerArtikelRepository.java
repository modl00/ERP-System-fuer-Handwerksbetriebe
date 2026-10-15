package org.example.kalkulationsprogramm.repository;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LagerArtikel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LagerArtikelRepository extends JpaRepository<LagerArtikel, Long> {

    Optional<LagerArtikel> findByArtikelId(Long artikelId);
}
