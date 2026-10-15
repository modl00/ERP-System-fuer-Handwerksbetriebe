package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Ein Modul samt gewählter Bauart, so wie es an einer Tür/einem Tor hinterlegt ist
 * (Tabelle {@code wartung_tuer_modul}). Gleichheit gilt nur über das Modul, denn pro Tür
 * kann ein Modul nur einmal vorkommen.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WartungModulAuswahl {

    @Column(name = "modul_id", nullable = false)
    private Long modulId;

    @Column(name = "untertyp_schluessel", length = 30)
    private String untertypSchluessel;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WartungModulAuswahl that)) {
            return false;
        }
        return Objects.equals(modulId, that.modulId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modulId);
    }
}
