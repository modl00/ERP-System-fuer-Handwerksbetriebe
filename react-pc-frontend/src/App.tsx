import { createBrowserRouter, createRoutesFromElements, RouterProvider, Outlet, Navigate, Route } from 'react-router-dom';
import { MainLayout } from './components/layout/MainLayout';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastProvider } from './components/ui/toast';
import { ConfirmProvider } from './components/ui/confirm-dialog';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin, RequireAuth } from './auth/RouteGuards';
import { SessionGuard } from './auth/SessionGuard';
import { installSessionInterceptor } from './auth/sessionInterceptor';
import TextbausteinEditor from './pages/TextbausteinEditor';
import Leistungseditor from './pages/Leistungseditor';
import LagerEditor from './pages/LagerEditor';
import Kundeneditor from './pages/Kundeneditor';
import LieferantenEditor from './pages/LieferantenEditor';
import ArtikelEditor from './pages/ArtikelEditor';
import ArtikelDetail from './pages/ArtikelDetail';
import ArbeitsgangEditor from './pages/ArbeitsgangEditor';
import KostenrechnungPage from './pages/KostenrechnungPage';
import FremdleistungenPage from './pages/FremdleistungenPage';
import WerkstoffEditor from './pages/WerkstoffEditor';
import Aufschlaege from './pages/Aufschlaege';
import ProduktkategorieEditor from './pages/ProduktkategorieEditor';
import ProjektEditor from './pages/ProjektEditor';
import AnfrageEditor from './pages/AnfrageEditor';
import BestellungenUebersicht from './pages/BestellungenUebersicht';
import BestellListe from './pages/BestellListe';
import BestellungBearbeiten from './pages/BestellungBearbeiten';
import ErfolgsanalyseEditor from './pages/ErfolgsanalyseEditor';
import KostenstellenControllingEditor from './pages/KostenstellenControllingEditor';
import FormularwesenEditor from './pages/FormularwesenEditor';
import OffenePostenEditor from './pages/OffenePostenEditor';
import EmailCenter from './pages/EmailCenter';
import EmailTextvorlagenEditor from './pages/EmailTextvorlagenEditor';
import EmailEinstellungenPage from './pages/EmailEinstellungenPage';
import Plantafel from './pages/Plantafel';
import Startbildschirm from './pages/Startbildschirm';
import WartungUebersicht from './pages/WartungUebersicht';
import WartungProtokoll from './pages/WartungProtokoll';
import WartungModulEditor from './pages/WartungModulEditor';
import WartungAnlage from './pages/WartungAnlage';
import RechteUebersichtSeite from './pages/RechteUebersicht';
import MeinTagesverlauf from './pages/MeinTagesverlauf';
import AktivitaetenImSystem from './pages/AktivitaetenImSystem';
import AbteilungBerechtigungenEditor from './pages/AbteilungBerechtigungenEditor';
import Testbox from './pages/Testbox';
import { RequireModul } from './features/modulschalter/RequireModul';
import { RequireWartungModul } from './features/modulschalter/RequireWartungModul';
import { RequireLagerModul } from './features/modulschalter/RequireLagerModul';
import MietabrechnungEditor from './pages/MietabrechnungEditor';
import BenutzerEditor from './pages/BenutzerEditor';
import MitarbeiterEditor from './pages/MitarbeiterEditor';
import ZeiterfassungKalender from './pages/ZeiterfassungKalender';
import Monatsabschluss from './pages/Monatsabschluss';
import ZeiterfassungAuswertung from './pages/ZeiterfassungAuswertung';
import ZeiterfassungZeitkonten from './pages/ZeiterfassungZeitkonten';
import ZeiterfassungFeiertage from './pages/ZeiterfassungFeiertage';
import ZeiterfassungSteuerberater from './pages/ZeiterfassungSteuerberater';
import Urlaubsantraege from './pages/Urlaubsantraege';
import Langzeitkrankmeldungen from './pages/Langzeitkrankmeldungen';
import TerminKalender from './pages/TerminKalender';
import RechnungsuebersichtEditor from './pages/RechnungsuebersichtEditor';
import BelegeKasseEditor from './pages/BelegeKasseEditor';
import DokumentUebersichtEditor from './pages/DokumentUebersichtEditor';
import FirmaEditor from './pages/FirmaEditor';
import BestellungEditor from './pages/BestellungEditor';
import DocumentEditorPage from './pages/DocumentEditorPage';
import ArbeitszeitartEditor from './pages/ArbeitszeitartEditor';
import EinstellungenEditor from './pages/EinstellungenEditor';
import LoginPage from './pages/LoginPage';
import FirstLoginSetupPage from './pages/FirstLoginSetupPage';
import WebsiteEditor from './pages/WebsiteEditor';
import TelefonPage from './pages/TelefonPage';
import BenachrichtigungenPage from './pages/BenachrichtigungenPage';
import { AnrufFensterHost } from './features/telefon/AnrufFensterHost';
import { BESTELLBEDARF_PFAD, istFreigegeben } from './lib/freigaben';

