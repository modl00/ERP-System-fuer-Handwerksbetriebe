package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Der Ort, mit dem die Dokumente abgeglichen werden: eine Netzwerkfreigabe (SMB) oder, in Tests, ein Ordner. Alle Pfade sind relativ, mit Schrägstrichen,
 * zum Beispiel {@code Projekte/2026/Muster GmbH/2026-014 - Anbau Halle/Planungsdokumente/Plan.pdf}.
 */
public interface AbgleichSpeicher extends AutoCloseable {

    /** Eine Datei am Ziel: Größe in Byte und Änderungszeit in Millisekunden. */
    record Datei(String relativ, long groesse, long geaendert) {
    }

    /** Alle Dateien (rekursiv), ohne den internen Ordner {@code _erp}. */
    List<Datei> liste() throws IOException;

    Optional<Datei> info(String relativ) throws IOException;

    byte[] lies(String relativ) throws IOException;

    /** Schreibt die Datei und legt fehlende Ordner an. Eine vorhandene Datei wird ersetzt. */
    void schreibe(String relativ, byte[] inhalt) throws IOException;

    @Override
    void close();
}
