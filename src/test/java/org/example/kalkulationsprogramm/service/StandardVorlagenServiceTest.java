package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateAssignment;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateListDto;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.FormularTemplateAssignmentRepository;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StandardVorlagenServiceTest {

    @Mock FormularTemplateService vorlagenService;
    @Mock FormularTemplateAssignmentRepository zuordnungen;
    @Mock TextbausteinService textbausteinService;
    @Mock TextbausteinRepository textbausteine;
    @Mock FormularTextbausteinDefaultService standardtexte;
    @Mock FirmeninformationRepository firmen;

    StandardVorlagenService service;
    long naechsteId;

    @BeforeEach
    void setUp() {
        service = new StandardVorlagenService(vorlagenService, zuordnungen, textbausteinService, textbausteine, standardtexte, firmen);
        when(vorlagenService.listNamedTemplates()).thenReturn(List.of());
        when(zuordnungen.findByDokumenttyp(any())).thenReturn(List.of());
        when(textbausteine.findFirstByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        naechsteId = 100;
        when(textbausteinService.create(any(TextbausteinDto.class))).thenAnswer(a -> {
            Textbaustein t = new Textbaustein();
            t.setId(naechsteId++);
            return t;
        });
    }

    @Test
    void legtAlleVierVorlagenMitTexteUndZuordnungAn() {
        StandardVorlagenService.Ergebnis e = service.anlegen();

        assertEquals(4, e.angelegteVorlagen().size());
        assertTrue(e.vorhandeneVorlagen().isEmpty());
        assertEquals(Dokumenttyp.values().length * 2, e.neueTextbausteine());
        ArgumentCaptor<List<String>> typen = ArgumentCaptor.forClass(List.class);
        verify(vorlagenService).saveNamedTemplate(eq("Standard Rechnung"), anyString(), typen.capture());
        assertTrue(typen.getValue().contains("Rechnung") && typen.getValue().contains("Gutschrift"));
        verify(standardtexte, org.mockito.Mockito.times(4)).replaceDefaults(anyString(), any());
    }

    @Test
    void vorhandeneVorlageBleibtUnveraendert() {
        FormularTemplateListDto vorhanden = new FormularTemplateListDto();
        vorhanden.setName("standard rechnung");
        when(vorlagenService.listNamedTemplates()).thenReturn(List.of(vorhanden));

        StandardVorlagenService.Ergebnis e = service.anlegen();

        assertEquals(List.of("Standard Rechnung"), e.vorhandeneVorlagen());
        verify(vorlagenService, never()).saveNamedTemplate(eq("Standard Rechnung"), anyString(), anyList());
        verify(standardtexte, org.mockito.Mockito.times(3)).replaceDefaults(anyString(), any());
    }

    @Test
    void bereitsZugeordneterDokumenttypBehaeltSeineVorlage() {
        when(zuordnungen.findByDokumenttyp(Dokumenttyp.RECHNUNG)).thenReturn(List.of(new FormularTemplateAssignment()));

        service.anlegen();

        ArgumentCaptor<List<String>> typen = ArgumentCaptor.forClass(List.class);
        verify(vorlagenService).saveNamedTemplate(eq("Standard Rechnung"), anyString(), typen.capture());
        assertTrue(!typen.getValue().contains("Rechnung"), "Rechnung bleibt bei der eigenen Vorlage");
        assertTrue(typen.getValue().contains("Schlussrechnung"));
    }

    @Test
    void vorhandenerTextbausteinWirdWiederverwendetStattDoppeltAngelegt() {
        Textbaustein da = new Textbaustein();
        da.setId(7L);
        when(textbausteine.findFirstByNameIgnoreCase("Standard Vortext Angebot")).thenReturn(Optional.of(da));

        StandardVorlagenService.Ergebnis e = service.anlegen();

        assertEquals(Dokumenttyp.values().length * 2 - 1, e.neueTextbausteine());
    }
}
