package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.KundeAnsprechpartner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KundeAnsprechpartnerRepository extends JpaRepository<KundeAnsprechpartner, Long> {

    /** Hauptansprechpartner zuerst, danach alphabetisch. */
    List<KundeAnsprechpartner> findByKundeIdOrderByHauptDescNameAscIdAsc(Long kundeId);

    Optional<KundeAnsprechpartner> findByIdAndKundeId(Long id, Long kundeId);

    long countByKundeId(Long kundeId);
}
