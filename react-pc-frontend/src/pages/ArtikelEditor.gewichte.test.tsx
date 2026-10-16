import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ArtikelEditor from './ArtikelEditor';

const auth = vi.hoisted(() => ({ wert: null as { isAdmin: boolean } | null }));
vi.mock('../auth/AuthContext', () => ({ useOptionalAuth: () => auth.wert }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ actions, children }: { actions: React.ReactNode; children: React.ReactNode }) => <div><div>{actions}</div>{children}</div> }));
vi.mock('../components/artikel/ArtikelSuche', () => ({ ArtikelSuche: () => <div /> }));
vi.mock('../components/artikel/GewichteErgaenzenDialog', () => ({ GewichteErgaenzenDialog: ({ offen }: { offen: boolean }) => (offen ? <div role="dialog">Gewichte-Fenster</div> : null) }));
vi.mock('./Aufschlaege', () => ({ default: () => <div /> }));
vi.mock('./WerkstoffEditor', () => ({ default: () => <div /> }));
vi.mock('../components/CategoryTreeModal', () => ({ CategoryTreeModal: () => <div /> }));
vi.mock('../components/CreateArticleModal', () => ({ CreateArticleModal: () => <div /> }));

beforeEach(() => { auth.wert = null; });

describe('ArtikelEditor: Gewichte ergänzen', () => {
    it('nur Administratoren sehen den Knopf', () => {
        auth.wert = { isAdmin: false };
        const { unmount } = render(<MemoryRouter><ArtikelEditor /></MemoryRouter>);
        expect(screen.queryByRole('button', { name: /Gewichte ergänzen/ })).toBeNull();
        unmount();

        auth.wert = { isAdmin: true };
        render(<MemoryRouter><ArtikelEditor /></MemoryRouter>);
        expect(screen.getByRole('button', { name: /Gewichte ergänzen/ })).toBeInTheDocument();
    });

    it('ohne Anmeldung (zum Beispiel in einer Vorschau) bleibt der Knopf weg', () => {
        render(<MemoryRouter><ArtikelEditor /></MemoryRouter>);
        expect(screen.queryByRole('button', { name: /Gewichte ergänzen/ })).toBeNull();
    });
});
