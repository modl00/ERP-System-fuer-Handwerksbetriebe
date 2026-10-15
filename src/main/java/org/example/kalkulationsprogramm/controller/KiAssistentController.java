package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.assistent.Fehlerkontext;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Antwort;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.ChatNachricht;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService.Ergebnis;
import org.example.kalkulationsprogramm.service.ki.assistent.KiKontext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Der KI-Assistent mit Auskünften und Vorschlägen. Für jeden angemeldeten Benutzer am PC; die KI hat nie mehr Rechte als er.
 * Änderungen geschehen nur über {@code /vorschlaege/{id}/bestaetigen}, also durch einen Klick des Benutzers.
 */
@RestController
@RequestMapping("/api/ki-assistent")
@RequiredArgsConstructor
public class KiAssistentController {

    private final KiAssistentService assistent;
    private final KiAnbieterService anbieterService;

    /** {@code seite}: wo der Benutzer gerade ist (optional). */
    public record ChatAnfrage(List<ChatNachricht> messages, KiAssistentService.Seite seite, KiAssistentService.Anhang dokument, BrowserKontext browser) {

        /** Ohne Browser-Kontext (ältere Oberfläche). */
        public ChatAnfrage(List<ChatNachricht> messages, KiAssistentService.Seite seite, KiAssistentService.Anhang dokument) {
            this(messages, seite, dokument, null);
        }
    }

    /** Was der Browser an fehlgeschlagenen Aufrufen und Fehlern gesehen hat (für Fehlerberichte, #255). Ungeprüfte Eingabe: Der Server begrenzt und bereinigt es. */
    public record BrowserKontext(List<Fehlerkontext.BrowserFehler> fehler) { }

    @PostMapping("/chat")
    public Antwort chat(@RequestBody ChatAnfrage anfrage, Authentication authentication) throws KiAnbieterFehler, InterruptedException {
        return assistent.chat(kontext(authentication, anfrage), anfrage == null ? null : anfrage.messages(), anfrage == null ? null : anfrage.seite(), anfrage == null ? null : anfrage.dokument());
    }

    @PostMapping("/vorschlaege/{id}/bestaetigen")
    public ResponseEntity<Ergebnis> bestaetigen(@PathVariable String id, Authentication authentication) {
        Ergebnis e = assistent.bestaetige(kontext(authentication), id);
        return ResponseEntity.status(e.ok() ? HttpStatus.OK : HttpStatus.CONFLICT).body(e);
    }

    @PostMapping("/vorschlaege/{id}/verwerfen")
    public ResponseEntity<Ergebnis> verwerfen(@PathVariable String id, Authentication authentication) {
        Ergebnis e = assistent.verwerfe(kontext(authentication), id);
        return ResponseEntity.status(e.ok() ? HttpStatus.OK : HttpStatus.NOT_FOUND).body(e);
    }

    /** Was die Oberfläche vor dem ersten Fragen wissen will: Ist der Assistent einsatzbereit, wer antwortet, bleiben die Daten im Haus? */
    public record Status(boolean bereit, String anbieter, boolean lokal, String datenschutz, String meldung) { }

    @GetMapping("/status")
    public Status status() {
        var e = anbieterService.einstellung(KiFunktion.ASSISTENT);
        try {
            anbieterService.waehle(KiFunktion.ASSISTENT);
            return new Status(true, e.anbieter().anzeige(), e.anbieter().lokal(), e.datenschutz().name(), null);
        } catch (KiAnbieterFehler ex) {
            return new Status(false, e.anbieter().anzeige(), e.anbieter().lokal(), e.datenschutz().name(), ex.getMessage());
        }
    }

    @ExceptionHandler(KiAnbieterFehler.class)
    public ResponseEntity<Map<String, String>> anbieterFehler(KiAnbieterFehler e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Ungültige Anfrage." : e.getMessage()));
    }

    /** Der Benutzer samt Fehlerkontext dieser Anfrage: wo er ist (Seite) und was der Browser sah. */
    static KiKontext kontext(Authentication a, ChatAnfrage anfrage) {
        KiKontext k = kontext(a);
        if (anfrage == null) {
            return k;
        }
        KiAssistentService.Seite seite = anfrage.seite();
        List<String> seitenFehler = seite == null || seite.fehler() == null || seite.fehler().isBlank() ? List.of() : List.of(seite.fehler());
        List<Fehlerkontext.BrowserFehler> browser = anfrage.browser() == null || anfrage.browser().fehler() == null ? List.of() : anfrage.browser().fehler();
        if (seite == null && browser.isEmpty()) {
            return k;
        }
        return new KiKontext(k.profilId(), k.benutzer(), k.admin(),
                new Fehlerkontext(seite == null ? null : seite.route(), seite == null ? null : seite.titel(), seitenFehler, browser));
    }

    static KiKontext kontext(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof FrontendUserPrincipal p)) {
            // Ohne Benutzer-Anmeldung (zum Beispiel Zugangslink der Handy-App) darf der Assistent nicht arbeiten
            throw new org.springframework.security.access.AccessDeniedException("Der KI-Assistent ist nur für angemeldete Benutzer.");
        }
        boolean admin = a.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
        return new KiKontext(p.getId(), p.getUsername(), admin);
    }
}
