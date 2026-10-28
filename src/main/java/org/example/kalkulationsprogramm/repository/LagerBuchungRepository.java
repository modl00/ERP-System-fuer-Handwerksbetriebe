package org.example.kalkulationsprogramm.repository;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.LagerBuchung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LagerBuchungRepository extends JpaRepository<LagerBuchung, Long> {

    /** Bestand je Artikel und Lagerort: {artikelId, lagerortId, Summe}. */
    /** Die Artikel, die seit {@code seit} am häufigsten gebucht wurden (Zugang und Entnahme; die Art {@code ausser}, hier die Inventur, zählt nicht), meistgebuchte zuerst. */
    @Query("select b.artikel.id from LagerBuchung b where b.gebuchtAm >= :seit and b.art <> :ausser "
            + "group by b.artikel.id order by count(b) desc, max(b.gebuchtAm) desc")
    List<Long> haeufigsteArtikel(@org.springframework.data.repository.query.Param("seit") java.time.LocalDateTime seit,
            @org.springframework.data.repository.query.Param("ausser") LagerBuchung.Art ausser, Pageable seite);

    /** Wie {@link #haeufigsteArtikel}, nur die Buchungen dieses Benutzers (Name wie im Lagerjournal). */
    @Query("select b.artikel.id from LagerBuchung b where b.gebuchtAm >= :seit and b.benutzer = :benutzer and b.art <> :ausser "
            + "group by b.artikel.id order by count(b) desc, max(b.gebuchtAm) desc")
    List<Long> haeufigsteArtikelVon(@org.springframework.data.repository.query.Param("seit") java.time.LocalDateTime seit,
            @org.springframework.data.repository.query.Param("benutzer") String benutzer,
            @org.springframework.data.repository.query.Param("ausser") LagerBuchung.Art ausser, Pageable seite);

    @Query("select b.artikel.id, b.lagerort.id, sum(b.menge) from LagerBuchung b group by b.artikel.id, b.lagerort.id")
    List<Object[]> bestandJeArtikelUndOrt();

    @Query("select coalesce(sum(b.menge), 0) from LagerBuchung b where b.artikel.id = :artikelId and b.lagerort.id = :ortId")
    BigDecimal bestand(@Param("artikelId") Long artikelId, @Param("ortId") Long ortId);

    /** Summe aller Buchungen einer Projektposition (negativ, solange Material entnommen ist). */
    @Query("select coalesce(sum(b.menge), 0) from LagerBuchung b where b.artikelInProjektId = :positionId")
    BigDecimal summeFuerPosition(@Param("positionId") Long positionId);

    List<LagerBuchung> findByArtikelIdOrderByGebuchtAmDescIdDesc(Long artikelId, Pageable seite);

    List<LagerBuchung> findByArtikelInProjektIdOrderByIdDesc(Long positionId);

    boolean existsByLagerortId(Long lagerortId);
}
