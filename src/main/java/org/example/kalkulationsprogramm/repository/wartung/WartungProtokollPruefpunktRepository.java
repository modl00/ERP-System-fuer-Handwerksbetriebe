package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WartungProtokollPruefpunktRepository extends JpaRepository<WartungProtokollPruefpunkt, Long> {

    List<WartungProtokollPruefpunkt> findByProtokollIdOrderBySortierungAscIdAsc(Long protokollId);

    Optional<WartungProtokollPruefpunkt> findByIdAndProtokollId(Long id, Long protokollId);

    long countByProtokollIdAndErgebnisIsNull(Long protokollId);

    /** Zählwerte je Protokoll für die Historie: {protokollId, gesamt, bewertet, maengel}. */
    @org.springframework.data.jpa.repository.Query("select p.protokollId, count(p), sum(case when p.ergebnis is not null then 1 else 0 end), "
            + "sum(case when p.ergebnis = org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis.NIO then 1 else 0 end) "
            + "from WartungProtokollPruefpunkt p where p.protokollId in :ids group by p.protokollId")
    List<Object[]> statistikJeProtokoll(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
