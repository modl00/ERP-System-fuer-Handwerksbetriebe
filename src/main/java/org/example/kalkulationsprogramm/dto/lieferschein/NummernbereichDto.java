package org.example.kalkulationsprogramm.dto.lieferschein;

import java.util.List;

/** Die für ein Gerät reservierten, noch nicht verwendeten Nummern einer Dokumentart. */
public record NummernbereichDto(String art, List<String> nummern) { }