// Install the global fetch interceptor once at module load time so that
// any HTTP 401 response from a non-auth endpoint triggers a session-expiry event.
installSessionInterceptor();

// Data router preserves the route tree and lets editors await saving before navigation.
const router = createBrowserRouter(createRoutesFromElements(
  <Route element={<><SessionGuard /><Outlet /></>}>

              {/* Public pages */}
              <Route path="/login" element={<ErrorBoundary><LoginPage /></ErrorBoundary>} />

              {/* Fullscreen pages outside MainLayout — still require authentication */}
              <Route path="/dokument-editor" element={<RequireAuth><><ErrorBoundary><DocumentEditorPage /></ErrorBoundary><AnrufFensterHost akteInNeuemTab /></></RequireAuth>} />
              <Route path="/onboarding" element={<RequireAdmin><ErrorBoundary><FirstLoginSetupPage /></ErrorBoundary></RequireAdmin>} />

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
                <Route path="/werkstoffe" element={<ErrorBoundary><WerkstoffEditor /></ErrorBoundary>} />
                <Route path="/aufschlaege" element={<ErrorBoundary><Aufschlaege /></ErrorBoundary>} />
                <Route path="/produktkategorien" element={<ErrorBoundary><ProduktkategorieEditor /></ErrorBoundary>} />
                <Route path="/projekte" element={<ErrorBoundary><ProjektEditor /></ErrorBoundary>} />
                <Route path="/anfragen" element={<ErrorBoundary><AnfrageEditor /></ErrorBoundary>} />
                <Route path="/bestellungen" element={<ErrorBoundary><BestellungenUebersicht /></ErrorBoundary>} />
                <Route path="/bestellungen/liste" element={<ErrorBoundary><BestellListe /></ErrorBoundary>} />
                <Route path="/bestellungen/bestellung/:id" element={<ErrorBoundary><BestellungBearbeiten /></ErrorBoundary>} />
                <Route path={BESTELLBEDARF_PFAD} element={istFreigegeben(BESTELLBEDARF_PFAD)
                    ? <ErrorBoundary><BestellungEditor /></ErrorBoundary>
                    : <Navigate to="/bestellungen" replace />} />
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
                <Route path="/emails" element={<Navigate to="/emails/inbox" replace />} />
                <Route path="/emails/:folder" element={<ErrorBoundary><EmailCenter /></ErrorBoundary>} />
                <Route path="/emails/:folder/:emailId" element={<ErrorBoundary><EmailCenter /></ErrorBoundary>} />
                <Route path="/email-textvorlagen" element={<ErrorBoundary><EmailTextvorlagenEditor /></ErrorBoundary>} />
                <Route path="/email-einstellungen" element={<ErrorBoundary><EmailEinstellungenPage /></ErrorBoundary>} />
                <Route path="/telefon" element={<Navigate to="/telefon/anrufe" replace />} />
                <Route path="/telefon/:reiter" element={<ErrorBoundary><TelefonPage /></ErrorBoundary>} />
                {/* Ziel der Glocke – für alle Angemeldeten; was jemand sieht, entscheidet der Server. */}
                <Route path="/benachrichtigungen" element={<ErrorBoundary><BenachrichtigungenPage /></ErrorBoundary>} />

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
                <Route path="/testbox" element={<RequireAdmin><ErrorBoundary><Testbox /></ErrorBoundary></RequireAdmin>} />
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
