package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ein Preis, der der Kalkulation folgt, darf nicht über das normale Bearbeiten der Leistung überschrieben werden. */
class LeistungServicePreisschutzTest {

    LeistungRepository repo = mock(LeistungRepository.class);
    LeistungMapper mapper = mock(LeistungMapper.class);
    LeistungService service = new LeistungService(repo, mapper);
    Leistung leistung = new Leistung();

    @BeforeEach
    void setUp() {
        leistung.setId(3L);
        leistung.setPreis(new BigDecimal("132.00"));
        when(repo.findById(3L)).thenReturn(Optional.of(leistung));
        when(repo.save(any(Leistung.class))).thenAnswer(a -> a.getArgument(0));
        org.mockito.Mockito.doAnswer(a -> {
            ((Leistung) a.getArgument(0)).setPreis(((LeistungCreateDto) a.getArgument(1)).getPrice());
            return null;
        }).when(mapper).updateEntity(any(Leistung.class), any(LeistungCreateDto.class));
    }

    @Test
    void berechneterPreisBleibtBeimBearbeitenErhalten() {
        leistung.setKalkuliert(true);
        LeistungCreateDto dto = new LeistungCreateDto();
        dto.setPrice(new BigDecimal("1.00"));

        service.updateLeistung(3L, dto);

        assertEquals(new BigDecimal("132.00"), leistung.getPreis());
    }

    @Test
    void handPreisLaesstSichWieBisherAendern() {
        leistung.setKalkuliert(false);
        LeistungCreateDto dto = new LeistungCreateDto();
        dto.setPrice(new BigDecimal("1.00"));

        service.updateLeistung(3L, dto);

        assertEquals(new BigDecimal("1.00"), leistung.getPreis());
    }
}
