# Software-Audit – Fortschritt
**Gestartet:** 2026-10-18  
**Status:** ✅ Abgeschlossen (Erstdurchlauf)

## Durchgeführte Prüfungen

### Backend
- [x] AktivitaetInterceptor – Registrierung geprüft → **Bug #396 bestätigt**
- [x] AbteilungBerechtigungController – Endpoints geprüft → **Bug #401 neu angelegt**
- [x] KostenstelleService – aktiv/jahresSoll-Mapping → **behoben in PR #393**
- [x] ArbeitsgangMapper – Formel-Felder → **behoben in PR #394**
- [x] FirmaController – Briefpapier-Endpoints → ✅ vorhanden
- [x] DateiSpeicherService – Path-Traversal → ✅ geschützt
- [x] SecurityConfig – ADMIN-Regeln → ✅ korrekt
- [x] Flyway-Migrationen V9040–V9054 → ✅ vollständig
- [x] AktivitaetKatalog – registrierte Aktionen → ✅ vorhanden (aber nie aufgerufen, da Interceptor fehlt)

### Frontend
- [x] TypeScript-Compile (`npx tsc -b --noEmit`) → ✅ 0 Fehler
- [x] DocumentEditor – Einfüge-Mechanismus → **Issue #395 (unklar, Analyse ergänzt)**
- [x] useProgrammfarbe – Registrierung in MainLayout → **behoben in PR #394**
- [x] ArtikelEditor – Tab-Entfernung → **behoben in PR #394**
- [x] Navigation – Leistungen aus Dokumente → **behoben in PR #394**
- [x] BerechtigungenSection – API-Aufrufe → **Bug #401 bestätigt**
- [x] Lock-Mechanismus (useDatensatzLock) → ✅ korrekt implementiert

## Offene Punkte
- Vitest-Tests: Timeout beim Ausführen im CI-losen Umfeld, kein Fehler gefunden
- Issue #395 (Einfügen im Dokument): Keine eindeutige Code-Ursache gefunden, Produktivsystem-Test empfohlen
