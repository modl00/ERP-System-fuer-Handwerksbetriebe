package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.MaterialRechnungZuordnung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialRechnungZuordnungRepository extends JpaRepository<MaterialRechnungZuordnung, Long> {

    @Query("SELECT z FROM MaterialRechnungZuordnung z LEFT JOIN FETCH z.artikelInProjekt WHERE z.projekt.id = :projektId ORDER BY z.id")
    List<MaterialRechnungZuordnung> findByProjektId(@Param("projektId") Long projektId);

    List<MaterialRechnungZuordnung> findByDokument_IdAndProjekt_Id(Long dokumentId, Long projektId);

    @Modifying
    @Query("DELETE FROM MaterialRechnungZuordnung z WHERE z.dokument.id = :dokumentId AND z.projekt.id = :projektId")
    void loescheFuer(@Param("dokumentId") Long dokumentId, @Param("projektId") Long projektId);
}
