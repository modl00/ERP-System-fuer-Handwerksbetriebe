import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import PropertiesSidebar from './PropertiesSidebar';
import type { VorlagenEinstellungen } from './vorlagenEinstellungen';
import type { FormBlock } from '../../types';

const einstellungen = { schriftart: 'HELVETICA', schriftgroesse: 10, falzmarken: true, adresszeileAnzeigen: true, fusszeileTyp: 'FIRMENDATEN' } as unknown as VorlagenEinstellungen;
const block = { id: 'b1', type: 'text', x: 10, y: 20, width: 100, height: 30, page: 1, content: 'Hallo' } as unknown as FormBlock;

const zeige = (teil: Partial<React.ComponentProps<typeof PropertiesSidebar>> = {}) => render(
    <PropertiesSidebar selectedItem={null} selectedDokumenttypen={[]} onUpdateBlock={vi.fn()} onDeleteBlock={vi.fn()} onUpdateDokumenttypen={vi.fn()}
        seitenEinstellungen={einstellungen} onOpenSeitenEinstellungen={vi.fn()} {...teil} />,
);

describe('PropertiesSidebar: Seite', () => {
    it('ohne gewählten Block zeigt die Box die Seiten-Einstellungen kurz und öffnet auf Wunsch den Dialog', async () => {
        const oeffnen = vi.fn();
        const user = userEvent.setup();
        zeige({ onOpenSeitenEinstellungen: oeffnen });

        const seite = screen.getByTestId('eigenschaften-seite');
        expect(within(seite).getByText('Arial / Calibri, 10 pt')).toBeVisible();
        expect(within(seite).getByText('Bank / Steuer / Register')).toBeVisible();
        expect(within(seite).getByText('wird angezeigt')).toBeVisible();
        await user.click(within(seite).getByRole('button', { name: /Seite einstellen/ }));
        expect(oeffnen).toHaveBeenCalledTimes(1);
    });

    it('mit gewähltem Block stehen die Eigenschaften des Blocks da, nicht die der Seite', () => {
        zeige({ selectedItem: block });
        expect(screen.queryByTestId('eigenschaften-seite')).toBeNull();
        expect(screen.getByText('Position & Größe')).toBeVisible();
    });

    it('ohne Dialog-Anbindung oder ohne geladene Einstellungen bleibt die Box wie bisher', () => {
        const { unmount } = zeige({ onOpenSeitenEinstellungen: undefined });
        expect(screen.queryByTestId('eigenschaften-seite')).toBeNull();
        unmount();
        zeige({ seitenEinstellungen: null });
        expect(screen.getByRole('button', { name: /Seite einstellen/ })).toBeVisible();
        expect(screen.queryByText(/Arial/)).toBeNull();
    });
});
