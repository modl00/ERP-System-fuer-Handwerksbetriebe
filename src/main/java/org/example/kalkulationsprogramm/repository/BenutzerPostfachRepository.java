package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.BenutzerPostfach;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenutzerPostfachRepository extends JpaRepository<BenutzerPostfach, Long> {

    List<BenutzerPostfach> findByProfilIdOrderByBezeichnungAscIdAsc(Long profilId);

    Optional<BenutzerPostfach> findByIdAndProfilId(Long id, Long profilId);

    long countByProfilId(Long profilId);
}
