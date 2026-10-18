# Modul-Übersicht – Software-Audit 2026-10-18

## Backend-Module (Spring Boot, Java 23, PostgreSQL 16)

| Controller / Service | Pfad | Letzte Prüfung | Befund |
|---|---|---|---|
| AktivitaetController | `/api/aktivitaet/**`, `/api/admin/aktivitaet` | 2026-10-18 | ❌ Interceptor nicht registriert → Issue #396 |
| AktivitaetInterceptor | `config/AktivitaetInterceptor.java` | 2026-10-18 | ❌ Nie registriert |
| AbteilungBerechtigungController | `/api/abteilungen/**` | 2026-10-18 | ❌ POST/DELETE fehlen → Issue #401 |
| FunktionsrechteController | `/api/abteilungen/funktionsrechte` | 2026-10-18 | ✅ |
| AusgangsGeschaeftsDokumentController | `/api/ausgangs-dokumente/**` | 2026-10-18 | ✅ |
| AusgangsGeschaeftsDokumentAuditService | Eigene Tabelle | 2026-10-18 | ✅ (unabhängig von AktivitaetInterceptor) |
| KostenstelleService | `/api/firma/kostenstellen` | 2026-10-18 | ✅ (behoben PR #393) |
| ArbeitsgangMapper | `mapper/ArbeitsgangMapper.java` | 2026-10-18 | ✅ (behoben PR #394) |
| DarstellungController | `/api/darstellung`, `/api/settings/darstellung` | 2026-10-18 | ✅ |
| FirmaController | `/api/firma/**` | 2026-10-18 | ✅ |
| DateiSpeicherService | Datei-Upload | 2026-10-18 | ✅ Path-Traversal geschützt |
| DatensatzLockController | `/api/datensatz-locks/**` | 2026-10-18 | ✅ |
| PlantafelController | `/api/plantafel/**` | 2026-10-18 | ✅ (nicht tief geprüft) |
| MetallbauLeistungenService | Vorlagen-Seeding | 2026-10-18 | ⚠️ Büro/Werkstatt/Montage hardcodiert |

## Frontend-Module (React TypeScript)

| Seite / Komponente | Pfad | Letzte Prüfung | Befund |
|---|---|---|---|
| DocumentEditor | `components/document-editor/index.tsx` | 2026-10-18 | ⚠️ Issue #395 |
| DocumentEditorPage | `pages/DocumentEditorPage.tsx` | 2026-10-18 | ✅ Lock-Logik korrekt |
| useDatensatzLock | `components/lock/useDatensatzLock.ts` | 2026-10-18 | ✅ |
| ArtikelEditor | `pages/ArtikelEditor.tsx` | 2026-10-18 | ✅ (behoben PR #394) |
| AktivitaetenImSystem | `pages/AktivitaetenImSystem.tsx` | 2026-10-18 | ❌ leer wegen Bug #396 |
| MainLayout | `components/layout/MainLayout.tsx` | 2026-10-18 | ✅ (useProgrammfarbe ergänzt PR #394) |
| DarstellungSection | `components/settings/sections/DarstellungSection.tsx` | 2026-10-18 | ✅ |
| BerechtigungenSection | `components/settings/sections/BerechtigungenSection.tsx` | 2026-10-18 | ❌ POST/DELETE-Calls ohne Backend-Handler |
| Navigation | `components/layout/navigation.ts` | 2026-10-18 | ✅ (Leistungen aus Dokumente entfernt PR #394) |

## Migrationen (Flyway PostgreSQL)

Letzte Migration: `V9054__firma_briefpapier.sql` – vorhanden und korrekt.

## Offene Feature-Issues (kein Bug, Roadmap)

| Issue | Titel |
|---|---|
| #395 | Dokument-Editor: Leistungen/Artikel/Textblöcke einfügen (teilweise unklar) |
| #397 | Plantafel: Kalender-Termine spiegeln |
| #398 | Plantafel: Einheitliche Abfragemaske |
| #399 | Plantafel: Bürotage entfernen, nicht-verrechenbare Stunden |
| #400 | Plantafel: Projektplanung |
| #390 | Mobile-Navigation nach Benutzerrechten |
| #391 | KI-Entkopplung von Gemini |
