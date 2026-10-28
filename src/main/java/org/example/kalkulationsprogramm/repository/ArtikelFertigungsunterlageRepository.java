package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelFertigungsunterlage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtikelFertigungsunterlageRepository extends JpaRepository<ArtikelFertigungsunterlage, Long> {

    List<ArtikelFertigungsunterlage> findByArtikelIdOrderByErstelltAmAscIdAsc(Long artikelId);
}
