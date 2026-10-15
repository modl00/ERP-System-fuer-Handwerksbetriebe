package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.DokumentnummerCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DokumentnummerCounterRepository extends JpaRepository<DokumentnummerCounter, Long> {
    Optional<DokumentnummerCounter> findByMonthKey(String monthKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DokumentnummerCounter c WHERE c.monthKey = :monthKey")
    Optional<DokumentnummerCounter> findByMonthKeyMitSperre(@Param("monthKey") String monthKey);
}
