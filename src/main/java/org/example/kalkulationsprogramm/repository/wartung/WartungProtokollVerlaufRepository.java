package org.example.kalkulationsprogramm.repository.wartung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WartungProtokollVerlaufRepository extends JpaRepository<WartungProtokollVerlauf, Long> {

    List<WartungProtokollVerlauf> findByProtokollIdOrderByZeitDescIdDesc(Long protokollId);
}
