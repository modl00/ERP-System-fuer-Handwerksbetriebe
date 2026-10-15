package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ArtikelRepository extends JpaRepository<Artikel, Long>, JpaSpecificationExecutor<Artikel> {

    @Query("select a.artikelnummer from Artikel a where a.artikelnummer is not null")
    java.util.Set<String> alleArtikelnummern();

    @Query("select lower(a.produktname) from Artikel a where a.produktname is not null")
    java.util.Set<String> alleProduktnamenKlein();

    Optional<Artikel> findByArtikelnummer(String artikelnummer);

    Optional<Artikel> findFirstByProduktnameIgnoreCase(String produktname);

    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer))")
    Optional<Artikel> findByExterneArtikelnummer(@Param("nummer") String nummer);

    /**
     * Laedt den Artikel mit einem PESSIMISTIC_WRITE-Lock auf die Zeile.
     *
     * <p>Dient als Serialisierungspunkt fuer das Ersetzen des Vorschaubilds in
     * {@code ArtikelDokumentService.ladeHoch}: Der Artikel existiert garantiert
     * schon (anders als das evtl. noch gar nicht vorhandene Vorschaubild-
     * Dokument, auf das sich also nicht sperren laesst). Zwei gleichzeitige
     * Vorschaubild-Uploads fuer denselben Artikel (Doppelklick, zwei Tabs,
     * Retry) serialisieren sich dadurch, statt beide eine eigene
     * VORSCHAUBILD-Zeile anzulegen. Gleiches Muster wie
     * {@code KundenZaehlerRepository.lockAndGet()}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artikel a where a.id = :id")
    Optional<Artikel> findByIdForUpdate(@Param("id") Long id);

    @Query("select a from Artikel a join a.artikelpreis n where lower(trim(n.externeArtikelnummer)) = lower(trim(:nummer)) and n.lieferant.id = :lieferantId")
    Optional<Artikel> findByExterneArtikelnummerAndLieferantId(@Param("nummer") String nummer,
                                                               @Param("lieferantId") Long lieferantId);

    @Query("select distinct a.produktlinie from Artikel a left join a.artikelpreis ap where (ap is null or ap.lieferant.id <> :lieferantId) and a.produktlinie is not null")
    List<String> findDistinctProduktlinieExcludingLieferant(@Param("lieferantId") Long lieferantId);

    Optional<Artikel> findByEan(String ean);

    @Query("select a from ArtikelWerkstoffe a where a.profilform is not null and a.masse is null and a.massePerQm is null and a.kgJeStueck is null order by a.id")
    List<org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe> findeOhneGewicht(org.springframework.data.domain.Pageable seite);

    @Query("select count(a) from ArtikelWerkstoffe a where a.profilform is not null and a.masse is null and a.massePerQm is null and a.kgJeStueck is null")
    long zaehleOhneGewicht();

    @Query("select a from Artikel a where (lower(coalesce(a.suchtext, '')) like :w1 and lower(coalesce(a.suchtext, '')) like :w2 and lower(coalesce(a.suchtext, '')) like :w3) "
            + "or lower(coalesce(a.artikelnummer, '')) like :ganz or a.ean = :ean order by a.produktname asc nulls first")
    List<Artikel> sucheFuerAssistent(@Param("w1") String w1, @Param("w2") String w2, @Param("w3") String w3, @Param("ganz") String ganz,
            @Param("ean") String ean, org.springframework.data.domain.Pageable seite);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into artikel_werkstoffe (id, geschliffen) select a.id, false from artikel a where a.id = :id "
            + "and not exists (select 1 from artikel_werkstoffe w where w.id = a.id)", nativeQuery = true)
    int legeWerkstoffDatenAn(@Param("id") Long id);
}

