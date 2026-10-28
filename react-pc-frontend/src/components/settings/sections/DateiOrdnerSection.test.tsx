import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DateiOrdnerSection } from './DateiOrdnerSection';
import { ToastProvider } from '../../ui/toast';

const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });
let gesendet: { url: string; methode: string; body?: Record<string, unknown> }[];

function stub(daten: Record<string, unknown>) {
    gesendet = [];
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (methode === 'GET') return json(daten);
        gesendet.push({ url: String(url), methode, body: init?.body ? JSON.parse(String(init.body)) : undefined });
        return json({ success: true, message: 'Ordner gefunden und beschreibbar: /app/uploads/CADdrawings' });
    }));
}
const rendern = () => render(<ToastProvider><DateiOrdnerSection /></ToastProvider>);

beforeEach(() => { /* jedes Test stubbt selbst */ });
afterEach(() => { vi.unstubAllGlobals(); });

describe('Datei-Ordner: Docker und Windows', { timeout: 30000 }, () => {
    it('im Docker: Ordner im Container mit Standard, Adresse für die Windows-Rechner, Anleitung fürs NAS statt Windows-Freigabe', async () => {
        const user = userEvent.setup();
        stub({ pfad: '/app/uploads/CADdrawings', networkUrl: '', konfiguriert: true, umgebung: 'DOCKER', freigabeMoeglich: false, standardPfad: '/app/uploads/CADdrawings' });
        rendern();

        expect(await screen.findByLabelText('Ordner im Container')).toHaveValue('/app/uploads/CADdrawings');
        expect(screen.getByTestId('docker-ordner-hinweis')).toHaveTextContent('ERP_DATEN/dokumente/CADdrawings');
        expect(screen.getByLabelText('Netzwerk-Adresse für die Windows-Rechner (HiCAD, Tenado)')).toBeVisible();
        expect(screen.queryByLabelText('Ordner auf diesem Rechner')).toBeNull();

        await user.click(screen.getByRole('button', { name: /So gibst du den Datenordner am NAS frei/ }));
        expect(screen.getByTestId('nas-anleitung')).toHaveTextContent('Gemeinsamer Ordner');
        expect(screen.getByTestId('nas-anleitung')).toHaveTextContent('docker-compose.netzwerk.yml');
        expect(screen.queryByRole('button', { name: /automatisch freigeben/ })).toBeNull();
    });

    it('im Docker lässt sich der Standard wieder einsetzen, und die Adresse darf smb:// sein', async () => {
        const user = userEvent.setup();
        stub({ pfad: '/daten/anders', networkUrl: '', konfiguriert: true, umgebung: 'DOCKER', freigabeMoeglich: false, standardPfad: '/app/uploads/CADdrawings' });
        rendern();
        const pfad = await screen.findByLabelText('Ordner im Container');

        await user.click(screen.getByRole('button', { name: 'Standard verwenden' }));
        expect(pfad).toHaveValue('/app/uploads/CADdrawings');
        expect(screen.queryByRole('button', { name: 'Standard verwenden' })).toBeNull();
        await user.type(screen.getByLabelText(/Netzwerk-Adresse für die Windows-Rechner/), 'smb://nas/erp/dokumente/CADdrawings');
        await user.click(screen.getByRole('button', { name: 'Datei-Ordner speichern' }));

        await waitFor(() => expect(gesendet.some(g => g.methode === 'PUT')).toBe(true));
        expect(gesendet.find(g => g.methode === 'PUT')!.body).toEqual({ pfad: '/app/uploads/CADdrawings', networkUrl: 'smb://nas/erp/dokumente/CADdrawings' });
    });

    it('unter Windows bleibt alles wie bisher, mit der automatischen Freigabe', async () => {
        const user = userEvent.setup();
        stub({ pfad: 'C:\\Zeichnungen', networkUrl: '', konfiguriert: true, umgebung: 'WINDOWS', freigabeMoeglich: true, standardPfad: 'C:\\App\\uploads\\CADdrawings' });
        rendern();

        expect(await screen.findByLabelText('Ordner auf diesem Rechner')).toHaveValue('C:\\Zeichnungen');
        expect(screen.queryByTestId('docker-ordner-hinweis')).toBeNull();
        await user.click(screen.getByRole('button', { name: /So gibst du den Ordner im Netzwerk frei/ }));
        expect(screen.getByRole('button', { name: /automatisch freigeben/ })).toBeVisible();
        expect(screen.queryByTestId('nas-anleitung')).toBeNull();
    });

    it('ohne Angabe der Umgebung (älterer Server) gilt Windows wie bisher', async () => {
        stub({ pfad: 'C:\\Zeichnungen', networkUrl: '', konfiguriert: true });
        rendern();

        expect(await screen.findByLabelText('Ordner auf diesem Rechner')).toBeVisible();
    });

    describe('Netzwerkordner mit Benutzer und Passwort (nur Docker)', () => {
        const docker = { pfad: '/app/uploads/CADdrawings', networkUrl: '', konfiguriert: true, umgebung: 'DOCKER', freigabeMoeglich: false, standardPfad: '/app/uploads/CADdrawings' };

        it('im Docker erzeugt die Hilfe die Zeilen für den Stack; das Passwort wird nicht abgefragt', async () => {
            const user = userEvent.setup();
            stub(docker);
            rendern();
            await screen.findByLabelText('Ordner im Container');
            await user.click(screen.getByRole('button', { name: /Die Dateien liegen auf einem anderen Gerät/ }));

            expect(screen.queryByLabelText(/Passwort/i)).toBeNull();
            await user.type(screen.getByLabelText('Adresse der Freigabe'), '\\\\nas.local\\erp-dokumente');
            expect(screen.getByRole('alert')).toHaveTextContent('Benutzer');
            await user.type(screen.getByLabelText('Benutzer der Freigabe'), 'erika');

            const zeilen = screen.getByTestId('smb-zeilen');
            expect(zeilen).toHaveTextContent('ERP_SMB_ADRESSE=nas.local/erp-dokumente');
            expect(zeilen).toHaveTextContent('ERP_SMB_BENUTZER=erika');
            expect(zeilen).toHaveTextContent('ERP_SMB_PASSWORT=<hier Ihr Passwort eintragen>');
            expect(zeilen).toHaveTextContent('ERP_SMB_OPTIONEN=vers=3.0');
            expect(screen.getByText(/docker-compose.netzwerk.yml/)).toBeVisible();
            expect(screen.getByText(/„Ordner prüfen"/)).toBeVisible();
            // Es wird nichts an den Server gesendet
            expect(gesendet).toHaveLength(0);
        });

        it('SMB 1 wird mit Warnung angeboten, und Kopieren legt die Zeilen in die Zwischenablage', async () => {
            const user = userEvent.setup();
            const schreibe = vi.fn(async () => undefined);
            Object.defineProperty(navigator, 'clipboard', { value: { writeText: schreibe }, configurable: true });
            stub(docker);
            rendern();
            await screen.findByLabelText('Ordner im Container');
            await user.click(screen.getByRole('button', { name: /Die Dateien liegen auf einem anderen Gerät/ }));
            await user.type(screen.getByLabelText('Adresse der Freigabe'), 'nas/erp');
            await user.type(screen.getByLabelText('Benutzer der Freigabe'), 'max');
            await user.selectOptions(screen.getByLabelText('Alter des Geräts'), 'vers=1.0');
            expect(screen.getByRole('status')).toHaveTextContent('unsicher');

            await user.click(screen.getByRole('button', { name: /Zeilen kopieren/ }));
            expect(schreibe).toHaveBeenCalledWith(expect.stringContaining('ERP_SMB_OPTIONEN=vers=1.0'));
        });

        it('unter Windows gibt es die Hilfe nicht', async () => {
            stub({ pfad: 'C:\\Zeichnungen', networkUrl: '', konfiguriert: true, umgebung: 'WINDOWS', freigabeMoeglich: true, standardPfad: '' });
            rendern();
            await screen.findByLabelText('Ordner auf diesem Rechner');
            expect(screen.queryByTestId('smb-hilfe')).toBeNull();
        });
    });
});

// TODO: Upstream-Tests für ERP-Paket-Ordner-Freigabe (containerFreigabe) noch nicht portiert
const FREIGABE = '\\\\erp-dateien.example.ts.net\\CADdrawings';
const FREIGABE_PFAD = '/app/uploads/CADdrawings';
const ALTE_ADRESSE = '\\\\BUERO-PC\\Zeichnungen';
const FELD = 'Netzwerk-Adresse für Kollegen (optional)';
const HINWEIS = 'Ordner-Freigabe des ERP ist eingeschaltet';
const ANLEITUNG = 'So geben Sie den Ordner im Netzwerk frei';
const EINTRAGEN = 'Diese Adresse eintragen';
const ORDNER_WARNUNG = 'So würden die Links ins Leere zeigen.';
const LAUNCHER_SATZ = 'Benutzer und Passwort für den Ordner stecken schon in der Einrichtung';
const LAUNCHER_ERST_SPEICHERN = 'Erst oben die Adresse der Ordner-Freigabe eintragen und speichern';

describe('DateiOrdnerSection – Ordner-Freigabe aus dem ERP-Paket', () => {
    let antwort: Record<string, unknown>;
    let gesendet: { pfad: string; networkUrl: string } | null;

    beforeEach(() => {
        antwort = {
            pfad: FREIGABE_PFAD,
            networkUrl: ALTE_ADRESSE,
            konfiguriert: true,
            containerFreigabe: FREIGABE,
            containerFreigabePfad: FREIGABE_PFAD,
        };
        gesendet = null;
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/settings/datei-ordner' && init?.method === 'PUT') {
                gesendet = JSON.parse(String(init.body));
                return new Response('{}', { status: 200 });
            }
            if (url === '/api/settings/datei-ordner') {
                return new Response(JSON.stringify(antwort), { status: 200 });
            }
            return new Response('{}', { status: 404 });
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    function zeige() {
        return render(<ToastProvider><DateiOrdnerSection /></ToastProvider>);
    }

    it('verhält sich ohne Freigabe wie bisher: kein Hinweis, Windows-Anleitung da', async () => {
        antwort = { pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE, konfiguriert: true, containerFreigabe: null, containerFreigabePfad: null };
        zeige();

        await screen.findByLabelText(FELD);
        expect(screen.queryByText(HINWEIS)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: ANLEITUNG })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_SATZ, { exact: false })).not.toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).not.toBeInTheDocument();
    });

    it('behandelt ein fehlendes Feld (älterer Server) wie keine Freigabe', async () => {
        antwort = { pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE, konfiguriert: true };
        zeige();

        await screen.findByLabelText(FELD);
        expect(screen.queryByText(HINWEIS)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: ANLEITUNG })).toBeInTheDocument();
    });

    it('zeigt die Adresse der Freigabe und blendet die Windows-Anleitung aus', async () => {
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText(FREIGABE)).toBeInTheDocument();
        expect(within(hinweis).getByRole('button', { name: 'Mehr zu: Ordner-Freigabe des ERP' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: ANLEITUNG })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Ordner automatisch freigeben/ })).not.toBeInTheDocument();
        // Der Ordner passt schon: keine Warnung, nur die Adresse fehlt noch.
        expect(screen.queryByText(ORDNER_WARNUNG, { exact: false })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: EINTRAGEN })).toBeInTheDocument();
        // Gespeichert ist noch die alte Adresse: die Karte sagt, dass erst gespeichert werden muss.
        expect(screen.getByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_SATZ, { exact: false })).not.toBeInTheDocument();
    });

    it('erklärt am Info-Knopf Hintergrund und Wirkung', async () => {
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Ordner-Freigabe des ERP' }));
        const erklaerung = screen.getByRole('dialog', { name: 'Ordner-Freigabe des ERP' });
        expect(erklaerung).toHaveTextContent('Das ERP-Paket bringt eine eigene Ordner-Freigabe mit.');
        expect(erklaerung).toHaveTextContent('nur im eigenen Tailscale-Netz erreichbar');
        expect(erklaerung).toHaveTextContent('Die tägliche Sicherung nimmt den Ordner automatisch mit.');
        expect(erklaerung).toHaveTextContent('Bereits hochgeladene Zeichnungen und Tabellen bleiben, wo sie sind');
    });

    it('trägt die Adresse per Klick ins Feld ein und zeigt danach „Eingetragen"', async () => {
        zeige();

        const feld = await screen.findByLabelText(FELD);
        expect(feld).toHaveValue(ALTE_ADRESSE);
        expect(screen.getByText('Danach speichern', { exact: false })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: EINTRAGEN }));

        expect(feld).toHaveValue(FREIGABE);
        expect(feld).toHaveFocus();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
        expect(screen.getByText('Eingetragen')).toBeInTheDocument();
        // Eingetragen heißt noch nicht gespeichert: die Hilfsprogramm-Karte erinnert ans Speichern.
        expect(screen.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeInTheDocument();
        expect(gesendet).toBeNull();
    });

    it('erkennt Adresse und Ordner auch mit anderer Schreibweise, Leerzeichen und Schrägstrich am Ende als eingetragen', async () => {
        antwort = { ...antwort, networkUrl: `  ${FREIGABE.toUpperCase()} `, pfad: ` ${FREIGABE_PFAD}/ ` };
        zeige();

        expect(await screen.findByText('Eingetragen')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: EINTRAGEN })).not.toBeInTheDocument();
    });

    it('schickt die übernommene Adresse beim Speichern mit', async () => {
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: EINTRAGEN }));
        fireEvent.click(screen.getByRole('button', { name: /Datei-Ordner speichern/ }));

        await waitFor(() => expect(gesendet).toEqual({ pfad: FREIGABE_PFAD, networkUrl: FREIGABE }));
        await waitFor(() => expect(screen.queryByText(/Bitte erst speichern/)).not.toBeInTheDocument());
        // Jetzt ist genau die Adresse der Freigabe gespeichert: die Zugangsdaten kommen mit der Einrichtung.
        expect(screen.getByText(LAUNCHER_SATZ, { exact: false })).toBeInTheDocument();
        expect(screen.queryByText(LAUNCHER_ERST_SPEICHERN, { exact: false })).not.toBeInTheDocument();
    });

    it('bietet bei abweichendem Ordner keinen Knopf an, sondern verweist an den Administrator', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen' };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText(ORDNER_WARNUNG, { exact: false })).toHaveTextContent(
            `Die Freigabe gibt den Ordner ${FREIGABE_PFAD} frei, das Programm legt Zeichnungen aber in C:\\Zeichnungen ab. `
            + 'So würden die Links ins Leere zeigen. Bitte den Administrator fragen: Er stellt die Freigabe auf den '
            + 'bisherigen Ordner um oder zieht die Dateien um.',
        );
        expect(within(hinweis).queryByRole('button', { name: /eintragen/ })).not.toBeInTheDocument();
        expect(within(hinweis).queryByText('Eingetragen')).not.toBeInTheDocument();

        // Der Ordner bleibt, wie er ist – auch beim Speichern.
        expect(screen.getByLabelText('Ordner auf diesem Rechner')).toHaveValue('C:\\Zeichnungen');
        fireEvent.click(screen.getByRole('button', { name: /Datei-Ordner speichern/ }));
        await waitFor(() => expect(gesendet).toEqual({ pfad: 'C:\\Zeichnungen', networkUrl: ALTE_ADRESSE }));
    });

    it('zeigt auch bei schon eingetragener Adresse kein „Eingetragen", solange der Ordner abweicht', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen', networkUrl: FREIGABE };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).queryByText('Eingetragen')).not.toBeInTheDocument();
        expect(within(hinweis).getByText(ORDNER_WARNUNG, { exact: false })).toBeInTheDocument();
    });

    it('vergleicht nur die Adresse, wenn der Server keinen Ordner zur Freigabe meldet', async () => {
        antwort = { ...antwort, pfad: 'C:\\Zeichnungen', networkUrl: FREIGABE, containerFreigabePfad: null };
        zeige();

        const hinweis = await screen.findByRole('region', { name: HINWEIS });
        expect(within(hinweis).getByText('Eingetragen')).toBeInTheDocument();
        expect(within(hinweis).queryByText(ORDNER_WARNUNG, { exact: false })).not.toBeInTheDocument();
    });
});
