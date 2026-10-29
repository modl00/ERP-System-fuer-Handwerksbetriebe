package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantenBestellung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LieferantenBestellungRepository extends JpaRepository<LieferantenBestellung, Long> {

    List<LieferantenBestellung> findAllByOrderByIdDesc();

    Optional<LieferantenBestellung> findByBestellnummer(String bestellnummer);

    long countByLieferant_Id(Long lieferantId);
}
