import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeFeaturesZurueck } from '../../features/modulschalter/useFeatures';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

function zeige(pfad: string) {
    stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/features' ? antwort({ wartung: true }) : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <AuthProvider><RibbonNavigation /></AuthProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – Wer darf was?', () => {
    beforeEach(() => { setzeFeaturesZurueck(); setzeTelefonBerechtigungZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('hat einen Punkt „Wer darf was?"; „Dokumentenrechte" ist ein Reiter darin und kein eigener Punkt mehr', async () => {
        zeige('/rechte-uebersicht');

        fireEvent.click(await screen.findByRole('button', { name: 'Vorlagen & Stammdaten' }));
        const punkt = await screen.findByRole('menuitem', { name: /Wer darf was\?/ });
        expect(punkt).toHaveAttribute('href', '/rechte-uebersicht');
        expect(screen.queryByRole('menuitem', { name: /Dokumentenrechte/ })).toBeNull();
    });

    it('auf der Seite der Abteilungsrechte ist „Vorlagen & Stammdaten" der hervorgehobene Hauptpunkt', async () => {
        zeige('/abteilung-berechtigungen');

        const hauptpunkt = await screen.findByRole('button', { name: 'Vorlagen & Stammdaten' });
        await waitFor(() => expect(hauptpunkt.className).toContain('text-rose-700'));
    });
});
