package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WartungProtokollAbschnittRepository extends JpaRepository<WartungProtokollAbschnitt, Long> {

    List<WartungProtokollAbschnitt> findByProtokollIdOrderBySortierungAscIdAsc(Long protokollId);
}
