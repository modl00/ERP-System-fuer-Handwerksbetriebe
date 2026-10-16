import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssignModal } from './EmailAssignmentDialog';
import { ToastProvider } from '../../components/ui/toast';

// Die großen Anlege-Fenster sind hier Attrappen: Es geht um das Zusammenspiel mit dem Zuordnen-Fenster.
vi.mock('../../components/ProjektErstellenModal', () => ({
    ProjektErstellenModal: ({ onClose, onSuccess }: { onClose: () => void; onSuccess: (id: number) => void }) => (
        <div role="dialog" aria-label="Projekt-Fenster"><button onClick={() => onSuccess(42)}>Projekt speichern</button><button onClick={onClose}>Projekt abbrechen</button></div>
    ),
}));
vi.mock('../../pages/AnfrageEditor', () => ({
    AnfrageErstellenModal: ({ onClose, onSuccess }: { onClose: () => void; onSuccess: (id: number) => void }) => (
        <div role="dialog" aria-label="Anfrage-Fenster"><button onClick={() => onSuccess(7)}>Anfrage speichern</button><button onClick={onClose}>Anfrage abbrechen</button></div>
    ),
}));

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ projekte: [], anfragen: [] }) }) as Response));
});
afterEach(() => { vi.unstubAllGlobals(); });

function zeige() {
    const onAssign = vi.fn(async () => undefined);
    const onClose = vi.fn();
    render(<ToastProvider><AssignModal isOpen onClose={onClose} onAssign={onAssign} emailSubject="Anfrage Treppe" emailId={1} /></ToastProvider>);
    return { onAssign, onClose };
}

describe('AssignModal: Neu anlegen und verknüpfen', () => {
    it('Neues Projekt: das Zuordnen-Fenster tritt zurück, nach dem Speichern ist die Mail mit dem neuen Projekt verknüpft', async () => {
        const u = userEvent.setup();
        const { onAssign, onClose } = zeige();

        await u.click(screen.getByTestId('neu-anlegen'));
        expect(screen.getByRole('dialog', { name: 'Projekt-Fenster' })).toBeInTheDocument();
        expect(screen.queryByText('E-Mail zuordnen')).toBeNull();

        await u.click(screen.getByRole('button', { name: 'Projekt speichern' }));
        await waitFor(() => expect(onAssign).toHaveBeenCalledWith('projekt', 42));
        expect(onClose).toHaveBeenCalled();
    });

    it('Neue Anfrage: nach der Wahl „Anfrage“ legt der Knopf eine Anfrage an und verknüpft sie', async () => {
        const u = userEvent.setup();
        const { onAssign } = zeige();

        await u.click(screen.getByRole('button', { name: /^Anfrage/ }));
        expect(screen.getByTestId('neu-anlegen')).toHaveTextContent('Neue Anfrage anlegen');
        await u.click(screen.getByTestId('neu-anlegen'));
        await u.click(screen.getByRole('button', { name: 'Anfrage speichern' }));

        await waitFor(() => expect(onAssign).toHaveBeenCalledWith('anfrage', 7));
    });

    it('Abbrechen im Anlege-Fenster bringt zum Zuordnen zurück, ohne etwas zu verknüpfen', async () => {
        const u = userEvent.setup();
        const { onAssign, onClose } = zeige();

        await u.click(screen.getByTestId('neu-anlegen'));
        await u.click(screen.getByRole('button', { name: 'Projekt abbrechen' }));

        expect(screen.getByText('E-Mail zuordnen')).toBeInTheDocument();
        expect(onAssign).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });
});
