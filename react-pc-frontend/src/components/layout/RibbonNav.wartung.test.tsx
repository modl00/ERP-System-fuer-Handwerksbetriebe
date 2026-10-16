import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeFeaturesZurueck } from '../../features/modulschalter/useFeatures';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

function stubbe(wartung: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: [], admin: false, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/features' ? antwort({ wartung }) : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
}

function zeige() {
    return render(
        <MemoryRouter initialEntries={['/projekte']}>
            <AuthProvider><RibbonNavigation /></AuthProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – Wartung', () => {
    beforeEach(() => { setzeFeaturesZurueck(); setzeTelefonBerechtigungZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt "Wartungen", solange das Modul eingeschaltet ist', async () => {
        stubbe(true);
        zeige();
        fireEvent.click(screen.getByRole('button', { name: 'Projektmanagement' }));
        expect(await screen.findByRole('menuitem', { name: /Wartungen/ })).toHaveAttribute('href', '/wartung');
    });

    it('blendet "Wartungen" aus, wenn das Modul ausgeschaltet ist, und lässt die übrigen Punkte stehen', async () => {
        const fetchMock = stubbe(false);
        zeige();
        await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/features'))).toBe(true));
        fireEvent.click(screen.getByRole('button', { name: 'Projektmanagement' }));
        expect(await screen.findByRole('menuitem', { name: /Projekte/ })).toBeInTheDocument();
        expect(screen.getByRole('menuitem', { name: /Anfragen/ })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: /Wartungen/ })).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Kommunikation' }));
        expect(screen.getByRole('menuitem', { name: /E-Mail Center/ })).toBeInTheDocument();
    });

    it('zeigt den Punkt nicht, solange noch geprüft wird: Er blitzt nicht auf und verschwindet wieder', () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => (String(url).includes('/api/features') ? new Promise(() => undefined)
            : Promise.resolve(new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })))));
        zeige();
        fireEvent.click(screen.getByRole('button', { name: 'Projektmanagement' }));
        expect(screen.queryByRole('menuitem', { name: /Wartungen/ })).toBeNull();
    });
});
