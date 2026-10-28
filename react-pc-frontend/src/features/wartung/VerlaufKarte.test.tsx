import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { VerlaufKarte } from './VerlaufKarte';

afterEach(() => { vi.unstubAllGlobals(); });
const antwort = (body: unknown, ok = true) => vi.fn(async () => ({ ok, status: ok ? 200 : 500, json: async () => body }) as Response);

describe('VerlaufKarte', () => {
    it('zeigt wer wann wieder geöffnet hat, mit Grund', async () => {
        const f = antwort([
            { zeit: '2026-10-09T08:00:00', benutzer: 'anna', vonStatus: 'ABGESCHLOSSEN', nachStatus: 'IN_BEARBEITUNG', grund: 'Messwert falsch' },
            { zeit: '2026-10-01T10:00:00', benutzer: 'bernd', vonStatus: 'IN_BEARBEITUNG', nachStatus: 'ABGESCHLOSSEN', grund: null },
        ]);
        vi.stubGlobal('fetch', f);
        render(<VerlaufKarte protokollId={7} stand="x" />);

        expect(await screen.findByText('In Arbeit')).toBeInTheDocument();
        expect(screen.getByText(/anna/)).toBeInTheDocument();
        expect(screen.getByText('„Messwert falsch"')).toBeInTheDocument();
        expect(screen.getByText(/bernd/)).toBeInTheDocument();
        expect(f.mock.calls[0][0]).toBe('/api/wartung/protokolle/7/verlauf');
    });

    it('zeigt ohne Einträge nichts', async () => {
        const f = antwort([]);
        vi.stubGlobal('fetch', f);
        render(<VerlaufKarte protokollId={7} stand="x" />);
        await waitFor(() => expect(f).toHaveBeenCalled());
        expect(screen.queryByTestId('wartung-verlauf')).toBeNull();
    });

    it('sagt es, wenn der Verlauf nicht geladen werden kann', async () => {
        vi.stubGlobal('fetch', antwort({}, false));
        render(<VerlaufKarte protokollId={7} stand="x" />);
        expect(await screen.findByText('Der Verlauf konnte nicht geladen werden.')).toBeInTheDocument();
    });

    it('lädt neu, wenn sich der Stand ändert', async () => {
        const f = antwort([]);
        vi.stubGlobal('fetch', f);
        const { rerender } = render(<VerlaufKarte protokollId={7} stand="a" />);
        await waitFor(() => expect(f).toHaveBeenCalledTimes(1));
        rerender(<VerlaufKarte protokollId={7} stand="b" />);
        await waitFor(() => expect(f).toHaveBeenCalledTimes(2));
    });
});
