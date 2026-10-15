package org.example.kalkulationsprogramm.repository.wartung;

import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollListeneintragDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WartungProtokollRepository extends JpaRepository<WartungProtokoll, Long> {

    /**
     * Bereits abgeschlossene Protokolle einer Tür, neueste zuerst. Dient als Vorgänger für den
     * Vergleich "Letztes Mal". Ein später archiviertes Protokoll zählt weiter, denn es wurde ja
     * abgeschlossen ({@code abgeschlossenAm} ist gesetzt).
     */
    List<WartungProtokoll> findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(Long tuerId, Pageable pageable);

    boolean existsByNummer(String nummer);

    /** Protokoll mit Schreibsperre (SELECT ... FOR UPDATE) für alle ändernden Vorgänge. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WartungProtokoll p where p.id = :id")
    Optional<WartungProtokoll> findMitSperre(@Param("id") Long id);

    /**
     * Hängt alle Protokolle einer Tür an ein anderes Objekt. Nötig, wenn die Tür verschoben wird,
     * denn das Objekt steht zusätzlich direkt im Protokoll.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WartungProtokoll p set p.objekt = :objekt where p.tuer.id = :tuerId")
    int setzeObjektFuerTuer(@Param("tuerId") Long tuerId, @Param("objekt") WartungObjekt objekt);

    /**
     * Sucht Protokolle für die Liste. Alle Filter sind optional ({@code null} = egal).
     * {@code statusAusschluss} blendet einen Status aus, üblicherweise das Archiv.
     * {@code suche} ist ein bereits kleingeschriebenes und mit %-Zeichen umrahmtes Muster, in dem
     * Platzhalterzeichen des Nutzers (%, _) mit einem Ausrufezeichen maskiert sind ({@code escape '!'}).
     * Ein Backslash taugt dafür nicht: In MySQL ist er selbst ein Escape-Zeichen in Zeichenketten.
     */
    @Query(value = """
            select new org.example.kalkulationsprogramm.dto.wartung.WartungProtokollListeneintragDto(
                p.id, p.nummer, p.titel, p.protokollTyp, p.datum, p.naechsteWartung, p.status,
                k.id, k.name, o.id, o.bezeichnung, t.id, t.bezeichnung,
                (select count(pp) from WartungProtokollPruefpunkt pp where pp.protokollId = p.id),
                (select count(pp) from WartungProtokollPruefpunkt pp where pp.protokollId = p.id and pp.ergebnis is not null),
                (select count(pp) from WartungProtokollPruefpunkt pp where pp.protokollId = p.id
                    and pp.ergebnis = org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis.NIO))
            from WartungProtokoll p
            join p.kunde k
            left join p.objekt o
            left join p.tuer t
            where (:kundeId is null or k.id = :kundeId)
              and (:objektId is null or o.id = :objektId)
              and (:tuerId is null or t.id = :tuerId)
              and (:status is null or p.status = :status)
              and (:statusAusschluss is null or p.status <> :statusAusschluss)
              and p.datum >= coalesce(:datumVon, p.datum)
              and p.datum <= coalesce(:datumBis, p.datum)
              and (:suche is null
                   or lower(p.nummer) like :suche escape '!'
                   or lower(coalesce(p.titel, '')) like :suche escape '!'
                   or lower(k.name) like :suche escape '!'
                   or lower(coalesce(o.bezeichnung, '')) like :suche escape '!'
                   or lower(coalesce(t.bezeichnung, '')) like :suche escape '!')
            order by p.datum desc, p.id desc
            """,
            countQuery = """
            select count(p) from WartungProtokoll p
            join p.kunde k
            left join p.objekt o
            left join p.tuer t
            where (:kundeId is null or k.id = :kundeId)
              and (:objektId is null or o.id = :objektId)
              and (:tuerId is null or t.id = :tuerId)
              and (:status is null or p.status = :status)
              and (:statusAusschluss is null or p.status <> :statusAusschluss)
              and p.datum >= coalesce(:datumVon, p.datum)
              and p.datum <= coalesce(:datumBis, p.datum)
              and (:suche is null
                   or lower(p.nummer) like :suche escape '!'
                   or lower(coalesce(p.titel, '')) like :suche escape '!'
                   or lower(k.name) like :suche escape '!'
                   or lower(coalesce(o.bezeichnung, '')) like :suche escape '!'
                   or lower(coalesce(t.bezeichnung, '')) like :suche escape '!')
            """)
    Page<WartungProtokollListeneintragDto> suche(
            @Param("kundeId") Long kundeId,
            @Param("objektId") Long objektId,
            @Param("tuerId") Long tuerId,
            @Param("status") WartungStatus status,
            @Param("statusAusschluss") WartungStatus statusAusschluss,
            @Param("datumVon") LocalDate datumVon,
            @Param("datumBis") LocalDate datumBis,
            @Param("suche") String suche,
            Pageable pageable);

    /** Anzahl Protokolle je Modul, für die Zähler im Modul-Editor: {modulId, Anzahl}. */
    @Query("select m.modulId, count(distinct p.id) from WartungProtokoll p join p.module m group by m.modulId")
    List<Object[]> zaehleProtokolleJeModul();

    /** Alle Prüfungen einer Wartung, älteste zuerst: Die Reihenfolge ergibt die laufende Nummer ("Prüfung 01"). */
    List<WartungProtokoll> findByTuerIdOrderByDatumAscIdAsc(Long tuerId);

    /** Prüfungen mehrerer Wartungen auf einmal, zum Laden der Historie ohne eine Abfrage je Wartung. */
    List<WartungProtokoll> findByTuerIdInOrderByDatumAscIdAsc(java.util.Collection<Long> tuerIds);

    /** Ein noch nicht abgeschlossener Feldeinsatz der Wartung (neuester zuerst), falls es einen gibt. */
    Optional<WartungProtokoll> findFirstByTuerIdAndStatusInOrderByIdDesc(Long tuerId, java.util.Collection<WartungStatus> status);
}
