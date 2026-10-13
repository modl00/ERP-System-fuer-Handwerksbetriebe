import { render, screen, fireEvent } from '@testing-library/react';
import { LogoBild } from './LogoBild';

describe('LogoBild', () => {
    it('zeigt das Logo bei gültiger Quelle', () => {
        render(<LogoBild quelle="data:image/png;base64,abc" />);
        expect(screen.getByAltText('Logo')).toBeInTheDocument();
    });

    it('zeigt Platzhalter bei leerem src', () => {
        render(<LogoBild quelle="" />);
        expect(screen.getByRole('img', { name: 'Bild nicht gefunden' })).toBeInTheDocument();
    });

    it('zeigt Platzhalter bei Ladefehler', () => {
        render(<LogoBild quelle="data:image/png;base64,abc" />);
        const img = screen.getByAltText('Logo');
        fireEvent.error(img);
        expect(screen.getByRole('img', { name: 'Bild nicht gefunden' })).toBeInTheDocument();
    });

    it('blockt SVG als Datenquelle', () => {
        render(<LogoBild quelle="data:image/svg+xml;base64,abc" />);
        expect(screen.getByRole('img', { name: 'Bild nicht gefunden' })).toBeInTheDocument();
    });
});
