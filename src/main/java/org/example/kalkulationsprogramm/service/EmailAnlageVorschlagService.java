package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.dto.KiAnlageVorschlagDto;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailAnlageVorschlagService {

    private static final String BETREFF_PRAEFIXE = "(?i)^(re|fwd|aw|wg|fw):\\s*";

    public KiAnlageVorschlagDto ermittleVorschlag(Email email) {
        String bauvorhaben = bereinigeBauvorhaben(email.getSubject());
        String absenderName = extrahiereAbsenderName(email.getFromAddress());
        return new KiAnlageVorschlagDto(bauvorhaben, absenderName);
    }

    private String bereinigeBauvorhaben(String betreff) {
        if (betreff == null || betreff.isBlank()) return "";
        String bereinigt = betreff.trim();
        String vorher;
        do {
            vorher = bereinigt;
            bereinigt = bereinigt.replaceAll(BETREFF_PRAEFIXE, "").trim();
        } while (!bereinigt.equals(vorher));
        return bereinigt;
    }

    private String extrahiereAbsenderName(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) return "";
        int lt = fromAddress.indexOf('<');
        if (lt > 0) {
            return fromAddress.substring(0, lt).trim().replaceAll("^\"|\"$", "");
        }
        int at = fromAddress.indexOf('@');
        return at > 0 ? fromAddress.substring(0, at).trim() : fromAddress.trim();
    }
}
