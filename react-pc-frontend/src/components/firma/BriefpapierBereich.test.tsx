import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { BriefpapierBereich } from './BriefpapierBereich';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten });
let fetcher: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetcher = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url === '/api/firma/briefpapier' && methode === 'POST') return antwort({ briefpapierDateiname: 'briefpapier.png', briefpapierEnthaeltKopf: false });
        if (url === '/api/firma/briefpapier' && methode === 'DELETE') return antwort({ briefpapierDateiname: null, briefpapierEnthaeltKopf: false });
        if (url === '/api/firma/briefpapier/einstellungen') return antwort({});
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (props: { name?: string | null; kopf?: boolean } = {}) => {
    const onGeaendert = vi.fn();
    render(<ToastProvider><ConfirmProvider><BriefpapierBereich briefpapierDateiname={props.name ?? null} briefpapierEnthaeltKopf={props.kopf ?? false} onGeaendert={onGeaendert} /></ConfirmProvider></ToastProvider>);
    return onGeaendert;
};

const datei = (name: string, typ: string, groesse = 10) => new File([new Uint8Array(groesse)], name, { type: typ });

it('erklärt, dass die Datei auf ganz A4 gezogen wird, und bietet das Hochladen an', () => {
    zeige();

    expect(screen.getByText(/auf die ganze A4-Seite gezogen/)).toBeVisible();
    expect(screen.getByText('Kein Briefpapier hinterlegt')).toBeVisible();
    expect(screen.getByText('Briefpapier hochladen')).toBeVisible();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
});

it('lädt ein Bild hoch und meldet den neuen Stand', async () => {
    const user = userEvent.setup();
    const onGeaendert = zeige();

    await user.upload(screen.getByLabelText('Briefpapier auswählen'), datei('mein papier.png', 'image/png'));

    await waitFor(() => expect(onGeaendert).toHaveBeenCalledWith({ briefpapierDateiname: 'briefpapier.png', briefpapierEnthaeltKopf: false }));
    const aufruf = fetcher.mock.calls.find(([u]) => u === '/api/firma/briefpapier')!;
    expect(aufruf[1].method).toBe('POST');
    expect((aufruf[1].body as FormData).get('datei')).toBeInstanceOf(File);
    expect(await screen.findByText(/Briefpapier gespeichert/)).toBeVisible();
});

it('lehnt falsche Dateitypen und zu große Dateien ab, ohne etwas zu senden', async () => {
    const user = userEvent.setup({ applyAccept: false });
    zeige();

    await user.upload(screen.getByLabelText('Briefpapier auswählen'), datei('x.exe', 'application/octet-stream'));
    expect(await screen.findByText(/Ungültiger Dateityp/)).toBeVisible();
    await user.upload(screen.getByLabelText('Briefpapier auswählen'), datei('riesig.png', 'image/png', 31 * 1024 * 1024));
    expect(await screen.findByText(/Datei zu groß/)).toBeVisible();
    expect(fetcher).not.toHaveBeenCalled();
});

it('zeigt die Fehlermeldung des Servers beim Hochladen', async () => {
    const user = userEvent.setup();
    fetcher.mockResolvedValue(antwort({ message: 'Das Bild ist zu groß (mehr als 400 Megapixel). Bitte verkleinern.' }, false, 400));
    const onGeaendert = zeige();

    await user.upload(screen.getByLabelText('Briefpapier auswählen'), datei('x.png', 'image/png'));

    expect(await screen.findByText(/400 Megapixel/)).toBeVisible();
    expect(onGeaendert).not.toHaveBeenCalled();
});

it('zeigt ein vorhandenes Bild als Vorschau und ein PDF als Symbol', () => {
    const { unmount } = render(<ToastProvider><ConfirmProvider><BriefpapierBereich briefpapierDateiname="briefpapier.png" briefpapierEnthaeltKopf={false} onGeaendert={() => {}} /></ConfirmProvider></ToastProvider>);
    expect(screen.getByAltText('Briefpapier')).toHaveAttribute('src', expect.stringContaining('/api/firma/briefpapier'));
    expect(screen.getByText('Briefpapier ersetzen')).toBeVisible();
    unmount();

    render(<ToastProvider><ConfirmProvider><BriefpapierBereich briefpapierDateiname="briefpapier.pdf" briefpapierEnthaeltKopf={false} onGeaendert={() => {}} /></ConfirmProvider></ToastProvider>);
    expect(screen.queryByAltText('Briefpapier')).not.toBeInTheDocument();
    expect(screen.getByText('PDF')).toBeVisible();
});

it('entfernt das Briefpapier erst nach Bestätigung', async () => {
    const user = userEvent.setup();
    const onGeaendert = zeige({ name: 'briefpapier.png' });

    await user.click(screen.getByRole('button', { name: /Entfernen/ }));
    expect(fetcher).not.toHaveBeenCalled();
    await user.click(await screen.findByRole('button', { name: 'Löschen' }));

    await waitFor(() => expect(onGeaendert).toHaveBeenCalledWith({ briefpapierDateiname: null, briefpapierEnthaeltKopf: false }));
    expect(fetcher.mock.calls.some(([u, i]) => u === '/api/firma/briefpapier' && i?.method === 'DELETE')).toBe(true);
});

it('schaltet "enthält schon Briefkopf und Fußzeile" sofort um', async () => {
    const user = userEvent.setup();
    const onGeaendert = zeige({ name: 'briefpapier.png' });

    await user.click(screen.getByRole('checkbox', { name: /enthält schon Briefkopf und Fußzeile/ }));

    await waitFor(() => expect(onGeaendert).toHaveBeenCalledWith({ briefpapierDateiname: 'briefpapier.png', briefpapierEnthaeltKopf: true }));
    const aufruf = fetcher.mock.calls.find(([u]) => u === '/api/firma/briefpapier/einstellungen')!;
    expect(JSON.parse(aufruf[1].body)).toEqual({ briefpapierEnthaeltKopf: true });
});
