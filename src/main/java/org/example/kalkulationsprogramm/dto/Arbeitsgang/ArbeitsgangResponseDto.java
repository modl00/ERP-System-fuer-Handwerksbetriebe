package org.example.kalkulationsprogramm.dto.Arbeitsgang;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ArbeitsgangResponseDto {
    private Long id;
    private String beschreibung;
    private BigDecimal stundensatz;
    private Long abteilungId;
    private String abteilungName;
    private Integer stundensatzJahr;
    private String formelBearbeitung;
    private String formelRuest;
    private List<ArbeitsgangFormeln.Feld> formelFelder;
}
