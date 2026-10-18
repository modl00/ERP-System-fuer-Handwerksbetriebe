# Software-Audit – Fortschritt
**Gestartet:** 2026-10-18  
**Status:** ✅ Abgeschlossen (Vollständig)

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

### Zweiter Durchlauf (Restmodule)
- [x] Formularwesen / RechnungPdfService – textAlign ignoriert → **Bug #402, in PR #403 behoben**
- [x] Kostenrechnung (KostenrechnungController, KostensatzService) → ✅ korrekt
- [x] Plantafel (PlanBelegung, PlantafelController) → ✅ Kalender-Termine bereits integriert
- [x] Zeiterfassung (ZeiterfassungApiController) → ✅ NPE-Pfade durch catch(RuntimeException) abgedeckt
- [x] Leistungsmodul (LeistungKalkulationService) → ✅ Formelvariablen korrekt gemappt
- [x] Bestellwesen (LieferantenBestellungController) → ✅ vollständig CRUD
- [x] E-Mail (UnifiedEmailController) → ✅ getOriginalFilename nur für Content-Type/Header, kein Dateisystem-Zugriff
- [x] Wartung (WartungController-Familie) → ✅ keine unsanitierten Dateinamen
- [x] Lager (LagerController + LagerService) → ✅ korrekt
- [x] Buchhaltung / Offene Posten (OffenePostenController, MonatsabschlussUebersichtRepository) → ✅ deaktivierte Mitarbeiter korrekt eingeschlossen

## Behobene Bugs (PR #403)
- Issue #396: AktivitaetInterceptor in WebConfig registriert ✅
- Issue #401: POST/DELETE /api/abteilungen ergänzt ✅
- Issue #402: PDF-Textausrichtung in baueTextSpalte ergänzt ✅

## Offene Punkte
- Issue #395 (Einfügen im Dokument): Keine eindeutige Code-Ursache gefunden, Produktivsystem-Test empfohlen
