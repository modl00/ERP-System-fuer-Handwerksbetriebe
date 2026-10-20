import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { WasIstNeu } from './WasIstNeu';
import { AKTUELLE_VERSION } from './changelog';

describe('WasIstNeu', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it('zeigt Badge wenn Version noch nicht gelesen wurde', () => {
        render(<WasIstNeu />);
        expect(screen.getByLabelText('Neue Einträge vorhanden')).toBeInTheDocument();
    });

    it('zeigt kein Badge wenn aktuelle Version bereits gelesen', () => {
        localStorage.setItem('erp_neuigkeiten_version', AKTUELLE_VERSION);
        render(<WasIstNeu />);
        expect(screen.queryByLabelText('Neue Einträge vorhanden')).not.toBeInTheDocument();
    });

    it('öffnet das Panel auf Klick und blendet Badge aus', () => {
        render(<WasIstNeu />);
        fireEvent.click(screen.getByRole('button', { name: 'Was ist neu?' }));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.queryByLabelText('Neue Einträge vorhanden')).not.toBeInTheDocument();
        expect(localStorage.getItem('erp_neuigkeiten_version')).toBe(AKTUELLE_VERSION);
    });

    it('schließt das Panel beim Klick auf X', () => {
        render(<WasIstNeu />);
        fireEvent.click(screen.getByRole('button', { name: 'Was ist neu?' }));
        fireEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('localStorage-Fehler werden abgefangen', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
        expect(() => render(<WasIstNeu />)).not.toThrow();
    });
});
