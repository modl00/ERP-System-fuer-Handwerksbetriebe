import { render, screen, fireEvent } from '@testing-library/react';
import { BriefpapierPdf } from './BriefpapierPdf';

describe('BriefpapierPdf', () => {
    it('zeigt das Briefpapier-Bild mit korrekter URL', () => {
        render(<BriefpapierPdf version={3} />);
        const img = screen.getByAltText('Firmenbriefpapier');
        expect(img).toHaveAttribute('src', '/api/firma/briefpapier?v=3');
    });

    it('verwendet version=0 als Standardwert', () => {
        render(<BriefpapierPdf />);
        const img = screen.getByAltText('Firmenbriefpapier');
        expect(img).toHaveAttribute('src', '/api/firma/briefpapier?v=0');
    });

    it('zeigt Platzhalter bei Ladefehler', () => {
        render(<BriefpapierPdf />);
        const img = screen.getByAltText('Firmenbriefpapier');
        fireEvent.error(img);
        expect(screen.getByRole('img', { name: 'Briefpapier nicht verfügbar' })).toBeInTheDocument();
        expect(screen.queryByAltText('Firmenbriefpapier')).not.toBeInTheDocument();
    });

    it('wendet zusätzliche className an', () => {
        render(<BriefpapierPdf className="meine-klasse" />);
        const img = screen.getByAltText('Firmenbriefpapier');
        expect(img).toHaveClass('meine-klasse');
    });
});
