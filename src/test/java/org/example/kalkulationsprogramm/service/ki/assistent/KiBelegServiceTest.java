package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class KiBelegServiceTest {

    GeminiDokumentAnalyseService analyse = mock(GeminiDokumentAnalyseService.class);
    LieferantenRepository lieferanten = mock(LieferantenRepository.class);
    LieferantGeschaeftsdokumentRepository dokumente = mock(LieferantGeschaeftsdokumentRepository.class);
    KiAnbieterService anbieter = mock(KiAnbieterService.class);
    KiBelegService service = new KiBelegService(analyse, lieferanten, dokumente, anbieter);

    private static Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private static AnalyzeResponse beleg(String lieferant, String nummer, Double sicherheit) {
        AnalyzeResponse a = new AnalyzeResponse();
        a.setLieferantName(lieferant);
        a.setDokumentNummer(nummer);
        a.setAiConfidence(sicherheit);
        return a;
    }

    @BeforeEach
    void setUp() {
        when(anbieter.eingriff()).thenReturn(KiObergrenze.Eingriff.MIT_BESTAETIGUNG);
        when(lieferanten.findAll()).thenReturn(List.of(lieferant(1, "Muster Stahlhandel GmbH"), lieferant(2, "Mustermann Schrauben KG"), lieferant(3, "Beispiel AG")));
    }

    @Test
    void einGleicherNameOhneRechtsformIstEindeutigUndBleibtOhneHinweis() {
        var v = service.vorschlag(beleg("MUSTER Stahlhandel", "R-100", 0.95));

        assertThat(v.lieferantId()).isEqualTo(1L);
        assertThat(v.lieferantName()).isEqualTo("Muster Stahlhandel GmbH");
        assertThat(v.hinweise()).isEmpty();
    }

    @Test
    void einAehnlicherNameBleibtEineAuswahlOhneAutomatischeZuordnung() {
        var v = service.vorschlag(beleg("Muster Stahl", "R-1", 0.9));

        assertThat(v.lieferantId()).isNull();
        assertThat(v.kandidaten()).extracting(k -> k.id()).containsExactly(1L);
        assertThat(v.hinweise()).anyMatch(h -> h.contains("nicht eindeutig"));
    }

    @Test
    void einUnbekannterLieferantWirdNurGemeldetNichtAngelegt() {
        var v = service.vorschlag(beleg("Völlig Neu OHG", "R-1", 0.9));

        assertThat(v.lieferantId()).isNull();
        assertThat(v.kandidaten()).isEmpty();
        assertThat(v.hinweise()).anyMatch(h -> h.contains("gibt es noch nicht") && h.contains("ausdrücklich"));
        verify(lieferanten, never()).save(any());
    }

    @Test
    void keinNameUnsichereAuswertungUndDoppelteNummerBekommenHinweise() {
        assertThat(service.vorschlag(beleg(null, "R-1", 0.3)).hinweise()).anyMatch(h -> h.contains("keinen Lieferanten")).anyMatch(h -> h.contains("unsicher (30 %)"));

        when(dokumente.existsByLieferantIdAndDokumentNummer(1L, "R-100")).thenReturn(true);
        assertThat(service.vorschlag(beleg("Muster Stahlhandel GmbH", "R-100", 0.9)).hinweise()).anyMatch(h -> h.contains("R-100") && h.contains("schon"));
    }

    @Test
    void aehnlichkeitUndNormalisierung() {
        assertThat(KiBelegService.normal("Müller & Söhne GmbH")).isEqualTo("mueller soehne");
        assertThat(KiBelegService.aehnlichkeit("mueller soehne", "mueller soehne")).isEqualTo(1.0);
        assertThat(KiBelegService.aehnlichkeit("stahl", "muster stahl")).isEqualTo(0.8);
        assertThat(KiBelegService.aehnlichkeit("a b", "b c")).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(KiBelegService.aehnlichkeit("", "x")).isZero();
    }

    @Test
    void dieObergrenzeStopptDieAuswertungBevorDieDateiAnDieKiGeht() throws Exception {
        var datei = new MockMultipartFile("datei", "rechnung.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        when(anbieter.sperrtProgrammdaten(AnbieterTyp.GEMINI)).thenReturn(true);

        assertThatThrownBy(() -> service.pruefe(datei)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)).hasMessageContaining("Cloud-Anbieter");
        when(anbieter.eingriff()).thenReturn(KiObergrenze.Eingriff.AUS);
        assertThatThrownBy(() -> service.pruefe(datei)).hasMessageContaining("ausgeschaltet");
        verify(analyse, never()).analyzeFile(any(), any());
    }

    @Test
    void ohneDateiZuGrossOderNichtLesbarGibtKlartext() {
        assertThatThrownBy(() -> service.pruefe(null)).hasMessageContaining("Datei auswählen");
        assertThatThrownBy(() -> service.pruefe(new MockMultipartFile("datei", "a.pdf", "application/pdf", new byte[0]))).hasMessageContaining("Datei auswählen");
        var gross = mock(org.springframework.web.multipart.MultipartFile.class);
        when(gross.isEmpty()).thenReturn(false);
        when(gross.getSize()).thenReturn(KiBelegService.MAX_BYTES + 1);
        assertThatThrownBy(() -> service.pruefe(gross)).hasMessageContaining("zu groß");

        var datei = new MockMultipartFile("datei", "../../x.pdf", "application/pdf", new byte[] { 1 });
        when(analyse.analyzeFile(any(), any())).thenReturn(null);
        assertThatThrownBy(() -> service.pruefe(datei)).hasMessageContaining("nicht auslesen");
    }

    @Test
    void einAnalysierterBelegBekommtDenVorschlagUndDerDateinameIstBereinigt() throws Exception {
        var datei = new MockMultipartFile("datei", "C:\\temp\\r:1.pdf", "application/pdf", new byte[] { 1 });
        when(analyse.analyzeFile(any(), org.mockito.ArgumentMatchers.argThat(n -> n != null && !n.contains("/") && !n.contains("\\") && !n.contains(":"))))
                .thenReturn(beleg("Beispiel AG", "R-9", 0.9));

        var v = service.pruefe(datei);

        assertThat(v.lieferantId()).isEqualTo(3L);
    }
}
