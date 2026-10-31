# ERP-Handwerk Extended

Dieser Branch erweitert das Original von Kuhn um zusätzliche Module und Basis-Upgrades.

**Basis:** `main` (Stand: Commit `5d4b0d11`, ca. Oktober 2025) von
[github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe)

**Abstand:** 396 eigene Commits auf dieser Basis.
Kuhns aktuelles `upstream/main` hat seitdem 62 weitere Commits (z. B. Mistral-Integration, Feiertage
je Bundesland, Zeiterfassung-Push-Erinnerungen) — diese sind **noch nicht in diesem Branch eingeflossen.**

---

## Übersicht

| Modul / Bereich              | Art      | Migrationen        | Neue Java-Dateien |
|------------------------------|----------|--------------------|-------------------|
| Wartungsmodul                | neu      | V9000–V9002, V9010, V9020–V9022, V9039–V9040 | ~114 |
| KI-System (Multi-Anbieter)   | neu/erweitert | V9013, V9033  | ~48              |
| Lagerverwaltung              | neu      | V9005              | ~21               |
| Leistungskalkulation         | neu      | V9004, V9024–V9026, V9034–V9037 | ~20  |
| Lieferanten-Bestellwesen     | neu      | V9017, V9018       | ~24               |
| Plantafel                    | neu      | V9044–V9053        | ~30               |
| Kostenrechnung               | erweitert| V9031, V9032, V9042, V9043 | ~29      |
| Kundenstamm-Erweiterungen    | neu      | V9003              | ~8                |
| Lieferschein                 | neu      | V9008, V9028       | ~6                |
| Fremdleistung                | neu      | V9029–V9030, V9055 | ~8                |
| SMB-Dateifreigabe            | neu      | –                  | ~17               |
| Nummernkreise                | neu      | V9027              | ~8                |
| Aktivitätsprotokoll          | neu      | V9019              | ~4                |
| Funktionsrechte je Abteilung | neu      | V9012              | ~4                |
| Programmfarbe & Menü         | neu      | –                  | ~4                |
| Startbildschirm (Widgets)    | neu      | –                  | ~3                |
| IMAP-Ordner-Zuordnung        | neu      | V9041, V9059       | ~6                |
| Artikel-Erweiterungen        | neu      | V9011, V9036–V9037, V9062, V9064–V9065 | ~10 |
| Spring Boot 4 / Java 23      | geändert | –                  | –                 |

**Gesamt eigene Migrationen: 67** (V9000–V9066)

---

## Wartungsmodul

Eigenständiges Modul zur Verwaltung von Anlagen und zur digitalen Durchführung von Wartungs- und Prüfprotokollen.
Kuhn hat keine vergleichbare Funktionalität — seine `*WartungController`-Klassen betreffen die Dokumenten-Wartung
bei Lieferanten, nicht die Anlagen-Inspektion.

### Was es kann

**Stammdaten**
- Anlagen anlegen (Tore, Türen, Maschinen, Aufzüge usw.) mit Standort, Seriennummer, Kundenzuordnung und Projekt
- Objekte und Türen/Tore je Anlage erfassen
- Wartungsverträge mit Intervall und Preis je Projekt hinterlegen

