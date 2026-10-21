package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.wartung.WartungAnlageDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollAnlegenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AppWartungServiceTest {

    FunktionsRechteService rechte = mock(FunktionsRechteService.class);
    WartungAnlageService anlagen = mock(WartungAnlageService.class);
    WartungProtokollService protokolle = mock(WartungProtokollService.class);
    WartungProtokollPdfService pdf = mock(WartungProtokollPdfService.class);
    WartungKatalogService katalog = mock(WartungKatalogService.class);
    WartungObjektService objekte = mock(WartungObjektService.class);
    WartungModulSchalterService schalter = mock(WartungModulSchalterService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<WartungModulSchalterService> provider = mock(ObjectProvider.class);
    AppWartungService service;
    Mitarbeiter max = new Mitarbeiter();

    @BeforeEach
    void setUp() {
        max.setId(5L);
        when(provider.getIfAvailable()).thenReturn(schalter);
        when(schalter.istAktiv()).thenReturn(true);
        when(rechte.verlangeFuerToken(any(), any(), any())).thenReturn(max);
        service = new AppWartungService(rechte, anlagen, protokolle, pdf, katalog, objekte, provider);
    }

    private static WartungAnlageDto anlage(Long offen, Long letzte) {
        return new WartungAnlageDto(7L, "Brandschutztür EG", null, 12, null, true, new WartungAnlageDto.Bezug(3L, "Max Mustermann GmbH"),
                new WartungAnlageDto.Objekt(4L, "Haus 1", null, null, null, null, null), new WartungAnlageDto.Bezug(9L, "Neubau"),
                List.of(new WartungAnlageDto.Modul(1L, "Brandschutztür", "Stahl", "STAHL")), offen, offen == null ? null : "W-1", letzte, List.of());
    }

    @Test
    void jederAufrufPrueftZuerstDasRechtAnDerFunktionWartung() {
        service.protokoll("tok", 1L);
        service.anlagenDesProjekts("tok", 9L);
        verify(rechte, org.mockito.Mockito.times(2)).verlangeFuerToken("tok", Funktion.WARTUNG, FunktionsAktion.ANSEHEN);

        service.pruefpunktBewerten("tok", 1L, 2L, new WartungPruefpunktErgebnisDto(null, null));
        service.status("tok", 1L, null);
        verify(rechte, org.mockito.Mockito.times(2)).verlangeFuerToken("tok", Funktion.WARTUNG, FunktionsAktion.AENDERN);

        when(anlagen.laden(7L)).thenReturn(anlage(null, null));
        service.feldeinsatzStarten("tok", 7L);
        verify(rechte).verlangeFuerToken("tok", Funktion.WARTUNG, FunktionsAktion.ANLEGEN);
    }

    @Test
    void ohneRechtOderZugangPassiertNichts() {
        when(rechte.verlangeFuerToken(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung: Wartung, Ändern."));

        assertThatThrownBy(() -> service.status("tok", 1L, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Berechtigung");
        assertThatThrownBy(() -> service.protokoll("tok", 1L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(protokolle, anlagen, pdf);
    }

    @Test
    void istDasWartungsmodulAusgeschaltetGiltAuchFuerDieAppNichts() {
        when(schalter.istAktiv()).thenReturn(false);

        assertThatThrownBy(() -> service.protokoll("tok", 1L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("ausgeschaltet");
        verifyNoInteractions(rechte, protokolle);
        when(provider.getIfAvailable()).thenReturn(null);
        service.protokoll("tok", 1L);
        verify(protokolle).laden(1L);
    }

    @Test
    void derStartNimmtKundeObjektProjektUndModuleVonDerWartungUndMachtDenAngemeldetenZumTechniker() {
        when(anlagen.laden(7L)).thenReturn(anlage(null, null));

        service.feldeinsatzStarten("tok", 7L);

        ArgumentCaptor<WartungProtokollAnlegenDto> dto = ArgumentCaptor.forClass(WartungProtokollAnlegenDto.class);
        verify(protokolle).anlegen(dto.capture(), eq(5L));
        assertThat(dto.getValue().kundeId()).isEqualTo(3L);
        assertThat(dto.getValue().objektId()).isEqualTo(4L);
        assertThat(dto.getValue().tuerId()).isEqualTo(7L);
        assertThat(dto.getValue().projektId()).isEqualTo(9L);
        assertThat(dto.getValue().technikerId()).isEqualTo(5L);
        assertThat(dto.getValue().module()).hasSize(1);
        assertThat(dto.getValue().module().get(0).untertypSchluessel()).isEqualTo("STAHL");
        assertThat(dto.getValue().vorlageProtokollId()).isNull();
    }

    @Test
    void miteinerFruehherenPruefungWirdEineFolgepruefungMitVorlageUndWerten() {
        when(anlagen.laden(7L)).thenReturn(anlage(null, 77L));

        service.feldeinsatzStarten("tok", 7L);

        ArgumentCaptor<WartungProtokollAnlegenDto> dto = ArgumentCaptor.forClass(WartungProtokollAnlegenDto.class);
        verify(protokolle).anlegen(dto.capture(), eq(5L));
        assertThat(dto.getValue().vorlageProtokollId()).isEqualTo(77L);
        assertThat(dto.getValue().module()).as("Module kommen aus der Vorlage").isNull();
        assertThat(dto.getValue().werteUebernehmen()).isTrue();
    }

    @Test
    void laeuftSchonEinFeldeinsatzWirdErZurueckgegebenStattEinenZweitenZuStarten() {
        when(anlagen.laden(7L)).thenReturn(anlage(55L, 77L));
        WartungProtokollDto laufend = new WartungProtokollDto(55L, 0L, "W-1", null, null, null, null, null, null, null, null, 12, null, null, true, List.of(), null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null);
        when(protokolle.laden(55L)).thenReturn(laufend);

        assertThat(service.feldeinsatzStarten("tok", 7L)).isSameAs(laufend);
        verify(protokolle, never()).anlegen(any(), any());
    }

    @Test
    void ohneKundeAmWartungsobjektGibtEsKlartextStattEinesFehlers() {
        WartungAnlageDto ohneKunde = new WartungAnlageDto(7L, "X", null, null, null, true, null, null, null, List.of(), null, null, null, List.of());
        when(anlagen.laden(7L)).thenReturn(ohneKunde);

        assertThatThrownBy(() -> service.feldeinsatzStarten("tok", 7L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("keinen Kunden");
    }

    @Test
    void diegemerkteUnterschriftGiltNurFuerDenMitarbeiterAusDemZugang() {
        service.gemerkteUnterschrift("tok", 1L);

        verify(protokolle).gespeicherteUnterschriftVerwenden(1L, 5L);
    }
}
