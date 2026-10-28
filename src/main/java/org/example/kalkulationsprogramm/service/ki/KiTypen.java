package org.example.kalkulationsprogramm.service.ki;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** Die gemeinsamen Datentypen für alle KI-Anbieter. Sie sind bewusst unabhängig vom Format eines Anbieters. */
public final class KiTypen {

    private KiTypen() {
    }

    /** Eine Nachricht im Gespräch. */
    public sealed interface KiNachricht permits Nutzer, Assistent, Werkzeugergebnis { }

    /** Eine Datei, die dem Modell mitgeschickt wird (Foto, PDF, Aufnahme). {@code mime} z. B. {@code image/jpeg}, {@code application/pdf}, {@code audio/webm}. */
    public record Anhang(String mime, byte[] daten) {
        public Anhang {
            if (mime == null || mime.isBlank() || daten == null || daten.length == 0) {
                throw new IllegalArgumentException("Ein Anhang braucht Dateityp und Inhalt.");
            }
            mime = mime.strip().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean istBild() {
            return mime.startsWith("image/");
        }

        public boolean istPdf() {
            return mime.equals("application/pdf");
        }

        public boolean istTon() {
            return mime.startsWith("audio/");
        }

        public String base64() {
            return java.util.Base64.getEncoder().encodeToString(daten);
        }
    }

    /** Eine Nutzernachricht, optional mit Dateien. */
    public record Nutzer(String text, List<Anhang> anhaenge) implements KiNachricht {
        public Nutzer {
            anhaenge = anhaenge == null ? List.of() : List.copyOf(anhaenge);
        }

        public Nutzer(String text) {
            this(text, List.of());
        }
    }

    /** Antwort des Modells; {@code aufrufe} sind die Werkzeuge, die es nutzen möchte. */
    public record Assistent(String text, List<KiWerkzeugAufruf> aufrufe) implements KiNachricht {
        public Assistent {
            aufrufe = aufrufe == null ? List.of() : List.copyOf(aufrufe);
        }
    }

    /** Das Ergebnis eines Werkzeugs, das dem Modell zurückgegeben wird. */
    public record Werkzeugergebnis(String aufrufId, String name, String inhalt) implements KiNachricht { }

    public record KiWerkzeugAufruf(String id, String name, JsonNode argumente) { }

    /** Ein Werkzeug, das das Modell kennen soll. {@code parameter} ist ein JSON-Schema (type=object). */
    public record KiWerkzeugBeschreibung(String name, String beschreibung, JsonNode parameter) { }

    public record KiAnfrage(String system, List<KiNachricht> nachrichten, List<KiWerkzeugBeschreibung> werkzeuge, double temperatur, int maxTokens) {
        public KiAnfrage {
            nachrichten = nachrichten == null ? List.of() : List.copyOf(nachrichten);
            werkzeuge = werkzeuge == null ? List.of() : List.copyOf(werkzeuge);
        }
    }

    public record KiAntwort(String text, List<KiWerkzeugAufruf> aufrufe) {
        public KiAntwort {
            text = text == null ? "" : text;
            aufrufe = aufrufe == null ? List.of() : List.copyOf(aufrufe);
        }
    }
}
