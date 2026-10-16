import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FIRMENLOGO_GEAENDERT, FirmenLogo } from './FirmenLogo';

let icon: HTMLLinkElement;

beforeEach(() => {
    icon = document.createElement('link');
    icon.setAttribute('rel', 'icon');
    icon.setAttribute('href', '/firmenlogo_icon.png');
    document.head.appendChild(icon);
});
afterEach(() => { icon.remove(); });

describe('FirmenLogo', () => {
    it('zeigt zuerst das Firmenlogo aus den Einstellungen und setzt es als Symbol im Browser-Tab', () => {
        render(<FirmenLogo />);
        const bild = screen.getByRole('img', { name: 'Firmenlogo' });
        expect(bild).toHaveAttribute('src', '/api/firma/logo?v=0');
        expect(icon.getAttribute('href')).toBe('/api/firma/logo?v=0');
    });

    it('gibt es kein Firmenlogo (Fehler beim Laden), bleibt es beim mitgelieferten Programmlogo, auch im Tab', () => {
        render(<FirmenLogo />);
        fireEvent.error(screen.getByRole('img', { name: 'Firmenlogo' }));
        expect(screen.getByRole('img', { name: 'Firmenlogo' })).toHaveAttribute('src', '/firmenlogo_icon.png');
        expect(icon.getAttribute('href')).toBe('/firmenlogo_icon.png');
    });

    it('nach dem Hochladen eines neuen Logos lädt es sofort neu, auch wenn vorher keines da war', () => {
        render(<FirmenLogo />);
        fireEvent.error(screen.getByRole('img', { name: 'Firmenlogo' }));
        expect(screen.getByRole('img', { name: 'Firmenlogo' })).toHaveAttribute('src', '/firmenlogo_icon.png');

        act(() => { window.dispatchEvent(new Event(FIRMENLOGO_GEAENDERT)); });

        expect(screen.getByRole('img', { name: 'Firmenlogo' })).toHaveAttribute('src', '/api/firma/logo?v=1');
    });

    it('beim Verlassen kehrt das Symbol im Tab zum ursprünglichen zurück', () => {
        const { unmount } = render(<FirmenLogo />);
        unmount();
        expect(icon.getAttribute('href')).toBe('/firmenlogo_icon.png');
    });
});
