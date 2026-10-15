import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { WiederOeffnenDialog } from './WiederOeffnenDialog';

describe('WiederOeffnenDialog', () => {
    it('gibt den Grund beim Bestätigen weiter, auch leer', async () => {
        const u = userEvent.setup();
        const ok = vi.fn();
        render(<WiederOeffnenDialog open laeuft={false} onOpenChange={vi.fn()} onBestaetigen={ok} />);

        await u.type(screen.getByLabelText(/Was soll geändert werden/), 'Messwert falsch');
        await u.click(screen.getByRole('button', { name: /Wieder öffnen/ }));
        expect(ok).toHaveBeenCalledWith('Messwert falsch');
    });

    it('erlaubt das Bestätigen ohne Grund', async () => {
        const u = userEvent.setup();
        const ok = vi.fn();
        render(<WiederOeffnenDialog open laeuft={false} onOpenChange={vi.fn()} onBestaetigen={ok} />);
        await u.click(screen.getByRole('button', { name: /Wieder öffnen/ }));
        expect(ok).toHaveBeenCalledWith('');
    });

    it('sperrt die Knöpfe, während der Statuswechsel läuft', () => {
        render(<WiederOeffnenDialog open laeuft onOpenChange={vi.fn()} onBestaetigen={vi.fn()} />);
        expect(screen.getByRole('button', { name: /Wieder öffnen/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
    });

    it('Abbrechen schließt ohne zu bestätigen', async () => {
        const u = userEvent.setup();
        const zu = vi.fn();
        const ok = vi.fn();
        render(<WiederOeffnenDialog open laeuft={false} onOpenChange={zu} onBestaetigen={ok} />);
        await u.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(zu).toHaveBeenCalledWith(false);
        expect(ok).not.toHaveBeenCalled();
    });
});
