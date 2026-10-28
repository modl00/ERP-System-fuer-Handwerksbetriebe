package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArbeitsgangRepository extends JpaRepository<Arbeitsgang, Long> {
    java.util.Optional<Arbeitsgang> findByBeschreibung(String bezeichnung);
    java.util.List<Arbeitsgang> findByMaschineId(Long maschineId);
    java.util.List<Arbeitsgang> findByMaschineIsNotNull();

    /** Wie viele Arbeitsgänge hängen an dieser Abteilung? Solange es welche gibt, darf sie nicht gelöscht werden. */
    long countByAbteilung_Id(Long abteilungId);
}
