package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Temporäre Leistungen (#55): kalkulierbare einmalige Positionen, die nicht im Katalog stehen, bis sie übernommen werden. */
class LeistungServiceTemporaerTest {

    LeistungRepository repo = mock(LeistungRepository.class);
    LeistungMapper mapper = mock(LeistungMapper.class);
    LeistungService service = new LeistungService(repo, mapper);

    @BeforeEach
    void setUp() {
        when(repo.save(any(Leistung.class))).thenAnswer(a -> a.getArgument(0));
        when(mapper.toEntity(any(LeistungCreateDto.class))).thenAnswer(a -> {
            LeistungCreateDto d = a.getArgument(0);
            Leistung l = new Leistung();
            l.setBezeichnung(d.getName());
            l.setPreis(d.getPrice());
            l.setBaugruppe(Boolean.TRUE.equals(d.getBaugruppe()));
            return l;
        });
        when(mapper.toDto(any(Leistung.class))).thenAnswer(a -> {
            LeistungDto dto = new LeistungDto();
            dto.setName(((Leistung) a.getArgument(0)).getBezeichnung());
            return dto;
        });
        doAnswer(a -> {
            Leistung l = a.getArgument(0);
            LeistungCreateDto d = a.getArgument(1);
            l.setBezeichnung(d.getName());
            l.setPreis(d.getPrice());
            return null;
        }).when(mapper).updateEntity(any(Leistung.class), any(LeistungCreateDto.class));
    }

    private static LeistungCreateDto dto(String name, String preis, Long ordner) {
        LeistungCreateDto d = new LeistungCreateDto();
        d.setName(name);
        d.setPrice(new BigDecimal(preis));
        d.setFolderId(ordner);
        return d;
    }

    @Test
    void eineTemporaereLeistungHatKeineKategorieIstKeineBaugruppeUndKennzeichnetSich() {
        LeistungCreateDto d = dto("Sondertreppe", "950.00", 4L);
        d.setBaugruppe(true);

        service.createTemporaer(d);

        org.mockito.ArgumentCaptor<Leistung> c = org.mockito.ArgumentCaptor.forClass(Leistung.class);
        org.mockito.Mockito.verify(repo).save(c.capture());
        assertThat(c.getValue().isTemporaer()).isTrue();
        assertThat(c.getValue().getKategorie()).isNull();
        assertThat(c.getValue().isBaugruppe()).as("eine einmalige Position ist keine Baugruppe").isFalse();
    }

    @Test
    void derKatalogZeigtKeineTemporaerenLeistungen() {
        Leistung katalog = new Leistung();
        katalog.setBezeichnung("Geländer");
        Leistung temp = new Leistung();
        temp.setBezeichnung("Sondertreppe");
        temp.setTemporaer(true);
        when(repo.findAll()).thenReturn(List.of(katalog, temp));

        assertThat(service.getAllLeistungen()).extracting(LeistungDto::getName).containsExactly("Geländer");
    }

    @Test
    void uebernehmenMachtSieZurKatalogLeistungMitKategorieUndBehaeltDenKalkuliertenPreis() {
        Leistung temp = new Leistung();
        temp.setId(8L);
        temp.setTemporaer(true);
        temp.setKalkuliert(true);
        temp.setPreis(new BigDecimal("950.00"));
        when(repo.findById(8L)).thenReturn(Optional.of(temp));

        service.uebernehmeTemporaere(8L, dto("Sondertreppe Edelstahl", "1.00", 4L));

        assertThat(temp.isTemporaer()).isFalse();
        assertThat(temp.getBezeichnung()).isEqualTo("Sondertreppe Edelstahl");
        assertThat(temp.getPreis()).as("der Preis folgt der Kalkulation, nicht dem Formular").isEqualByComparingTo("950.00");
    }

    @Test
    void uebernehmenBrauchtEineKategorieUndEineTemporaereLeistung() {
        Leistung temp = new Leistung();
        temp.setId(8L);
        temp.setTemporaer(true);
        Leistung katalog = new Leistung();
        katalog.setId(9L);
        when(repo.findById(8L)).thenReturn(Optional.of(temp));
        when(repo.findById(9L)).thenReturn(Optional.of(katalog));
        when(repo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uebernehmeTemporaere(8L, dto("X", "1.00", null))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Kategorie");
        assertThatThrownBy(() -> service.uebernehmeTemporaere(9L, dto("X", "1.00", 4L))).hasMessageContaining("schon im Katalog");
        assertThatThrownBy(() -> service.uebernehmeTemporaere(10L, dto("X", "1.00", 4L))).hasMessageContaining("nicht gefunden");
        assertThat(temp.isTemporaer()).isTrue();
    }
}
