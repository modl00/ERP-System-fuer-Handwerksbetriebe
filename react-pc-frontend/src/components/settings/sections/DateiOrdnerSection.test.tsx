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
            expect(screen.getByText(/„Ordner prüfen“/)).toBeVisible();
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
