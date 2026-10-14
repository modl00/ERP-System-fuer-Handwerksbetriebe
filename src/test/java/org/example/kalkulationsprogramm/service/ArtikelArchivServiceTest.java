package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelDokument;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ArtikelDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArtikelArchivServiceTest {

    @Mock ArtikelRepository artikel;
    @Mock ArtikelInProjektRepository projektZeilen;
    @Mock LieferantenArtikelPreiseRepository preise;
    @Mock ArtikelDokumentRepository dokumente;
    @Mock ArtikelDokumentService dokumentService;

    private ArtikelArchivService service;

    @BeforeEach
    void setUp() {
        service = new ArtikelArchivService(artikel, projektZeilen, preise, dokumente, dokumentService);
    }

    private Artikel artikel(long id, String name) {
        Artikel a = new Artikel();
        ReflectionTestUtils.setField(a, "id", id);
        a.setProduktname(name);
        when(artikel.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private static void abgelehnt(Runnable r, HttpStatus status, String teil) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).contains(teil);
        });
    }

    private static ArtikelInProjekt zeile(long projektId, String bauvorhaben, Integer stueck, String meter) {
        ArtikelInProjekt z = new ArtikelInProjekt();
        Projekt p = new Projekt();
        ReflectionTestUtils.setField(p, "id", projektId);
        p.setBauvorhaben(bauvorhaben);
        z.setProjekt(p);
        z.setStueckzahl(stueck);
        z.setMeter(meter == null ? null : new BigDecimal(meter));
        return z;
    }

    // ------------------------------------------------------------------ Archivieren

    @Test
    void archivierenSetztDatumUndBenutzerUndIstIdempotent() {
        Artikel a = artikel(1, "Rundrohr");

        assertThat(service.archiviere(1L, false, " anna.buero ")).isEqualTo(1);
        assertThat(a.getArchiviertAm()).isNotNull();
        assertThat(a.getArchiviertVon()).isEqualTo("anna.buero");

        LocalDateTime erstes = a.getArchiviertAm();
        assertThat(service.archiviere(1L, false, "max.mustermann")).isZero();
        assertThat(a.getArchiviertAm()).isEqualTo(erstes);
        assertThat(a.getArchiviertVon()).isEqualTo("anna.buero");
    }

    @Test
    void grundartikelKannSeineVariantenGleichMitArchivieren() {
        Artikel g = artikel(1, "Quadratrohr");
        g.setGrundartikel(true);
        Artikel v1 = new Artikel();
        Artikel v2 = new Artikel();
        v2.setArchiviertAm(LocalDateTime.now().minusDays(1));
        when(artikel.findByGrundartikelRef(g)).thenReturn(List.of(v1, v2));

        assertThat(service.archiviere(1L, true, null)).isEqualTo(2);

        assertThat(v1.getArchiviertAm()).isNotNull();
        assertThat(v1.getArchiviertVon()).isNull();
    }

    @Test
    void ohneMitVariantenBleibenDieVariantenAktiv() {
        Artikel g = artikel(1, "Quadratrohr");
        g.setGrundartikel(true);

        assertThat(service.archiviere(1L, false, null)).isEqualTo(1);

        verify(artikel, never()).findByGrundartikelRef(any());
    }

    @Test
    void langerBenutzernameWirdGekappt() {
        Artikel a = artikel(1, "Rundrohr");

        service.archiviere(1L, false, "x".repeat(150));

        assertThat(a.getArchiviertVon()).hasSize(100);
    }

    @Test
    void unbekannteUndEndgueltigGeloeschteArtikelGibtEsNicht() {
        when(artikel.findById(9L)).thenReturn(Optional.empty());
        Artikel weg = artikel(8, "Platzhalter");
        weg.setGeloeschtAm(LocalDateTime.now());

        abgelehnt(() -> service.archiviere(9L, false, null), HttpStatus.NOT_FOUND, "gibt es nicht");
        abgelehnt(() -> service.archiviere(null, false, null), HttpStatus.NOT_FOUND, "gibt es nicht");
        abgelehnt(() -> service.archiviere(8L, false, null), HttpStatus.NOT_FOUND, "gibt es nicht");
        abgelehnt(() -> service.verwendungen(8L), HttpStatus.NOT_FOUND, "gibt es nicht");
        abgelehnt(() -> service.stelleWiederHer(8L), HttpStatus.NOT_FOUND, "nicht mehr");
    }

    @Test
    void wiederherstellenLeertDasArchiv() {
        Artikel a = artikel(1, "Rundrohr");
        a.setArchiviertAm(LocalDateTime.now());
        a.setArchiviertVon("anna.buero");

        service.stelleWiederHer(1L);

        assertThat(a.getArchiviertAm()).isNull();
        assertThat(a.getArchiviertVon()).isNull();
    }

    // ------------------------------------------------------------------ Verwendungen

    @Test
    void verwendungenZeigenProjekteMengenPreiseDokumenteUndVarianten() {
        Artikel a = artikel(1, "Rundrohr 42,4 x 2");
        a.setArchiviertAm(LocalDateTime.now());
        when(projektZeilen.findByArtikel_Id(1L)).thenReturn(List.of(zeile(9, "Geländer Musterstraße", 3, "12.50"), zeile(10, "Treppe Beispielweg", null, null)));
        when(preise.countByArtikel_Id(1L)).thenReturn(4L);
        when(dokumente.countByArtikelId(1L)).thenReturn(2L);

        ArtikelArchivService.Verwendungen v = service.verwendungen(1L);

        assertThat(v.archiviert()).isTrue();
        assertThat(v.projekte()).extracting(ArtikelArchivService.ProjektNutzung::bauvorhaben).containsExactly("Geländer Musterstraße", "Treppe Beispielweg");
        assertThat(v.projekte()).extracting(ArtikelArchivService.ProjektNutzung::menge).containsExactly("3 Stück, 12,5 m", "ohne Menge");
        assertThat(v.lieferantenpreise()).isEqualTo(4);
        assertThat(v.dokumente()).isEqualTo(2);
        assertThat(v.varianten()).isZero();
        assertThat(v.grundartikelName()).isNull();
    }

    @Test
    void variantenNennenIhrenGrundartikelUndGrundartikelZaehlenVarianten() {
        Artikel g = new Artikel();
        g.setProduktname("Quadratrohr");
        g.setGrundartikel(true);
        Artikel v = artikel(2, "QH 40x40x2");
        v.setGrundartikelRef(g);
        assertThat(service.verwendungen(2L).grundartikelName()).isEqualTo("Quadratrohr");

        Artikel grund = artikel(3, "Rundrohr");
        grund.setGrundartikel(true);
        when(artikel.countByGrundartikelRef(grund)).thenReturn(28L);
        assertThat(service.verwendungen(3L).varianten()).isEqualTo(28);
    }

    // ------------------------------------------------------------------ Endgültig löschen

    @Test
    void nurArchivierteArtikelWerdenGeloescht() {
        artikel(1, "Rundrohr");

        abgelehnt(() -> service.loesche(1L), HttpStatus.CONFLICT, "zuerst archivieren");
        verify(artikel, never()).delete(any(Artikel.class));
    }

    @Test
    void grundartikelMitVariantenWirdNichtGeloescht() {
        Artikel g = artikel(1, "Quadratrohr");
        g.setGrundartikel(true);
        g.setArchiviertAm(LocalDateTime.now());
        when(artikel.countByGrundartikelRef(g)).thenReturn(1L);

        abgelehnt(() -> service.loesche(1L), HttpStatus.CONFLICT, "noch 1 Variante");

        when(artikel.countByGrundartikelRef(g)).thenReturn(5L);
        abgelehnt(() -> service.loesche(1L), HttpStatus.CONFLICT, "noch 5 Varianten");
        verify(artikel, never()).delete(any(Artikel.class));
    }

    @Test
    void ohneProjektverwendungEntfaelltDerArtikelMitDokumenten() {
        Artikel a = artikel(1, "Rundrohr");
        a.setArchiviertAm(LocalDateTime.now());
        ArtikelDokument d = new ArtikelDokument();
        ReflectionTestUtils.setField(d, "id", 55L);
        when(dokumente.findByArtikelIdOrderBySortierungAscIdAsc(1L)).thenReturn(List.of(d));
        when(projektZeilen.findByArtikel_Id(1L)).thenReturn(List.of());

        ArtikelArchivService.Geloescht g = service.loesche(1L);

        assertThat(g.alsPlatzhalter()).isFalse();
        verify(dokumentService).loescheDokument(55L);
        verify(artikel).delete(a);
    }

    @Test
    void ausProjektenWirdEinPlatzhalterMitNamenUndPreisen() {
        Artikel g = new Artikel();
        Artikel a = artikel(1, "Rundrohr");
        a.setArchiviertAm(LocalDateTime.now());
        a.setArtikelnummer("RR-1");
        a.setEan("4006381333931");
        a.setSuchtext("rundrohr");
        a.setGrundartikelRef(g);
        a.setKurzbezeichnung("RR");
        when(projektZeilen.findByArtikel_Id(1L)).thenReturn(List.of(zeile(9, "Geländer", 1, null), zeile(10, "Treppe", 2, null)));

        ArtikelArchivService.Geloescht ergebnis = service.loesche(1L);

        assertThat(ergebnis).isEqualTo(new ArtikelArchivService.Geloescht(true, 2));
        assertThat(a.getGeloeschtAm()).isNotNull();
        assertThat(a.getProduktname()).isEqualTo("Rundrohr");
        assertThat(a.getArtikelnummer()).isNull();
        assertThat(a.getEan()).isNull();
        assertThat(a.getSuchtext()).isNull();
        assertThat(a.getGrundartikelRef()).isNull();
        assertThat(a.getKurzbezeichnung()).isNull();
        verify(artikel).save(a);
        verify(artikel, never()).delete(any(Artikel.class));
    }
}
