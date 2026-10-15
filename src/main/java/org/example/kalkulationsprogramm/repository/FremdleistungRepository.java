package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FremdleistungRepository extends JpaRepository<Fremdleistung, Long> {

    List<Fremdleistung> findAllByOrderByBezeichnungAscIdAsc();

    List<Fremdleistung> findByLieferantIdOrderByBezeichnungAscIdAsc(Long lieferantId);

    @Query("SELECT f FROM Fremdleistung f WHERE f.aktiv = true AND LOWER(f.bezeichnung) = LOWER(:bezeichnung) ORDER BY f.id")
    List<Fremdleistung> findeAktiveMitBezeichnung(@Param("bezeichnung") String bezeichnung);

    @Query("SELECT COUNT(p) FROM LeistungPosition p WHERE p.fremdleistung.id = :id")
    long zaehleVerwendung(@Param("id") Long id);
}
