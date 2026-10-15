package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegPositionRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Der Hinweis am Beleg, warum (noch) keine Eingangsrechnung entstanden ist (#190). */
class BelegEingangsrechnungHinweisTest {

    private final LieferantDokumentRepository dokumente = mock(LieferantDokumentRepository.class);
    private final BelegRepository belegRepository = mock(BelegRepository.class);
    private BelegService service;
    private Beleg beleg;

    @BeforeEach
    void setUp() {
        SachkontoRepository sachkonten = mock(SachkontoRepository.class);
        KostenstelleRepository kostenstellen = mock(KostenstelleRepository.class);
        service = new BelegService(belegRepository, mock(LieferantenRepository.class), mock(MitarbeiterRepository.class), mock(AbteilungDokumentBerechtigungRepository.class),
                sachkonten, kostenstellen, mock(BelegKiAnalyseService.class), mock(BelegZuordnungService.class), dokumente, mock(FrontendUserProfileRepository.class),
                mock(BelegSplitService.class), mock(BelegPositionRepository.class), mock(KasseSaldoService.class), mock(BelegKostenstellenAnteilRepository.class),
                mock(BelegAuditService.class), mock(KassenbuchSchreibschutz.class), mock(KassenbuchMonatsabschlussRepository.class),
                new BelegVorschlagService(belegRepository, sachkonten, kostenstellen));
        beleg = new Beleg();
        beleg.setId(1L);
        beleg.setDokumentTyp(LieferantDokumentTyp.RECHNUNG);
        beleg.setBelegNummer("R-4711");
        when(dokumente.findByBelegId(1L)).thenReturn(Optional.empty());
    }

    private static Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setId(5L);
        l.setLieferantenname("Muster Stahlhandel");
        return l;
    }

    @Test
    void eineRechnungOhneLieferantenSagtDassDerLieferantFehlt() {
        assertThat(service.eingangsrechnungHinweis(beleg)).isEqualTo("LIEFERANT_FEHLT");
        assertThat(service.toDto(beleg, false).getEingangsrechnungHinweis()).isEqualTo("LIEFERANT_FEHLT");
        beleg.setDokumentTyp(LieferantDokumentTyp.GUTSCHRIFT);
        assertThat(service.eingangsrechnungHinweis(beleg)).isEqualTo("LIEFERANT_FEHLT");
    }

    @Test
    void mitLieferantenUndNeuerNummerGibtEsKeinenHinweis() {
        beleg.setLieferant(lieferant());
        when(dokumente.existiertDokumentNummer(5L, "R-4711")).thenReturn(false);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
    }

    @Test
    void eineNummerDieEsBeimLieferantenSchonGibtWirdAlsDoppeltGemeldet() {
        beleg.setLieferant(lieferant());
        when(dokumente.existiertDokumentNummer(5L, "R-4711")).thenReturn(true);
        assertThat(service.eingangsrechnungHinweis(beleg)).isEqualTo("NUMMER_DOPPELT");
    }

    @Test
    void ohneNummerGibtEsKeinenDoppeltHinweis() {
        beleg.setLieferant(lieferant());
        beleg.setBelegNummer(" ");
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
    }

    @Test
    void QuittungenLieferscheinePrivateUmbuchungenUndVerworfeneBelegeBekommenKeinenHinweis() {
        beleg.setDokumentTyp(LieferantDokumentTyp.LIEFERSCHEIN);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
        beleg.setDokumentTyp(null);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();

        beleg.setDokumentTyp(LieferantDokumentTyp.RECHNUNG);
        beleg.setBelegKategorie(BelegKategorie.PRIVATENTNAHME);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
        beleg.setBelegKategorie(BelegKategorie.UNZUGEORDNET);
        beleg.setIstUmbuchung(true);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
        beleg.setIstUmbuchung(false);
        beleg.setStatus(BelegStatus.VERWORFEN);
        assertThat(service.eingangsrechnungHinweis(beleg)).isNull();
    }

    @Test
    void eineEingangsrechnungOhneRechnungsdatumFaelltBeiDerMonatsauswahlHerausUndWirdGemeldet() {
        LieferantGeschaeftsdokument rechnung = new LieferantGeschaeftsdokument();
        rechnung.setId(91L);
        LieferantDokument d = new LieferantDokument();
        d.setGeschaeftsdaten(rechnung);
        when(dokumente.findByBelegId(1L)).thenReturn(Optional.of(d));

        assertThat(service.toDto(beleg, false).getEingangsrechnungHinweis()).isEqualTo("DATUM_FEHLT");

        rechnung.setDokumentDatum(java.time.LocalDate.of(2026, 10, 1));
        assertThat(service.toDto(beleg, false).getEingangsrechnungHinweis()).isNull();
    }

    @Test
    void bestehtSchonEineEingangsrechnungGibtEsKeinenHinweisSondernDieId() {
        LieferantGeschaeftsdokument rechnung = new LieferantGeschaeftsdokument();
        rechnung.setId(90L);
        rechnung.setDokumentDatum(java.time.LocalDate.of(2026, 10, 1));
        LieferantDokument d = new LieferantDokument();
        d.setGeschaeftsdaten(rechnung);
        when(dokumente.findByBelegId(1L)).thenReturn(Optional.of(d));
        var dto = service.toDto(beleg, false);
        assertThat(dto.getEingangsrechnungId()).isEqualTo(90L);
        assertThat(dto.getEingangsrechnungHinweis()).isNull();
    }
}
