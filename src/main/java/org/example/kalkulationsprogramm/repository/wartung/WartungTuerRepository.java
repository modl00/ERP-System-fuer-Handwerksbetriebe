package org.example.kalkulationsprogramm.repository.wartung;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WartungTuerRepository extends JpaRepository<WartungTuer, Long> {

    List<WartungTuer> findByObjektIdOrderByBezeichnungAscIdAsc(Long objektId);

    /** Alle Türen der angegebenen Objekte, zum gesammelten Laden je Kunde. */
    @Query("select t from WartungTuer t where t.objekt.id in :objektIds order by t.bezeichnung asc, t.id asc")
    List<WartungTuer> findByObjektIds(@Param("objektIds") Collection<Long> objektIds);

    /** Aktive Anlagen, deren nächste Fälligkeit im Zeitraum liegt (für die Plantafel). */
    @Query("select t from WartungTuer t left join fetch t.objekt left join fetch t.projekt where t.aktiv = true and t.naechsteFaelligkeit >= :von and t.naechsteFaelligkeit <= :bis order by t.naechsteFaelligkeit asc nulls first, t.id asc")
    List<WartungTuer> findeFaelligImZeitraum(@Param("von") java.time.LocalDate von, @Param("bis") java.time.LocalDate bis);

    /** Anzahl Türen je Modul, für die Zähler im Modul-Editor: {modulId, Anzahl}. */
    @Query("select m.modulId, count(distinct t.id) from WartungTuer t join t.module m group by m.modulId")
    List<Object[]> zaehleTuerenJeModul();

    /** Türen, die das Modul mit genau dieser Bauart verwenden. */
    @Query("select count(distinct t.id) from WartungTuer t join t.module m where m.modulId = :modulId and m.untertypSchluessel = :schluessel")
    long zaehleMitBauart(@Param("modulId") Long modulId, @Param("schluessel") String schluessel);

    /**
     * Lädt die Wartung mit Schreibsperre. Wer einen Feldeinsatz startet, sperrt damit die Wartung: Zwei gleichzeitige Starts
     * laufen nacheinander, der zweite sieht den ersten und bekommt die Meldung "läuft schon".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WartungTuer t where t.id = :id")
    Optional<WartungTuer> findMitSperre(@Param("id") Long id);

    /** Die aktiven Wartungen (Türen) eines Projekts. */
    List<WartungTuer> findByProjektIdAndAktivTrueOrderByBezeichnungAscIdAsc(Long projektId);
}
