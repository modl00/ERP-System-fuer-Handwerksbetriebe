import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlternativeKalkulation } from './AlternativeKalkulation';
import { ServiceBlock } from './ServiceBlock';
import { ToastProvider } from '../ui/toast';
import type { DocBlock } from './types';

vi.mock('../../features/leistungen/LeistungKalkulation', () => ({
    LeistungKalkulation: ({ leistungId, onPreisGeaendert }: { leistungId: number | string; onPreisGeaendert: (p: number, k: boolean) => void }) => (
        <div data-testid="kalkulation">
            Kalkulation für Leistung {String(leistungId)}
            <button onClick={() => onPreisGeaendert(950, true)}>Preis speichern (kalkuliert)</button>
        </div>
    ),
}));

/** Nur Dummy-Daten. */
const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, json: async () => daten, text: async () => JSON.stringify(daten) });
const block = (teil: Partial<DocBlock> = {}): DocBlock => ({ id: 'b1', type: 'SERVICE', title: 'Geländer, Stahl', quantity: 12, unit: 'lfm', price: 180, leistungId: 5, ...teil });

let fetcher: ReturnType<typeof vi.fn>;
let ergebnis: () => ReturnType<typeof antwort>;
beforeEach(() => {
    ergebnis = () => antwort({ id: 91 });
    fetcher = vi.fn(async () => ergebnis());
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

/** Wie der Editor: Die Änderungen der Position kommen als neuer Block zurück. */
function Halter({ start, onUpdate }: { start: DocBlock; onUpdate: (u: Partial<DocBlock>) => void }) {
    const [b, setB] = useState(start);
    return <AlternativeKalkulation block={b} onUpdate={u => { onUpdate(u); setB(alt => ({ ...alt, ...u })); }} />;
}
const zeige = (b: DocBlock = block(), onUpdate = vi.fn()) => ({ onUpdate, ...render(<ToastProvider><Halter start={b} onUpdate={onUpdate} /></ToastProvider>) });

describe('Alternative Kalkulation an einer Position', () => {
    it('bei einer Katalogleistung entsteht eine Kopie, die Position verweist darauf, und die Kalkulation öffnet sich', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige();
        await user.click(screen.getByRole('button', { name: /Alternative kalkulieren/ }));

        await waitFor(() => expect(onUpdate).toHaveBeenCalledWith({ leistungId: 91, temporaereLeistung: true, alternativeVon: { leistungId: 5, preis: 180 } }));
        expect(fetcher).toHaveBeenCalledWith('/api/leistungen/5/alternative', { method: 'POST' });
        expect(await screen.findByTestId('kalkulation')).toHaveTextContent('Kalkulation für Leistung 91');
    });

    it('bei einem Artikel entsteht eine Leistung mit dem Artikel als Material', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige(block({ leistungId: undefined, artikelId: 33, title: 'Quadratrohr 40x3', unit: 'lfm', price: 12.5 }));
        await user.click(screen.getByRole('button', { name: /Alternative kalkulieren/ }));

        await waitFor(() => expect(onUpdate).toHaveBeenCalledWith({ leistungId: 91, temporaereLeistung: true, alternativeVon: { leistungId: null, preis: 12.5 } }));
        const [url, init] = fetcher.mock.calls[0] as [string, RequestInit];
        expect(url).toBe('/api/leistungen/temporaer/aus-artikel');
        expect(JSON.parse(String(init.body))).toEqual({ artikelId: 33, name: 'Quadratrohr 40x3', unit: 'LAUFENDE_METER', price: 12.5 });
    });

    it('der Preis folgt der gespeicherten Kalkulation', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige(block({ leistungId: 91, temporaereLeistung: true, alternativeVon: { leistungId: 5, preis: 180 } }));
        await user.click(screen.getByRole('button', { name: /Alternative bearbeiten/ }));
        await user.click(await screen.findByRole('button', { name: 'Preis speichern (kalkuliert)' }));

        expect(onUpdate).toHaveBeenCalledWith({ price: 950 });
    });

    it('„Zurück zum Original" setzt Leistung und Preis wieder ein', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige(block({ leistungId: 91, temporaereLeistung: true, price: 950, alternativeVon: { leistungId: 5, preis: 180 } }));
        expect(screen.getByTestId('alternative-aktiv')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /Zurück zum Original/ }));

        expect(onUpdate).toHaveBeenCalledWith({ leistungId: 5, temporaereLeistung: false, price: 180, alternativeVon: undefined });
    });

    it('bei einem Artikel verweist das Original danach wieder auf keine Leistung', async () => {
        const user = userEvent.setup();
        const { onUpdate } = zeige(block({ leistungId: 91, artikelId: 33, temporaereLeistung: true, price: 40, alternativeVon: { leistungId: null, preis: 12.5 } }));
        await user.click(screen.getByRole('button', { name: /Zurück zum Original/ }));

        expect(onUpdate).toHaveBeenCalledWith({ leistungId: undefined, temporaereLeistung: false, price: 12.5, alternativeVon: undefined });
    });

    it('ein Fehler vom Server steht im Klartext da und ändert die Position nicht', async () => {
        ergebnis = () => antwort({ message: 'Die Leistung wurde nicht gefunden.' }, false, 404);
        const user = userEvent.setup();
        const { onUpdate } = zeige();
        await user.click(screen.getByRole('button', { name: /Alternative kalkulieren/ }));

        expect(await screen.findByText('Die Leistung wurde nicht gefunden.')).toBeInTheDocument();
        expect(onUpdate).not.toHaveBeenCalled();
    });
});

describe('im Angebot (ServiceBlock)', () => {
    const props = (b: DocBlock, gesperrt = false) => ({
        block: b, positionNumber: '1', isLocked: gesperrt, isActive: false, editorRefs: { current: {} } as never, onEditorReady: vi.fn(), onUpdate: vi.fn(), onRemove: vi.fn(),
        onModusWechsel: vi.fn(), onAlternativOeffnen: vi.fn(), onFocus: vi.fn(), onEditorFocus: vi.fn(),
    }) as React.ComponentProps<typeof ServiceBlock>;

    it('Katalogleistung und Artikel bieten die alternative Kalkulation an, eine freie Position nicht, ein gesperrtes Dokument auch nicht', () => {
        const { unmount } = render(<ToastProvider><ServiceBlock {...props(block())} defaultCollapsed={false} /></ToastProvider>);
        expect(screen.queryByTestId('alternative-kalkulation')).toBeInTheDocument();
        unmount();
        const zwei = render(<ToastProvider><ServiceBlock {...props(block({ leistungId: undefined, artikelId: 3 }))} defaultCollapsed={false} /></ToastProvider>);
        expect(screen.queryByTestId('alternative-kalkulation')).toBeInTheDocument();
        zwei.unmount();
        const drei = render(<ToastProvider><ServiceBlock {...props(block({ leistungId: undefined, artikelId: undefined, eigenePosition: true }))} defaultCollapsed={false} /></ToastProvider>);
        expect(screen.queryByTestId('alternative-kalkulation')).not.toBeInTheDocument();
        drei.unmount();
        render(<ToastProvider><ServiceBlock {...props(block(), true)} defaultCollapsed={false} /></ToastProvider>);
        expect(screen.queryByTestId('alternative-kalkulation')).not.toBeInTheDocument();
    });
});
