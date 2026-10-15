package org.example.kalkulationsprogramm.service.ki.assistent;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Eine E-Mail aus dem E-Mail-Center als Anhang im KI-Chat: der Text der Mail samt Absender, Datum und Betreff, dazu die Nummer der Mail, damit die KI sie später mit einem
 * Vorgang verknüpfen kann. Nichts wird verändert; für das Modell sind das Daten, keine Anweisungen.
 */
@Service
@RequiredArgsConstructor
public class KiEmailAnhangService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final EmailRepository emails;

    @Transactional(readOnly = true)
    public KiDokumentService.Dokument anhang(Long emailId) {
        Email e = emails.findById(emailId).orElseThrow(() -> new IllegalArgumentException("Die E-Mail gibt es nicht mehr."));
        String betreff = e.getSubject() == null || e.getSubject().isBlank() ? "(ohne Betreff)" : e.getSubject().strip();
        StringBuilder sb = new StringBuilder();
        sb.append("E-Mail-Nr. im Programm: ").append(e.getId()).append('\n');
        sb.append("Richtung: ").append(e.getDirection() == null ? "?" : e.getDirection().name()).append('\n');
        sb.append("Von: ").append(nz(e.getFromAddress())).append('\n');
        sb.append("An: ").append(nz(e.getRecipient())).append('\n');
        if (e.getSentAt() != null) {
            sb.append("Datum: ").append(e.getSentAt().format(DATUM)).append('\n');
        }
        sb.append("Betreff: ").append(betreff).append('\n');
        sb.append("Bereits zugeordnet: ").append(e.getZuordnungTyp() == null ? "?" : e.getZuordnungTyp().name()).append("\n\n");
        sb.append(text(e));
        if (e.getAttachments() != null && !e.getAttachments().isEmpty()) {
            sb.append("\n\nAnhänge (Namen, Inhalt nicht gelesen): ")
                    .append(e.getAttachments().stream().map(EmailAttachment::getOriginalFilename).filter(n -> n != null && !n.isBlank()).collect(Collectors.joining(", ")));
        }
        String gesamt = sb.toString();
        boolean gekuerzt = gesamt.length() > KiDokumentService.MAX_ZEICHEN;
        if (gekuerzt) {
            gesamt = gesamt.substring(0, KiDokumentService.MAX_ZEICHEN);
        }
        return new KiDokumentService.Dokument("E-Mail: " + betreff, gesamt, gekuerzt, 1, gekuerzt ? "Die E-Mail ist lang, nur der Anfang wurde gelesen." : null);
    }

    private static String text(Email e) {
        if (e.getBody() != null && !e.getBody().isBlank()) {
            return e.getBody().strip();
        }
        if (e.getHtmlBody() != null && !e.getHtmlBody().isBlank()) {
            return Jsoup.parse(e.getHtmlBody()).text();
        }
        return "(kein Text)";
    }

    private static String nz(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").strip();
    }
}
