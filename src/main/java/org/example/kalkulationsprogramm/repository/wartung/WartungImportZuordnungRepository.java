package org.example.kalkulationsprogramm.repository.wartung;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.wartung.WartungImportZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WartungImportZuordnungRepository extends JpaRepository<WartungImportZuordnung, Long> {

    Optional<WartungImportZuordnung> findByQuelleAndTypAndAltId(String quelle, String typ, Long altId);

    List<WartungImportZuordnung> findByQuelleAndTyp(String quelle, String typ);
}