**Modulkatalog (Vorlagen)**
- Wiederverwendbare Prüfmodule definieren (z. B. „Sicherheitsinspektion Tor Typ A")
- Prüfpunkte, Abschnitte, Datenfelder pro Modul (Text, Zahl, Datum, Ja/Nein, Foto, Jahreszahl)
- Startvorlagen für Brandschutztüren, Tore und Zusatzmodule eingebaut
- CSV-Import bestehender Anlagen mit Mapping-Vorschau

**Prüfprotokoll**
- Protokoll je Anlage anlegen, Prüfmodule zuweisen
- Prüfpunkte als OK / Mangel / nicht geprüft abhaken
- Datenfelder befüllen, Freitext-Notizen; Unterschrift digital erfassen
- PDF-Ausgabe des fertigen Protokolls
- Sammelbericht: PDF mit allen Protokollen eines Zeitraums je Kunde
- Verlaufsspalte: wer hat das Protokoll wann abgeschlossen, wieder geöffnet oder archiviert (mit Grund)

**Feldeinsatz (Mobile)**
- Feldeinsatz direkt am Objekt starten, Folgprüfung aus bestehender Vorlage erzeugen
- Prüfpunkte vor Ort abhaken, Unterschrift auf dem Mobilgerät erfassen

**Modulschalter**
- Das Modul kann je Mandant deaktiviert werden (`WartungModulSperre`)

### Technischer Umfang

- **Migrationen:** V9000, V9001, V9002, V9010, V9020, V9021, V9022, V9039, V9040
- **Tabellen:** `wartung_objekt`, `wartung_tuer`, `wartung_modul`, `wartung_modul_abschnitt`, `wartung_modul_pruefpunkt`, `wartung_modul_datenfeld`, `wartung_modul_andockbar`, `wartung_modul_untertyp`, `wartung_globales_datenfeld`, `wartung_tuer_modul`, `wartung_nummernkreis`, `wartung_protokoll`, `wartung_protokoll_modul`, `wartung_protokoll_abschnitt`, `wartung_protokoll_pruefpunkt`, `wartung_protokoll_datenfeld`, `wartung_mitarbeiter_unterschrift` — 17 Tabellen
- **Paket:** `controller/wartung/`, `service/wartung/` — vollständig eigene Pakete, berührt keine bestehenden Klassen
- **Frontend:** `pages/WartungAnlage.tsx`, `pages/WartungProtokoll.tsx`, `pages/WartungUebersicht.tsx`, `pages/WartungModulEditor.tsx`

---

## KI-System (Multi-Anbieter)

Kuhn hat Gemini als Scanner/Beleg-Analyse-Dienst eingebaut (`GeminiScannerService`, `BelegKiAnalyseService`).
**Dieser Branch ersetzt und erweitert das** um eine vollständige Anbieter-Abstraktion mit Assistenten und Werkzeugen.
Kuhns neues `upstream/main` (Commit `d801608f`) enthält inzwischen ebenfalls eine Anbieter-Abstraktion mit Mistral —
diese wurde unabhängig parallel entwickelt und ist **noch nicht eingeflossen.**

### Was es kann

**Unterstützte Anbieter**

| Anbieter | Typ | Bild | PDF nativ |
|---|---|---|---|
| Gemini (Google) | Cloud | ja | ja |
| Claude (Anthropic) | Cloud | ja | ja |
| Mistral | Cloud (EU) | ja | nein (OCR-API) |
| Lokal (Ollama u. a.) | lokal | ja | nein |
| OpenAI-kompatibel | flexibel | ja | – |

Anbieter und Modell sind je Funktion einzeln wählbar, zur Laufzeit wechselbar ohne Neustart.

**KI-Assistent mit Werkzeugen**
- Natürlichsprachige Eingabe; der Assistent führt Aktionen nach Bestätigung aus
- Werkzeuge (alle in `service/ki/assistent/`): Artikel/Kunden/Lieferanten suchen und anlegen, Lager buchen, Zeit buchen, Notiz speichern, Entwicklungswunsch senden, Programm-Hilfe, Stücklisten-Scan, Lieferschein-Scan, E-Mail-Anhang analysieren, KI-Merkliste pflegen
- Dokument- und Beleganalyse (PDFs, Bilder)
- Monatliche Ausgabenobergrenze je Anbieter konfigurierbar (V9013: `ki_aktion_protokoll`)

**KI-Merkliste** (V9033)
- Der KI-Assistent kann sich sitzungsübergreifend Notizen merken
- Nur durch Administrator-Bestätigung; in den Einstellungen sichtbar und löschbar

**Programm-Hilfe (RAG)**
- Eingebettete Dokumentations-Suche direkt im Programm (`service/kihilfe/`)
- RAG-Cache wird bei jedem Image-Build neu indiziert (Gemini-Einbetter, `GeminiEinbetter.java`)
- Details: `RAG_INDEXING.md`

### Technischer Umfang

- **Migrationen:** V9013 (KI-Aktionsprotokoll), V9033 (KI-Merkliste)
- **Pakete:** `service/ki/` (Anbieter-Abstraktion, 5 Anbieter-Implementierungen), `service/ki/assistent/` (Werkzeuge), `service/kihilfe/` (RAG)
- **Schnittstelle:** `KiAnbieter`-Interface — neue Anbieter durch eine einzige Implementierung hinzufügbar
- **Controller:** `KiAnbieterController`, `KiAssistentController`, `KiBelegController`, `KiDokumentController`, `KiMerkzettelController`, `KiModellController`, `KiObergrenzeController`, `LokaleKiController`

---

## Lagerverwaltung

Kuhn hat keine Lagerverwaltung. Dieses Modul ergänzt ein Journal-basiertes Lager.

### Was es kann

- Lagerorte anlegen (Halle, Fahrzeug, Regal); nur deaktivierbar, nicht löschbar (Buchungen verweisen darauf)
- Zugänge, Abgänge und Korrekturen (Inventur) buchen
- Bestand = Summe aller Buchungen, kein separater Zählerstand
- Mindestbestand je Artikel einstellbar
- Reservierungen für Projektbedarf (V9056)
- Chargen und Zeugnisse an Lagerbuchungen (V9066)
- Modulschalter: Lager kann je Mandant deaktiviert werden (`LagerModulSperre`)

### Technischer Umfang

- **Migration:** V9005 (Tabellen: `lagerort`, `lager_buchung`, `lager_artikel`)
- **Controller:** `LagerController`
- **Service:** `LagerService`, `LagerAppService`, `LagerModulSchalterService`
- **Frontend:** `pages/LagerEditor.tsx`

---

## Leistungskalkulation

Kuhns Original hat `leistung` als einfachen Datensatz mit einem manuell eingetragenem Preis.
Dieser Branch ergänzt eine vollständige Kalkulation mit Material, Arbeitszeit, Maschinen und Fremdleistung.

### Was es kann

- Material (Artikel) und Arbeitszeit (Arbeitsgänge) je Einheit der Leistung hinterlegen
- Zuschlag in Prozent auf Material und Arbeit (Gemeinkosten, Wagnis, Gewinn)
- Maschinenkosten (Rüst- und Bearbeitungszeit) in die Kalkulation einbeziehen (V9026)
- Verschnitt bei Material konfigurierbar (V9025)
- Baugruppen in Leistungen (V9024)
- Formelfelder für variable Abmessungen (V9035): Eingabefelder und Formeln für Bearbeitungs- und Rüstzeit
- Temporäre Leistungen: einmalige Position im Angebot kalkulierbar (V9034)
- Arbeitsgänge archivieren statt löschen (V9038)

### Technischer Umfang

- **Migrationen:** V9004, V9009, V9024, V9025, V9026, V9034, V9035, V9038
- **Controller:** `LeistungKalkulationController`
- **Service:** `LeistungKalkulationService`, `LeistungAlternativeService`, `MaschinenKostenRechner`
- **Frontend:** `pages/leistungen/`

---

## Lieferanten-Bestellwesen

Kuhn hat Bestellungen im Kontext von Lieferanten-Dokumenten. Dieser Branch ergänzt eigenständige Bestellungen.

### Was es kann

- Bestellung an einen Lieferanten als eigener Datensatz mit Bestellnummer und Positionen
- Bestellstatus verfolgen (V9017)
- Materialrechnung-Zuordnung: welche Rechnung hat welches Projektmaterial bezahlt (V9018)
- Standard-Lieferzeit am Lieferanten hinterlegen (V9047)
- Fertigungsunterlagen (DXF, STEP, DWG, PDF) an Artikel und Bestellungen (V9058)
- Lieferanten-Dokumente als „Privat" kennzeichnen (V9060)
- Eingangskorb: Status-Spalte für Dokumente aus E-Mail-Import (V9063)

### Technischer Umfang

- **Migrationen:** V9017, V9018, V9047, V9058, V9060, V9061, V9063
- **Controller:** `LieferantenBestellungController`
- **Service:** `LieferantenBestellungService`, `LieferantenBestellungPdfService`

---

## Plantafel

Kuhns Original hat keine Plantafel. Dieses Modul ergänzt eine Auftragsplanung.

### Was es kann

- Vorgänge je Auftrag (Projekt) mit Abhängigkeiten und Terminen (V9044)
- Arbeitsvorgänge (Stunden + Personen) und Wartevorgänge (Material, Fremdleistung mit Lieferanten) (V9049)
- Kapazitätsplanung je Abteilung (V9045)
- Ressourcen (Werkstatt, Fahrzeug, Maschine) und Zuweisung zu Vorgängen (V9046)
- Stempelung als Fortschritt: gestempelte Stunden zählen vom Aufwand ab (V9053)
- Mehrtägige Kalendertermine (V9048)
- Änderungsliste mit Rückgängig und Schutz vor gleichzeitigem Bearbeiten (V9050)
- Lieferung bei Wartevorgängen: Lieferant, Standard-Lieferzeit aus Lieferantenstamm (V9051)
- Auftragsfenster beim Bestätigen: Plantafel-Kette mit geschätzten Werten anlegen oder später planen (V9052)

### Technischer Umfang

- **Migrationen:** V9044–V9053 (10 Migrationen)
- **Paket:** `service/planung/`
- **Controller:** `PlantafelController`, `PlantafelAufnahmeController`, `PlantafelBelegungController`, `PlantafelEinstellungenController`, `PlantafelKiController`, `PlantafelTerminController`, `PlantafelZuordnungController`
- **Frontend:** `pages/Plantafel.tsx`

---

## Kostenrechnung

Kuhn hat Kostenstellen. Dieser Branch erweitert die Kostenrechnung um Verrechnungssätze.

### Was es kann

- Zeitbuchungen speichern ihre Sätze selbst (Kostenrechnung bleibt stabil bei späteren Änderungen, V9031)
- Maschinenliste mit Rechner (V9032)
- Geschätzter Jahresbetrag (Soll) je Kostenstelle für Gemeinkosten-Kalkulation (V9042)
- Kalkulationssatz am Arbeitsgang: manuell oder aus der Kostenrechnung (Qualifikation oder Betrieb, V9043)

### Technischer Umfang

- **Migrationen:** V9031, V9032, V9042, V9043
- **Controller:** `KostenrechnungController`
- **Service:** `KostenrechnungVorgaben`, `KostensatzService`, `MaschinenKostenRechner`

---

## Kundenstamm-Erweiterungen

### Was es kann

- Mehrere Ansprechpartner je Kunde mit Name, Funktion, Telefon, Mobil, E-Mail, Notizen (V9003)
- Hauptansprechpartner-Kennzeichnung; bestehende Einträge werden als Hauptansprechpartner übernommen
- Kundenzuordnung in der Plantafel (über Projekt)
- Kundenobjekte (über Wartungsmodul)
- DSGVO-Löschfunktion (`KundeDatenschutzService`)

### Technischer Umfang

- **Migration:** V9003 (Tabelle: `kunde_ansprechpartner`)
- **Controller:** `KundeAnsprechpartnerController`, `KundeObjektController`

---

## Lieferschein

Ergänzt den Dokumententyp LIEFERSCHEIN (Kuhn hat diesen Wert nicht).

### Was es kann

- Lieferschein an Kunden mit Unterschrift des Empfängers (V9028)
- Lieferschein-Scan per KI (Stücklisten aus Lieferscheinen auslesen, `LieferscheinScanService`)

### Technischer Umfang

- **Migrationen:** V9008 (Enum-Wert LIEFERSCHEIN), V9028 (Lieferschein-Datensatz)
- **Controller:** `controller/lieferschein/`

---

## Fremdleistung (Veredler)

### Was es kann

- Preisliste je Veredler (Lieferant): Verzinken (Gewicht), Pulverbeschichten (Fläche), Fräsen
- Menge aus berechnetem Gewicht oder Oberfläche der Leistung (V9030)
- Eigene Lieferzeit je Leistung (V9055)

### Technischer Umfang

- **Migrationen:** V9029, V9030, V9055
- **Controller:** `FremdleistungController`

---

## SMB-Dateifreigabe

Neu in diesem Branch, kein Äquivalent bei Kuhn.

### Was es kann

**OpenFile-Launcher (Windows-Client)**
- Klick im ERP auf eine Datei öffnet sie lokal in der Desktop-Anwendung (HiCAD, Excel, Adobe usw.)
- Windows-Hintergrundprogramm empfängt Öffnen-Befehle als Einmallinks
- Einmalige Installation per Setup-Assistent (Inno Setup, `.exe`)
- Details: `deployment/openfile-launcher/`

**Samba-Freigabe via Tailscale**
- Docker-Container gibt den Upload-Ordner des ERP als Netzlaufwerk frei
- Erreichbar über Tailscale im Büro und unterwegs
- Details: `deployment/docker-server/dateifreigabe/`

**Bidirektionaler SMB-Abgleich**
- Bestehenden Netzwerkordner (Windows-Freigabe) ↔ ERP-Uploads synchronisieren
- Regeln konfigurierbar: Ordner, Richtung, Zeitplan
- Bibliothek: smbj 0.12.2

### Technischer Umfang

- **Pakete:** `service/ablage/` (SMB-Abgleich), `service/openfile/` (Einmallinks)
- **Deployment:** `deployment/openfile-launcher/`, `deployment/docker-server/dateifreigabe/`
- **Abhängigkeiten:** `com.hierynomus:smbj:0.12.2`, `com.rapid7.client:dcerpc:0.12.13`

---

## Nummernkreise

Kuhns Original hat feste Nummernkreise. Dieser Branch macht sie konfigurierbar.

### Was es kann

- Präfix, Startnummer und Schrittweite je Dokumententyp einstellbar (V9027)
- Eigener Nummernkreis für Wartungsprotokolle (`wartung_nummernkreis`)

### Technischer Umfang

- **Migration:** V9027
- **Controller:** `NummernkreisController`
- **Service:** `NummernkreisService`

---

## Aktivitätsprotokoll „Mein Tagesverlauf"

### Was es kann

- Gemeinsames Ereignisprotokoll: was hat der Benutzer heute getan (V9019)
- Neue Einträge werden automatisch über `AktivitaetInterceptor` erzeugt

### Technischer Umfang

- **Migration:** V9019 (Tabelle: `aktivitaet_ereignis`)
- **Frontend:** `pages/MeinTagesverlauf.tsx`

---

## Funktionsrechte je Abteilung

### Was es kann

- Einzelne Programm-Funktionen (Web und App) je Abteilung freischalten oder sperren (V9012)
- `FunktionsrechtSperre`-Annotation sperrt Controller-Methoden wenn das Recht fehlt

### Technischer Umfang

- **Migration:** V9012 (Tabellen: `abteilung_funktion`, `abteilung_funktionsrecht`)
- **Controller:** `FunktionsrechteController`

---

## Programmfarbe & Menü

### Was es kann

- Primärfarbe der Oberfläche je Betrieb einstellbar (gespeichert als `#rrggbb` in `system_setting`)
- Das Frontend berechnet alle Farbstufen aus der Hauptfarbe (Hell bis Dunkel)
- Menüreihenfolge anpassbar (`MenueService`, `MenueController`)

### Technischer Umfang

- **Keine Migration** (nutzt vorhandene `system_setting`-Tabelle, Schlüssel `darstellung.farbe`)
- **Controller:** `DarstellungController`, `MenueController`
- **Service:** `DarstellungService`, `MenueService`

---

## IMAP-Ordner-Zuordnung

Kuhn hat IMAP-Postfächer (V407+) ohne manuelle Ordner-Zuordnung.
Dieser Branch ergänzt die Papierkorb/Spam-Ordner-Einstellung.

### Was es kann

- Papierkorb- und Spam-Ordner je Postfach manuell wählen oder automatisch erkennen (`\\Trash`, `\\Junk`)
- IMAP-Rücksync: E-Mails aus dem Spam- oder Papierkorb-Ordner werden im ERP entsprechend markiert
- Persönliche Postfächer je Benutzer (V9041)

### Technischer Umfang

- **Migrationen:** V9041 (persönliche Postfächer), V9059 (Spalten `imap_ordner_papierkorb`, `imap_ordner_spam` an `email_absender`)
- **Service:** `MailOrdnerService`, `ImapRuecksyncService`
- **Controller:** `MailOrdnerController`

---

## Artikel-Erweiterungen

| Erweiterung | Migration |
|---|---|
| EAN/Barcode | V9011 |
| Gewicht und Fläche (Verzinken/Pulverbeschichten) | V9036 |
| Zuschnitt-Formeln (Gewicht kg, Außenfläche m²) | V9037 |
| Handelseinheit | V9062 |
| Handelseinheit-Varianten | V9064 |
| Lieferanten-Artikelbezeichnung je Lieferant | V9065 |
| Archiv (zwei Stufen: archivieren / löschen) | V9057 |
| Reservierungen im Projekt | V9056 |

---

## Startbildschirm (Schnellzugriff)

Kuhn hat keine Startbildschirm-Widgets. Dieser Branch ergänzt einen konfigurierbaren Startbildschirm.

### Technischer Umfang

- **Service:** `SchnellzugriffService`, `StartbildschirmService`
- **Frontend:** `pages/Startbildschirm.tsx`

---

## Basis-Änderungen

### Spring Boot: 3.2.5 → 4.1.2

Die Abhängigkeit `spring-boot-starter-parent` wurde von 3.2.5 auf 4.1.2 gehoben.
Spring Boot 4.x läuft auf Java 23 problemlos.

Hintergrund der Pinnung auf Java 23 (statt 25): Der Ziel-Server läuft auf einem Linux-Kernel 3.10.108
(Synology NAS), der die für `eclipse-temurin:25-jre` nötigen Syscalls nicht bereitstellt. Java 23 läuft
auf diesem Kernel. Upgrade auf Java 25 sobald der Kernel aktualisiert wird.
Details: Kommentar in `pom.xml`, Zeile `<java.version>`.

### Dockerfile

- **Java-Version:** `eclipse-temurin:23-jdk` / `eclipse-temurin:23-jre` (Kuhn war kurzzeitig auf 25 und zurück auf 23)
- **npm-Cache:** `--mount=type=cache,target=/root/.npm` — npm-Pakete werden bei unverändertem `package-lock.json` gecacht
- **Maven-Cache:** `--mount=type=cache,target=/root/.m2` — Maven-Abhängigkeiten werden zwischen Builds gecacht
- **Node-Heap:** `NODE_OPTIONS=--max-old-space-size=1024` — Vite-Build läuft auch auf NAS-Hardware durch (512 MB zu knapp)

### Neue Abhängigkeiten (pom.xml)

| Bibliothek | Version | Zweck |
|---|---|---|
| `com.hierynomus:smbj` | 0.12.2 | SMB-Netzwerkfreigaben |
| `com.rapid7.client:dcerpc` | 0.12.13 | SMB-DCERPC |
| `org.apache.httpcomponents:httpasyncclient` | 4.1.5 | web-push HTTP-Client |
| `org.bitbucket.b_c:jose4j` | 0.9.7 | web-push JWT |
| H2 pinned | 2.3.232 | Testdatenbank (2.4.x bricht Enum-IN-Prüfungen) |
| Mustang Validator | 2.26.0 | ZUGFeRD/E-Rechnung (war 2.17.0) |

### CI/CD

- **`docker-image.yml`** (geändert): Erweiterter Workflow mit RAG-Indexierung (KI-Hilfe), PostgreSQL-Starttest,
  Trivy-Sicherheits-Scan, PR-Quality-Gate-Prüfung. Image-Name: `ghcr.io/modl00/erp-system-fuer-handwerksbetriebe:stable`
- **`handwerk-extended.yml`** (neu): Vereinfachter Workflow für diesen Branch — baut und veröffentlicht bei jedem Push
  unter `:handwerk-extended`, kein PR-Gate, kein Starttest
- **`dependabot.yml`** (neu): Automatische Dependency-Updates für Maven und npm

### Deployment

- **`deployment/docker-server/docker-compose.yml`** (geändert): Produktions-Stack mit `ghcr.io`-Image-Pull,
  Samba-Dateifreigabe-Container, Netzsperre-Container, automatischem Nachtupdate
- **`deployment/docker-server/dateifreigabe/`** (neu): Samba-Container für Netzlaufwerk-Freigabe
- **`deployment/gitea-runner/`** (neu): Gitea Actions Runner für lokale CI
- **`deployment/openfile-launcher/`** (neu): Windows-Client-Setup (Inno Setup)
