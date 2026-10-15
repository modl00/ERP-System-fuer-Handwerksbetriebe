package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WartungProtokollDatenfeldRepository extends JpaRepository<WartungProtokollDatenfeld, Long> {

    List<WartungProtokollDatenfeld> findByProtokollIdOrderBySortierungAscIdAsc(Long protokollId);

    Optional<WartungProtokollDatenfeld> findByIdAndProtokollId(Long id, Long protokollId);
}
