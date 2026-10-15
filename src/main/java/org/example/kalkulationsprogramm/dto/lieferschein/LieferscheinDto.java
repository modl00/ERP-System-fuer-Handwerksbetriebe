package org.example.kalkulationsprogramm.dto.lieferschein;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ein Lieferschein für die App. Das Unterschriftsbild gehört nicht dazu (es steht im PDF). */
public record LieferscheinDto(Long id, String nummer, String clientId, Long kundeId, String kundeName, Long projektId, String projektName, String empfaengerName,
        LocalDate datum, String bemerkung, List<Position> positionen, boolean unterschrieben, String unterschriftName, LocalDateTime unterschriebenAm,
        String kopieAn, LocalDateTime kopieGesendetAm) {

    public record Position(String bezeichnung, BigDecimal menge, String einheit) { }
}
