import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiAbgleichDialog } from './KiAbgleichDialog';
import { ToastProvider } from '../../components/ui/toast';

/** Nur Dummy-Daten. */
const vorschlaege = [
    { emailId: 1, betreff: 'Angebot Geländer', von: 'max@mustermann.example', art: 'PROJEKT', zielId: 5, ziel: 'Geländer Halle', hinweis: null },
    { emailId: 2, betreff: 'Rechnung 4711', von: 'info@stahl.example', art: 'LIEFERANT', zielId: 9, ziel: 'Muster Stahl GmbH', hinweis: null },
    { emailId: 3, betreff: 'Hallo', von: 'unbekannt@x.example', art: 'KEINE', zielId: null, ziel: null, hinweis: 'Keine passende Zuordnung gefunden.' },
];
let fetcher: ReturnType<typeof vi.fn>;
const json = (b: unknown, s = 200) => ({ ok: s < 300, status: s, json: async () => b, text: async () => JSON.stringify(b) }) as Response;
const zeige = (fertig = vi.fn()) => { render(<ToastProvider><KiAbgleichDialog open emailIds={[1, 2, 3]} onOpenChange={vi.fn()} onUebernommen={fertig} /></ToastProvider>); return fertig; };
const postBody = (teil: string) => JSON.parse((fetcher.mock.calls.find(([u]) => String(u).includes(teil))![1] as RequestInit).body as string);

beforeEach(() => {
    fetcher = vi.fn(async (url: string) => {
        if (String(url).endsWith('/vorschlaege')) return json(vorschlaege);
        if (String(url).endsWith('/uebernehmen')) return json({ zugeordnet: 2, abgelehnt: [] });
        return json({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('KiAbgleichDialog', () => {
    it('zeigt je Mail den Vorschlag; Mails mit Treffer sind vorgewählt, ohne Treffer gesperrt', async () => {
        zeige();
        expect(await screen.findByTestId('ki-vorschlaege')).toBeInTheDocument();
        expect(screen.getByText('Geländer Halle')).toBeInTheDocument();
        expect(screen.getByText('Muster Stahl GmbH')).toBeInTheDocument();
        expect(screen.getByText('Keine passende Zuordnung gefunden.')).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: /Angebot Geländer/ })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: /Hallo/ })).toBeDisabled();
        expect(postBody('vorschlaege')).toEqual({ emailIds: [1, 2, 3] });
    });

    it('übernimmt nur das Angekreuzte und meldet den Erfolg', async () => {
        const u = userEvent.setup();
        const fertig = zeige();
        await u.click(await screen.findByRole('checkbox', { name: /Rechnung 4711/ }));
        await u.click(screen.getByRole('button', { name: /Übernehmen \(1 von 2\)/ }));

        await waitFor(() => expect(fertig).toHaveBeenCalled());
        expect(postBody('uebernehmen')).toEqual([{ emailId: 1, art: 'PROJEKT', zielId: 5 }]);
    });

    it('zeigt den Fehler des Servers und übernimmt nichts', async () => {
        fetcher.mockImplementation(async () => json({ message: 'Bitte höchstens 50 E-Mails auf einmal abgleichen.' }, 400));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('höchstens 50');
        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });
});
