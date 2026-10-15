import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { RechteReiter } from './RechteReiter';

const zeige = (pfad: string) => render(<MemoryRouter initialEntries={[pfad]}><RechteReiter /></MemoryRouter>);

describe('RechteReiter', () => {
    it('führt zu beiden Bereichen und markiert den offenen', () => {
        zeige('/rechte-uebersicht');

        expect(screen.getByRole('link', { name: 'Wer darf was?' })).toHaveAttribute('aria-current', 'page');
        const abteilungen = screen.getByRole('link', { name: 'Rechte der Abteilungen' });
        expect(abteilungen).toHaveAttribute('href', '/abteilung-berechtigungen');
        expect(abteilungen).not.toHaveAttribute('aria-current');
    });

    it('markiert auf der Seite der Abteilungen deren Reiter', () => {
        zeige('/abteilung-berechtigungen');

        expect(screen.getByRole('link', { name: 'Rechte der Abteilungen' })).toHaveAttribute('aria-current', 'page');
        expect(screen.getByRole('link', { name: 'Wer darf was?' })).not.toHaveAttribute('aria-current');
    });
});
