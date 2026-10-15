package org.example.kalkulationsprogramm.repository;

import java.util.List;

import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KiAktionProtokollRepository extends JpaRepository<KiAktionProtokoll, Long> {

    List<KiAktionProtokoll> findAllByOrderByZeitDescIdDesc(Pageable pageable);
}
