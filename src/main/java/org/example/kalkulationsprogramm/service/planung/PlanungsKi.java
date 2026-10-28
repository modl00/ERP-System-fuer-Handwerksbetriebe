package org.example.kalkulationsprogramm.service.planung;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.PositionsTexte;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangDto;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KI-Hilfe für die Plantafel (#184). Die KI <b>rechnet keine Termine</b>: Die Termine rechnet das Programm ({@link PlanungsRechner}). Die KI schlägt nur vor, welche Vorgänge ein
 * Auftrag braucht (aus den Positionen des Angebots oder der Auftragsbestätigung), und erklärt in einfachen Worten, wie der Liefertermin zustande kommt. Nichts wird ohne
 * Auswahl des Anwenders gespeichert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanungsKi {

    static final int MAX_POSITIONEN = 80;
    static final int MAX_ZEICHEN = 6000;
    private static final int MAX_DAUER = 60;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_KETTE = """
            Du planst Aufträge einer Metallbau-Werkstatt, die Einzelteile und Kleinserien fertigt.
            Wähle aus den erlaubten Vorgangsarten nur die, die dieser Auftrag wirklich braucht, in sinnvoller Reihenfolge (jeder Vorgang beginnt nach dem vorigen).
            Ein einfaches Einzelteil braucht zum Beispiel keine Veredelung und keine Montage, wenn die Positionen nichts davon nennen.
            Schätze je Vorgang die Dauer in ganzen Arbeitstagen (mindestens 1). Die Standarddauer der Art ist nur ein Anhaltspunkt.
            Verwende die Namen der Vorgangsarten genau wie in der Liste. Erfinde keine eigenen Arten.
            Antworte ausschließlich als JSON in dieser Form:
            {"vorgaenge":[{"art":"<Name der Vorgangsart>","dauerTage":<Zahl>,"begruendung":"<ein kurzer Satz auf Deutsch>"}]}
            """;

    private static final String SYSTEM_ERKLAERUNG = """
            Du erklärst einem Handwerker in drei bis fünf kurzen Sätzen auf Deutsch, wie der Liefertermin dieses Auftrags zustande kommt.
            Nutze ausschließlich die gegebenen Angaben und erfinde nichts. Rechne keine neuen Termine aus.
            Nenne, was den Termin bestimmt (zum Beispiel der längste Vorgang, ein fester Starttermin oder ein Konflikt). Ist der Auftrag knapp oder verspätet,
            nenne ein bis zwei Möglichkeiten, Zeit zu gewinnen (zum Beispiel einen Vorgang früher starten oder verkürzen). Schreibe einfachen Fließtext ohne Aufzählung.
            """;

    private final KiTextWeiche weiche;
    private final PlanungsService planung;
    private final PlanVorgangstypRepository typen;
    private final AusgangsGeschaeftsDokumentService dokumente;

    /** Ein vorgeschlagener Vorgang. */
    public record VorschlagSchritt(Long typId, String typName, String farbe, int dauerTage, String begruendung) { }

    /** {@code quelle}: ANGEBOT oder AUFTRAGSBESTAETIGUNG. {@code hinweise}: Dinge, die die KI geliefert hat, das Programm aber nicht übernehmen konnte. */
    public record KettenVorschlag(String quelle, List<VorschlagSchritt> schritte, List<String> hinweise) { }

    public record Erklaerung(String text) { }

    // ───────────────────────────── Vorgänge vorschlagen ─────────────────────────────

    public KettenVorschlag kettenVorschlag(Long projektId) {
        PositionsTexte positionen = dokumente.positionsTexteFuerProjekt(projektId);
        if (positionen.zeilen().isEmpty()) {
            throw new IllegalArgumentException("Für diesen Auftrag gibt es kein Angebot und keine Auftragsbestätigung mit Positionen. Die KI kann daher nichts vorschlagen.");
        }
        List<PlanVorgangstyp> arten = typen.findAllByOrderBySortierungAscNameAsc().stream().filter(PlanVorgangstyp::isAktiv).toList();
        if (arten.isEmpty()) {
            throw new IllegalArgumentException("Es gibt keine Vorgangsart. Bitte zuerst eine Vorgangsart anlegen.");
        }
        String name = planung.auftrag(projektId).bezeichnung();
        String antwort = frage(SYSTEM_KETTE, nutzerTextKette(name, positionen.zeilen(), arten), 0.2, 2048, true);
        return auswerten(antwort, positionen.quelle(), arten);
    }

    static String nutzerTextKette(String auftrag, List<String> zeilen, List<PlanVorgangstyp> arten) {
        StringBuilder sb = new StringBuilder("Auftrag: ").append(auftrag).append("\n\nPositionen:\n");
        int n = 0;
        for (String z : zeilen) {
            if (n++ >= MAX_POSITIONEN || sb.length() > MAX_ZEICHEN) {
                sb.append("- … (weitere Positionen nicht gezeigt)\n");
                break;
            }
            sb.append("- ").append(z).append('\n');
        }
        sb.append("\nErlaubte Vorgangsarten (mit Standarddauer in Arbeitstagen):\n");
        for (PlanVorgangstyp a : arten) {
            sb.append("- ").append(a.getName()).append(" (").append(a.getStandardDauerTage()).append(")\n");
        }
        return sb.toString();
    }

    /** Liest die JSON-Antwort der KI. Unbekannte oder doppelte Arten werden nicht übernommen und als Hinweis gemeldet; die Dauer wird auf 1 bis {@value #MAX_DAUER} Tage begrenzt. */
    static KettenVorschlag auswerten(String antwort, String quelle, List<PlanVorgangstyp> arten) {
        JsonNode liste;
        try {
            JsonNode wurzel = JSON.readTree(antwort == null ? "" : antwort);
            liste = wurzel.isArray() ? wurzel : wurzel.get("vorgaenge");
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Die Antwort der KI war nicht lesbar. Bitte noch einmal versuchen.");
        }
        if (liste == null || !liste.isArray()) {
            throw new IllegalArgumentException("Die Antwort der KI war nicht lesbar. Bitte noch einmal versuchen.");
        }
        List<VorschlagSchritt> schritte = new ArrayList<>();
        List<String> hinweise = new ArrayList<>();
        Set<Long> schonDa = new HashSet<>();
        for (JsonNode eintrag : liste) {
            String artName = eintrag.path("art").asText("").strip();
            PlanVorgangstyp art = arten.stream().filter(a -> a.getName().equalsIgnoreCase(artName)).findFirst().orElse(null);
            if (art == null) {
                hinweise.add("Die KI nannte „" + (artName.isEmpty() ? "?" : artName) + "“. Diese Vorgangsart gibt es nicht und sie wurde nicht übernommen.");
                continue;
            }
            if (!schonDa.add(art.getId())) {
                hinweise.add("„" + art.getName() + "“ kam mehrfach vor und wurde nur einmal übernommen.");
                continue;
            }
            int dauer = eintrag.path("dauerTage").asInt(art.getStandardDauerTage());
            dauer = Math.max(1, Math.min(MAX_DAUER, dauer));
            String begruendung = eintrag.path("begruendung").asText("").strip();
            schritte.add(new VorschlagSchritt(art.getId(), art.getName(), art.getFarbe(), dauer, begruendung.length() > 300 ? begruendung.substring(0, 300) : begruendung));
        }
        if (schritte.isEmpty()) {
            throw new IllegalArgumentException("Die KI hat keinen brauchbaren Vorschlag geliefert. Bitte noch einmal versuchen oder die Vorgänge selbst anlegen.");
        }
        return new KettenVorschlag(quelle, schritte, hinweise);
    }

    // ───────────────────────────── Termin erklären ─────────────────────────────

    public Erklaerung erklaeren(Long projektId) {
        AuftragDto a = planung.auftrag(projektId);
        if (a.vorgaenge().isEmpty()) {
            throw new IllegalArgumentException("Dieser Auftrag hat noch keine Vorgänge in der Plantafel.");
        }
        return new Erklaerung(frage(SYSTEM_ERKLAERUNG, nutzerTextErklaerung(a), 0.3, 700, false).strip());
    }

    static String nutzerTextErklaerung(AuftragDto a) {
        StringBuilder sb = new StringBuilder("Auftrag: ").append(a.bezeichnung()).append('\n');
        sb.append("Voraussichtliche Lieferung: ").append(a.liefertermin() == null ? "unbekannt" : a.liefertermin()).append('\n');
        sb.append("Gewünschter Zieltermin: ").append(a.zielDatum() == null ? "keiner" : a.zielDatum()).append('\n');
        sb.append("Ampel: ").append(ampelText(a.ampel())).append("\n\nVorgänge in der Reihenfolge:\n");
        for (VorgangDto v : a.vorgaenge()) {
            sb.append("- ").append(v.bezeichnung()).append(": ").append(v.dauerTage()).append(" Arbeitstage, ").append(v.start()).append(" bis ").append(v.ende());
            if (v.festerStart() != null) sb.append(", fester Start ").append(v.festerStart());
            if (v.konflikt()) sb.append(", KONFLIKT (der feste Start kann nicht eingehalten werden)");
            if (!v.vorgaenger().isEmpty()) sb.append(", wartet auf ").append(v.vorgaenger().size()).append(" Vorgang/Vorgänge");
            sb.append(" [Status ").append(v.status().toLowerCase(Locale.ROOT)).append("]\n");
        }
        return sb.toString();
    }

    private static String ampelText(String ampel) {
        return switch (ampel == null ? "" : ampel) {
            case "OK" -> "im Plan";
            case "KNAPP" -> "knapp (weniger als 2 Arbeitstage Luft)";
            case "VERSPAETET" -> "verspätet";
            default -> "kein Zieltermin gesetzt";
        };
    }

    private String frage(String system, String nutzer, double temperatur, int maxTokens, boolean json) {
        try {
            return weiche.frage(KiFunktion.PLANUNG, system, nutzer, temperatur, maxTokens, json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Die Anfrage an die KI wurde abgebrochen.");
        } catch (IOException e) {
            log.warn("[PlanungsKi] {}", e.getMessage());
            throw new IllegalArgumentException(e.getMessage() == null || e.getMessage().isBlank() ? "Die KI antwortet nicht." : e.getMessage());
        }
    }
}
