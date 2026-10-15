import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { VerknuepfungsLinks } from './VerknuepfungsLinks';

describe('VerknuepfungsLinks', () => {
    it('verlinkt Projekt und Kunde direkt', () => {
        render(<MemoryRouter><VerknuepfungsLinks projektId={7} projektName="Geländer" kundeId={12} kundeName="Muster GmbH" /></MemoryRouter>);

        expect(screen.getByRole('link', { name: /Projekt: Geländer/ })).toHaveAttribute('href', '/projekte?projektId=7');
        expect(screen.getByRole('link', { name: /Kunde: Muster GmbH/ })).toHaveAttribute('href', '/kunden?kundeId=12');
    });

    it('zeigt ohne Namen „Projekt öffnen“, funktioniert auch ohne Router und fehlt ohne Angaben', () => {
        const { container, rerender } = render(<VerknuepfungsLinks projektId={7} />);
        expect(screen.getByRole('link', { name: /Projekt öffnen/ })).toHaveAttribute('href', '/projekte?projektId=7');
        expect(screen.queryByRole('link', { name: /Kunde/ })).not.toBeInTheDocument();
        rerender(<VerknuepfungsLinks />);
        expect(container).toBeEmptyDOMElement();
    });
});
