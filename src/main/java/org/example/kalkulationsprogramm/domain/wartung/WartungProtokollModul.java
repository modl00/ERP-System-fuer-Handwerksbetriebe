package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Ein im Protokoll gewähltes Modul. Name, Schlüssel und Bauart-Bezeichnung stehen als Kopie
 * dabei, damit ein später gelöschtes oder umbenanntes Modul das alte Protokoll nicht unlesbar
 * macht (Tabelle {@code wartung_protokoll_modul}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WartungProtokollModul {

    @Column(name = "modul_id", nullable = false)
    private Long modulId;

    @Column(name = "modul_key", nullable = false, length = 60)
    private String modulKey;

    @Column(name = "modul_name", nullable = false, length = 120)
    private String modulName;

    @Column(name = "untertyp_schluessel", length = 30)
    private String untertypSchluessel;

    @Column(name = "untertyp_label", length = 100)
    private String untertypLabel;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WartungProtokollModul that)) {
            return false;
        }
        return Objects.equals(modulId, that.modulId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modulId);
    }
}
