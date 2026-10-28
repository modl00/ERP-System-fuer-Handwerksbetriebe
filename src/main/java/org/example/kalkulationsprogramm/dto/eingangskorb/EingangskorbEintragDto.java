package org.example.kalkulationsprogramm.dto.eingangskorb;

import java.time.LocalDateTime;

public record EingangskorbEintragDto(
        Long id,
        String dateiname,
        String typ,
        LocalDateTime uploadDatum,
        Long lieferantId,
        String lieferantName,
        String emailBetreff,
        String emailAbsender,
        String eingangStatus
) {}
