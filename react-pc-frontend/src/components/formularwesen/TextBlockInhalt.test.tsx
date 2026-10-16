import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TextBlockInhalt } from './TextBlockInhalt';

const stil = { fontSize: 9, fontWeight: '400', color: '#111827', textAlign: 'left' } as const;

describe('TextBlockInhalt', () => {
    it('zeigt normalen Text unverändert mit Zeilenumbrüchen', () => {
        const { container } = render(<TextBlockInhalt text={'Zeile 1\nZeile 2'} styles={stil} />);
        expect(container.textContent).toBe('Zeile 1\nZeile 2');
    });

    it('setzt im Infoblock die Bezeichnung links und den Wert rechts und lässt Zeilen ohne Wert weg', () => {
        render(<TextBlockInhalt text={'Datum\t01.10.2026\nMobil\t\nTelefon\t0931 1'} styles={stil} />);
        expect(screen.getByText('Datum')).toBeVisible();
        expect(screen.getByText('01.10.2026')).toBeVisible();
        expect(screen.getByText('0931 1')).toBeVisible();
        expect(screen.queryByText('Mobil')).toBeNull();
    });
});
