import { lazy } from 'react';
import { createBrowserRouter, createRoutesFromElements, RouterProvider, Outlet, Navigate, Route } from 'react-router-dom';
import { MainLayout } from './components/layout/MainLayout';
import { Seite } from './components/layout/Seite';
import { ToastProvider } from './components/ui/toast';
import { ConfirmProvider } from './components/ui/confirm-dialog';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin, RequireAuth } from './auth/RouteGuards';
import { SessionGuard } from './auth/SessionGuard';
import { installSessionInterceptor } from './auth/sessionInterceptor';
import { RequireModul } from './features/modulschalter/RequireModul';
import { RequireWartungModul } from './features/modulschalter/RequireWartungModul';
import { RequireLagerModul } from './features/modulschalter/RequireLagerModul';
import { AnrufFensterHost } from './features/telefon/AnrufFensterHost';

// Jede Seite ist eine eigene Datei und wird erst beim Öffnen geladen – der erste Aufruf
// (von unterwegs über Tailscale) lädt so nur das Gerüst und die aktuelle Seite.
const TextbausteinEditor = lazy(() => import('./pages/TextbausteinEditor'));
const LeistungenUndKategorien = lazy(() => import('./pages/leistungen/LeistungenUndKategorien'));
const Kundeneditor = lazy(() => import('./pages/Kundeneditor'));
const LieferantenEditor = lazy(() => import('./pages/LieferantenEditor'));
const ArtikelEditor = lazy(() => import('./pages/ArtikelEditor'));
const ArtikelDetail = lazy(() => import('./pages/ArtikelDetail'));
const ArbeitsgangEditor = lazy(() => import('./pages/ArbeitsgangEditor'));
const ProjektEditor = lazy(() => import('./pages/ProjektEditor'));
const AnfrageEditor = lazy(() => import('./pages/AnfrageEditor'));
const BestellungenUebersicht = lazy(() => import('./pages/BestellungenUebersicht'));
const ErfolgsanalyseEditor = lazy(() => import('./pages/ErfolgsanalyseEditor'));
const KostenstellenControllingEditor = lazy(() => import('./pages/KostenstellenControllingEditor'));
const FormularwesenEditor = lazy(() => import('./pages/FormularwesenEditor'));
const OffenePostenEditor = lazy(() => import('./pages/OffenePostenEditor'));
const EmailCenter = lazy(() => import('./pages/EmailCenter'));
const EmailTextvorlagenEditor = lazy(() => import('./pages/EmailTextvorlagenEditor'));
const EmailEinstellungenPage = lazy(() => import('./pages/EmailEinstellungenPage'));
const MietabrechnungEditor = lazy(() => import('./pages/MietabrechnungEditor'));
const BenutzerEditor = lazy(() => import('./pages/BenutzerEditor'));
const MitarbeiterEditor = lazy(() => import('./pages/MitarbeiterEditor'));
const ZeiterfassungKalender = lazy(() => import('./pages/ZeiterfassungKalender'));
const Monatsabschluss = lazy(() => import('./pages/Monatsabschluss'));
const ZeiterfassungAuswertung = lazy(() => import('./pages/ZeiterfassungAuswertung'));
const ZeiterfassungZeitkonten = lazy(() => import('./pages/ZeiterfassungZeitkonten'));
const ZeiterfassungFeiertage = lazy(() => import('./pages/ZeiterfassungFeiertage'));
const ZeiterfassungSteuerberater = lazy(() => import('./pages/ZeiterfassungSteuerberater'));
const Urlaubsantraege = lazy(() => import('./pages/Urlaubsantraege'));
const Langzeitkrankmeldungen = lazy(() => import('./pages/Langzeitkrankmeldungen'));
const TerminKalender = lazy(() => import('./pages/TerminKalender'));
const RechnungsuebersichtEditor = lazy(() => import('./pages/RechnungsuebersichtEditor'));
const BelegeKasseEditor = lazy(() => import('./pages/BelegeKasseEditor'));
const DokumentUebersichtEditor = lazy(() => import('./pages/DokumentUebersichtEditor'));
const Eingangskorb = lazy(() => import('./pages/Eingangskorb'));
const FirmaEditor = lazy(() => import('./pages/FirmaEditor'));
const BestellungEditor = lazy(() => import('./pages/BestellungEditor'));
const DocumentEditorPage = lazy(() => import('./pages/DocumentEditorPage'));
const ArbeitszeitartEditor = lazy(() => import('./pages/ArbeitszeitartEditor'));
const EinstellungenEditor = lazy(() => import('./pages/EinstellungenEditor'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const FirstLoginSetupPage = lazy(() => import('./pages/FirstLoginSetupPage'));
const WebsiteEditor = lazy(() => import('./pages/WebsiteEditor'));
const TelefonPage = lazy(() => import('./pages/TelefonPage'));
const BenachrichtigungenPage = lazy(() => import('./pages/BenachrichtigungenPage'));

// Install the global fetch interceptor once at module load time so that
// any HTTP 401 response from a non-auth endpoint triggers a session-expiry event.
installSessionInterceptor();

// Data router preserves the route tree and lets editors await saving before navigation.
const router = createBrowserRouter(createRoutesFromElements(
  <Route element={<><SessionGuard /><Outlet /></>}>

              {/* Public pages */}
              <Route path="/login" element={<Seite vollbild><LoginPage /></Seite>} />

              {/* Fullscreen pages outside MainLayout — still require authentication */}
              <Route path="/dokument-editor" element={<RequireAuth><><Seite vollbild><DocumentEditorPage /></Seite><AnrufFensterHost akteInNeuemTab /></></RequireAuth>} />
              <Route path="/onboarding" element={<RequireAdmin><Seite vollbild><FirstLoginSetupPage /></Seite></RequireAdmin>} />

              <Route element={<RequireAuth><MainLayout /></RequireAuth>}>
                <Route path="/" element={<ErrorBoundary><Startbildschirm /></ErrorBoundary>} />
                <Route path="/start" element={<ErrorBoundary><Startbildschirm /></ErrorBoundary>} />

                <Route path="/textbausteine" element={<ErrorBoundary><TextbausteinEditor /></ErrorBoundary>} />
                <Route path="/leistungen" element={<ErrorBoundary><Leistungseditor /></ErrorBoundary>} />
                <Route path="/arbeitszeitarten" element={<ErrorBoundary><ArbeitszeitartEditor /></ErrorBoundary>} />
                <Route path="/kunden" element={<ErrorBoundary><Kundeneditor /></ErrorBoundary>} />
                <Route path="/mitarbeiter" element={<ErrorBoundary><MitarbeiterEditor /></ErrorBoundary>} />
                <Route path="/lieferanten" element={<ErrorBoundary><LieferantenEditor /></ErrorBoundary>} />
                <Route path="/artikel" element={<ErrorBoundary><ArtikelEditor /></ErrorBoundary>} />
                <Route path="/artikel/:id" element={<ErrorBoundary><ArtikelDetail /></ErrorBoundary>} />
                <Route path="/arbeitsgaenge" element={<ErrorBoundary><ArbeitsgangEditor /></ErrorBoundary>} />
                <Route path="/kostenrechnung" element={<RequireAdmin><ErrorBoundary><KostenrechnungPage /></ErrorBoundary></RequireAdmin>} />
                <Route path="/fremdleistungen" element={<ErrorBoundary><FremdleistungenPage /></ErrorBoundary>} />
                <Route path="/lager" element={<RequireLagerModul><ErrorBoundary><LagerEditor /></ErrorBoundary></RequireLagerModul>} />
                <Route path="/werkstoffe" element={<Navigate to="/artikel#werkstoffe" replace />} />
                <Route path="/aufschlaege" element={<Navigate to="/artikel#aufschlaege" replace />} />
                <Route path="/produktkategorien" element={<ErrorBoundary><ProduktkategorieEditor /></ErrorBoundary>} />
                <Route path="/projekte" element={<ErrorBoundary><ProjektEditor /></ErrorBoundary>} />
                <Route path="/anfragen" element={<ErrorBoundary><AnfrageEditor /></ErrorBoundary>} />
                <Route path="/bestellungen" element={<ErrorBoundary><BestellungenUebersicht /></ErrorBoundary>} />
                <Route path="/bestellungen/liste" element={<ErrorBoundary><BestellListe /></ErrorBoundary>} />
                <Route path="/bestellungen/bestellung/:id" element={<ErrorBoundary><BestellungBearbeiten /></ErrorBoundary>} />
                <Route path="/bestellungen/bedarf" element={<Navigate to="/bestellungen/liste" replace />} />
                <Route path="/wartung" element={<RequireWartungModul><ErrorBoundary><WartungUebersicht /></ErrorBoundary></RequireWartungModul>} />
                <Route path="/wartung/:id" element={<RequireWartungModul><ErrorBoundary><WartungProtokoll /></ErrorBoundary></RequireWartungModul>} />
                <Route path="/wartung/anlagen/:tuerId" element={<RequireWartungModul><ErrorBoundary><WartungAnlage /></ErrorBoundary></RequireWartungModul>} />
                <Route path="/wartung/module/:id" element={<RequireAdmin><RequireWartungModul><ErrorBoundary><WartungModulEditor /></ErrorBoundary></RequireWartungModul></RequireAdmin>} />
                <Route path="/kalender" element={<ErrorBoundary><TerminKalender /></ErrorBoundary>} />
                <Route path="/plantafel" element={<ErrorBoundary><Plantafel /></ErrorBoundary>} />
                <Route path="/analyse" element={<ErrorBoundary><ErfolgsanalyseEditor /></ErrorBoundary>} />
                <Route path="/kostenstellen" element={<ErrorBoundary><KostenstellenControllingEditor /></ErrorBoundary>} />
                <Route path="/website" element={<RequireAdmin><ErrorBoundary><WebsiteEditor /></ErrorBoundary></RequireAdmin>} />
                <Route path="/formulare" element={<RequireAdmin><ErrorBoundary><FormularwesenEditor /></ErrorBoundary></RequireAdmin>} />
                <Route path="/offeneposten" element={<ErrorBoundary><OffenePostenEditor /></ErrorBoundary>} />
                <Route path="/rechnungsuebersicht" element={<ErrorBoundary><RechnungsuebersichtEditor /></ErrorBoundary>} />
                <Route path="/belege-kasse" element={<ErrorBoundary><BelegeKasseEditor /></ErrorBoundary>} />
                <Route path="/dokumentuebersicht" element={<ErrorBoundary><DokumentUebersichtEditor /></ErrorBoundary>} />
                <Route path="/eingangskorb" element={<ErrorBoundary><Eingangskorb /></ErrorBoundary>} />
                <Route path="/emails" element={<Navigate to="/emails/inbox" replace />} />
                <Route path="/emails/:folder" element={<Seite><EmailCenter /></Seite>} />
                <Route path="/emails/:folder/:emailId" element={<Seite><EmailCenter /></Seite>} />
                <Route path="/email-textvorlagen" element={<Seite><EmailTextvorlagenEditor /></Seite>} />
                <Route path="/email-einstellungen" element={<Seite><EmailEinstellungenPage /></Seite>} />
                <Route path="/telefon" element={<Navigate to="/telefon/anrufe" replace />} />
                <Route path="/telefon/:reiter" element={<Seite><TelefonPage /></Seite>} />
                {/* Ziel der Glocke – für alle Angemeldeten; was jemand sieht, entscheidet der Server. */}
                <Route path="/benachrichtigungen" element={<Seite><BenachrichtigungenPage /></Seite>} />

                <Route path="/miete" element={<RequireModul modul="miete" titel="Mietabrechnung" ribbonCategory="Finanzen & Controlling" modulName="Das Mietabrechnungsmodul" erhalten="Die bisherigen Mietobjekte und Abrechnungen"><ErrorBoundary><MietabrechnungEditor /></ErrorBoundary></RequireModul>} />
                <Route path="/benutzer" element={<RequireAdmin><ErrorBoundary><BenutzerEditor /></ErrorBoundary></RequireAdmin>} />

                {/* Zeiterfassung & Admin */}
                <Route path="/monatsabschluss" element={<ErrorBoundary><Monatsabschluss /></ErrorBoundary>} />
                <Route path="/zeitbuchungen" element={<ErrorBoundary><ZeiterfassungKalender /></ErrorBoundary>} />
                <Route path="/auswertung" element={<ErrorBoundary><ZeiterfassungAuswertung /></ErrorBoundary>} />
                <Route path="/steuerberater" element={<ErrorBoundary><ZeiterfassungSteuerberater /></ErrorBoundary>} />
                <Route path="/zeitkonten" element={<ErrorBoundary><ZeiterfassungZeitkonten /></ErrorBoundary>} />
                <Route path="/feiertage" element={<ErrorBoundary><ZeiterfassungFeiertage /></ErrorBoundary>} />
                <Route path="/urlaubsantraege" element={<ErrorBoundary><Urlaubsantraege /></ErrorBoundary>} />
                <Route path="/langzeitkrankmeldungen" element={<ErrorBoundary><Langzeitkrankmeldungen /></ErrorBoundary>} />
                <Route path="/rechte-uebersicht" element={<RequireAdmin><ErrorBoundary><RechteUebersichtSeite /></ErrorBoundary></RequireAdmin>} />
                <Route path="/mein-tagesverlauf" element={<ErrorBoundary><MeinTagesverlauf /></ErrorBoundary>} />
                <Route path="/aktivitaeten" element={<RequireAdmin><ErrorBoundary><AktivitaetenImSystem /></ErrorBoundary></RequireAdmin>} />
                <Route path="/abteilung-berechtigungen" element={<RequireAdmin><ErrorBoundary><AbteilungBerechtigungenEditor /></ErrorBoundary></RequireAdmin>} />

                <Route path="/firma" element={<RequireAdmin><ErrorBoundary><FirmaEditor /></ErrorBoundary></RequireAdmin>} />
                <Route path="/einstellungen" element={<RequireAdmin><ErrorBoundary><EinstellungenEditor /></ErrorBoundary></RequireAdmin>} />

                <Route path="*" element={<Navigate to="/projekte" replace />} />
              </Route>

  </Route>
));

export default function App() {
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}
