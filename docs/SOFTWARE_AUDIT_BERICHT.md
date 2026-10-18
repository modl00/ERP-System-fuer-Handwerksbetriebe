# Software-Audit-Bericht – ERP-Handwerk
**Datum:** 2026-10-18  
**Branch:** `fix/navigation-vollstaendig-module-vorlagen` (PR #394, offen)  
**TypeScript-Check:** ✅ sauber (0 Fehler)  
**Scope:** Gesamtaudit Backend (Java/Spring Boot) + Frontend (React TypeScript)  

---

## Zusammenfassung

| Kategorie | Anzahl |
|---|---|
| Bestätigte Bugs (P1) – behoben in PR #403 | 2 |
| Bestätigte Bugs (P2) – behoben in PR #403 | 1 |
| In PR #393/#394 bereits behoben | 6 |
| Feature-Requests als Issues erfasst | 6 |

---

## P1-Bugs (kritisch, sofort beheben)

### Bug #1 – AktivitaetInterceptor nicht registriert → Issue #396

**Datei:** `src/main/java/.../config/AktivitaetInterceptor.java`  
**Symptom:** Seite „Aktivitäten im System" (Administration → Aktivitäten) zeigt dauerhaft leere Liste.  
**Ursache:** Die Klasse `AktivitaetInterceptor` ist vollständig implementiert, hat aber weder `@Component`-Annotation noch einen `@Bean`-Eintrag und wird in keinem `WebMvcConfigurer.addInterceptors()` registriert. Spring erzeugt sie nie, kein HTTP-Request wird protokolliert.  
**Betroffene Zeilen:** `WebConfig.java:23` – `addInterceptors()` registriert nur `ZeiterfassungZugriffInterceptor`.  
**Fix:** In `WebConfig.addInterceptors()` ergänzen:
```java
private final AktivitaetInterceptor aktivitaetInterceptor;
registry.addInterceptor(aktivitaetInterceptor);
```

---

### Bug #2 – POST/DELETE /api/abteilungen fehlen → Issue #401

**Datei:** `src/main/java/.../controller/AbteilungBerechtigungController.java`  
**Symptom:** In Einstellungen → Berechtigungen können Abteilungen nicht angelegt und nicht gelöscht werden (404-Fehler).  
**Ursache:** `BerechtigungenSection.tsx` ruft `POST /api/abteilungen` und `DELETE /api/abteilungen/{id}` auf. Der Controller hat nur GET- und PUT-Methoden; POST und DELETE fehlen.  
**Hinweis:** SecurityConfig hat die ADMIN-Regeln für beide Endpoints korrekt eingetragen – die Handler fehlen.  
**Fix:** `@PostMapping` (Abteilung anlegen) und `@DeleteMapping("/{id}")` (Abteilung löschen, mit 409 bei vorhandenen Arbeitsgängen) ergänzen.

---

### Bug #3 – Dokument-Editor: Einfügen nicht möglich → Issue #395

**Datei:** `react-pc-frontend/src/components/document-editor/index.tsx`  
**Symptom:** Leistungen, Artikel und Textblöcke können nicht in ein Angebot eingefügt werden.  
**Code-Stand:** Die Picker-Dialoge und Header-Buttons sind technisch korrekt verdrahtet. Der Lock-Mechanismus (`useDatensatzLock`) setzt nach erfolgreichem Acquire auf `bearbeiten`, was die Buttons freigibt.  
**Mögliche Ursachen:** (a) Leere Kataloge (keine Leistungen/Textbausteine angelegt), (b) Lock-Acquire schlägt im Produktivsystem fehl, (c) Netzwerkfehler bei `/api/leistungen`.  
**Zu prüfen:** Browser-Netzwerk-Tab auf `/api/leistungen` und `/api/textbausteine`; Browser-Console auf Fehler; Lock-Status in der BearbeitenLeiste.

---

## Bereits behobene Bugs (PR #393 + #394)

| Bug | Fix |
|---|---|
| Kostenstelle verschwindet nach Bearbeiten | `KostenstelleAuswertungDto` + `aktiv`-Feld-Mapping (PR #393) |
| Schätzwert (jahresSoll) geht verloren | `KostenstelleService.speichern()` ergänzt (PR #393) |
| Programmfarbe wird nicht gespeichert/geladen | `useProgrammfarbe()` in `MainLayout`, `wendeGemerkteFarbeAn()` in `main.tsx` (PR #394) |
| Arbeitsgänge Vorlagen ohne Variablen | `ArbeitsgangResponseDto` + `ArbeitsgangMapper` ergänzt (PR #394) |
| Artikel-Reiter (Werkstoffe/Aufschläge/Kategorien) | Modaldialoge statt Tabs in `ArtikelEditor.tsx` (PR #394) |
| Leistungsmodul doppelt im Hauptmenü | Aus Navigation „Dokumente" entfernt (PR #394) |

---

## Bekannte Einschränkungen (kein Bug, bewusste Entscheidung)

- **Büro/Werkstatt/Montage** als Starter-Abteilungen: werden beim ersten Laden der Metallbau-Vorlagen (`MetallbauLeistungenService.abteilungenSichern()`) automatisch angelegt. Kein Bug, aber nicht konfigurierbar – verwandt mit Issue #297.
- **Aktivitätsprotokoll für spezialisierte Audit-Services** (Dokumente, Belege, Zeitbuchungen) läuft über eigene Repositories und ist von Bug #1 nicht betroffen.

---

## Geprüfte Module (Kurzübersicht)

| Modul | Status |
|---|---|
| Dokumenten-Editor (index.tsx) | ⚠️ Issue #395 (Einfügen) |
| Lock-Mechanismus (useDatensatzLock) | ✅ korrekt |
| Programmfarbe (darstellung.ts / useProgrammfarbe) | ✅ behoben in PR #394 |
| Aktivitätenprotokoll (AktivitaetService) | ✅ behoben in PR #403 |
| Abteilungsverwaltung (AbteilungBerechtigungController) | ✅ behoben in PR #403 |
| Formularwesen PDF (RechnungPdfService) | ✅ behoben in PR #403 |
| Kostenstellen (KostenstelleService) | ✅ behoben in PR #393 |
| Arbeitsgänge Vorlagen (ArbeitsgangMapper) | ✅ behoben in PR #394 |
| Briefpapier (FirmaController + V9054) | ✅ vorhanden |
| Datei-Upload-Sicherheit | ✅ Path-Traversal-Schutz vorhanden |
| E-Mail-Anhänge | ✅ sicher (ByteArrayDataSource) |
| Kostenrechnung (KostenrechnungController) | ✅ korrekt |
| Plantafel (PlanBelegung + KalenderEintrag) | ✅ Kalender-Termine integriert |
| Zeiterfassung Mobile API | ✅ Fehler durch catch(RuntimeException) abgedeckt |
| Leistungsmodul (LeistungKalkulationService) | ✅ Formelvariablen korrekt |
| Bestellwesen (LieferantenBestellungController) | ✅ vollständiges CRUD |
| Lager (LagerController) | ✅ korrekt |
| Offene Posten / Buchhaltung | ✅ deaktivierte Mitarbeiter korrekt behandelt |
| TypeScript (Frontend) | ✅ 0 Fehler |
