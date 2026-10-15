package org.example.kalkulationsprogramm.repository.lieferschein;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.lieferschein.Lieferschein;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LieferscheinRepository extends JpaRepository<Lieferschein, Long> {

    Optional<Lieferschein> findByClientId(String clientId);

    boolean existsByNummer(String nummer);

    List<Lieferschein> findByProjektIdOrderByDatumDescIdDesc(Long projektId);
}
