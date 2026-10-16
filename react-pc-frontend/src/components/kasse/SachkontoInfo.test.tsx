import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SachkontoInfo } from './SachkontoInfo';
import { buildSachkontoOptions } from './belegFormat';
import type { Sachkonto } from '../../types';

const konto = (a: Partial<Sachkonto> = {}): Sachkonto => ({
    id: 1, nummer: '3100', bezeichnung: 'Fremdleistungen / Subunternehmer', kontoTyp: 'AUFWAND',
    beschreibung: 'Bezahlte Rechnungen von Sub-Handwerkern und Dienstleistern', aktiv: true, sortierung: 11, ...a,
});

describe('SachkontoInfo', () => {
    it('zeigt Art und Beschreibung des gewählten Kontos', () => {
        render(<SachkontoInfo konto={konto()} anzahl={5} />);
        const text = screen.getByTestId('sachkonto-info');
        expect(text).toHaveTextContent('Aufwand');
        expect(text).toHaveTextContent('Bezahlte Rechnungen von Sub-Handwerkern und Dienstleistern');
    });

    it('ohne Beschreibung steht ein ehrlicher Satz statt einer leeren Zeile', () => {
        render(<SachkontoInfo konto={konto({ beschreibung: '  ' })} anzahl={5} />);
        expect(screen.getByTestId('sachkonto-info')).toHaveTextContent('keine Beschreibung hinterlegt');
    });

    it('ohne Auswahl erklärt der Text, wo die Erklärung steht', () => {
        render(<SachkontoInfo konto={null} anzahl={5} />);
        expect(screen.getByTestId('sachkonto-info')).toHaveTextContent('Erklärung unter jedem Namen');
    });

    it('ohne jedes Konto sagt der Text das und verweist an den Administrator', () => {
        render(<SachkontoInfo konto={null} anzahl={0} />);
        expect(screen.getByRole('status')).toHaveTextContent('noch keine Konten angelegt');
    });
});

describe('buildSachkontoOptions', () => {
    it('gibt die Beschreibung als Erklärung unter dem Namen mit, leere Beschreibungen weg', () => {
        const optionen = buildSachkontoOptions([konto(), konto({ id: 2, bezeichnung: 'Porto', nummer: '4920', beschreibung: '  ', sortierung: 12 })]);
        expect(optionen[1]).toMatchObject({ value: '1', label: '3100 Fremdleistungen / Subunternehmer', beschreibung: 'Bezahlte Rechnungen von Sub-Handwerkern und Dienstleistern' });
        expect(optionen[2].beschreibung).toBeUndefined();
    });
});
