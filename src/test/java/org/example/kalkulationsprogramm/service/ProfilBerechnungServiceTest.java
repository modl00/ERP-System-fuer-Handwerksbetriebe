package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfilBerechnungServiceTest {

    @Mock WerkstoffRepository werkstoffe;

    private ProfilBerechnungService service() {
        return new ProfilBerechnungService(new ProfilRechner(), werkstoffe);
    }

    private static ProfilRechner.Masse rundstab20() {
        return new ProfilRechner.Masse(new BigDecimal("20"), null, null, null);
    }

    @Test
    void ohneWerkstoffGiltStahl() {
        assertThat(service().vorschau(Profilform.RUNDSTAB, rundstab20(), null).kgProMeter()).isEqualByComparingTo("2.4662");
    }

    @Test
    void mitWerkstoffGiltDessenDichte() {
        Werkstoff alu = new Werkstoff();
        alu.setDichte(new BigDecimal("2.700"));
        when(werkstoffe.findById(5L)).thenReturn(Optional.of(alu));

        assertThat(service().vorschau(Profilform.RUNDSTAB, rundstab20(), 5L).kgProMeter().doubleValue()).isEqualTo(0.8482);
    }

    @Test
    void werkstoffOhneDichteRechnetMitStahl() {
        when(werkstoffe.findById(6L)).thenReturn(Optional.of(new Werkstoff()));

        assertThat(service().vorschau(Profilform.RUNDSTAB, rundstab20(), 6L).kgProMeter()).isEqualByComparingTo("2.4662");
    }

    @Test
    void unbekannterWerkstoffIstEinEingabefehler() {
        when(werkstoffe.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().vorschau(Profilform.RUNDSTAB, rundstab20(), 9L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Werkstoff");
    }
}
