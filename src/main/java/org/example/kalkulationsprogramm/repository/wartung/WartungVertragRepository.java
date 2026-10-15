package org.example.kalkulationsprogramm.repository.wartung;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.wartung.WartungVertrag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WartungVertragRepository extends JpaRepository<WartungVertrag, Long> {

    Optional<WartungVertrag> findByProjektId(Long projektId);
}
