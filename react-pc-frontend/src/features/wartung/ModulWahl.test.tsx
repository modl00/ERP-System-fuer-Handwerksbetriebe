import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ModulWahl } from './ModulWahl';
import type { ModulAuswahl, WartungModul } from './types';

const modul = (id: number, name: string, kategorie: 'BASIS' | 'ZUSATZ', extra: Partial<WartungModul> = {}): WartungModul => ({
    id, modulKey: `m_${id}`, name, beschreibung: null, icon: 'DoorClosed', kategorie, systemModul: true,
    standardIntervallMonate: 12, andockbarAnBasisModulIds: [], untertypen: [], anzahlPruefpunkte: 10, ...extra,
});

const KATALOG: WartungModul[] = [
    modul(1, 'Brandschutztür', 'BASIS', { untertypen: [{ schluessel: 'einflg', label: '1-flügelig' }, { schluessel: 'zweiflg', label: '2-flügelig' }] }),
    modul(2, 'Rauchschutztür', 'BASIS'),
    modul(3, 'Feststellanlage', 'ZUSATZ', { andockbarAnBasisModulIds: [1, 2] }),
    modul(4, 'Bodendichtung', 'ZUSATZ', { andockbarAnBasisModulIds: [2] }),
];

function Wrapper({ start = [], disabled = false, beiAenderung }: { start?: ModulAuswahl[]; disabled?: boolean; beiAenderung?: (a: ModulAuswahl[]) => void }) {
    const [auswahl, setAuswahl] = useState<ModulAuswahl[]>(start);
    return <ModulWahl module={KATALOG} auswahl={auswahl} disabled={disabled} onChange={(a) => { setAuswahl(a); beiAenderung?.(a); }} />;
}

describe('ModulWahl', () => {
    it('zeigt zuerst nur die Basis-Elemente, noch keine Zusatzmodule', () => {
        render(<Wrapper />);
        expect(screen.getByRole('radio', { name: /Brandschutztür/ })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('radio', { name: /Rauchschutztür/ })).toHaveAttribute('aria-checked', 'false');
        expect(screen.queryByRole('checkbox')).toBeNull();
    });

    it('wählt ein Basis-Element und zeigt danach die passenden Zusatzmodule', async () => {
        render(<Wrapper />);
        await userEvent.click(screen.getByRole('radio', { name: /Brandschutztür/ }));
        expect(screen.getByRole('radio', { name: /Brandschutztür/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('checkbox', { name: /Feststellanlage/ })).toBeVisible();
        expect(screen.queryByRole('checkbox', { name: /Bodendichtung/ })).toBeNull();
    });

    it('wählt immer nur ein Basis-Element: ein zweiter Klick ersetzt das erste', async () => {
        render(<Wrapper />);
        await userEvent.click(screen.getByRole('radio', { name: /Brandschutztür/ }));
        await userEvent.click(screen.getByRole('radio', { name: /Rauchschutztür/ }));
        expect(screen.getByRole('radio', { name: /Brandschutztür/ })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('radio', { name: /Rauchschutztür/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('checkbox', { name: /Bodendichtung/ })).toBeVisible();
    });

    it('entfernt ein Zusatzmodul, das zum neuen Basis-Element nicht passt', async () => {
        const gemeldet: ModulAuswahl[][] = [];
        render(<Wrapper start={[{ modulId: 2, untertypSchluessel: null }, { modulId: 4, untertypSchluessel: null }]} beiAenderung={(a) => gemeldet.push(a)} />);
        await userEvent.click(screen.getByRole('radio', { name: /Brandschutztür/ }));
        expect(gemeldet.at(-1)!.map((a) => a.modulId)).toEqual([1]);
    });

    it('schaltet ein Zusatzmodul an und aus', async () => {
        render(<Wrapper start={[{ modulId: 1, untertypSchluessel: 'einflg' }]} />);
        const feststell = screen.getByRole('checkbox', { name: /Feststellanlage/ });
        expect(feststell).toHaveAttribute('aria-checked', 'false');
        await userEvent.click(feststell);
        expect(screen.getByRole('checkbox', { name: /Feststellanlage/ })).toHaveAttribute('aria-checked', 'true');
        await userEvent.click(screen.getByRole('checkbox', { name: /Feststellanlage/ }));
        expect(screen.getByRole('checkbox', { name: /Feststellanlage/ })).toHaveAttribute('aria-checked', 'false');
    });

    it('bietet die Bauart an, wenn das gewählte Modul welche kennt, und weist auf die fehlende Wahl hin', async () => {
        render(<Wrapper start={[{ modulId: 1, untertypSchluessel: null }]} />);
        expect(screen.getByRole('combobox', { name: 'Bauart für Brandschutztür' })).toBeVisible();
        expect(screen.getByText('Bitte die Bauart wählen.')).toBeVisible();
    });

    it('bietet keine Bauart an, wenn das Modul keine kennt', () => {
        render(<Wrapper start={[{ modulId: 2, untertypSchluessel: null }]} />);
        expect(screen.queryByRole('combobox')).toBeNull();
    });

    it('setzt die Bauart über die Auswahl', async () => {
        const gemeldet: ModulAuswahl[][] = [];
        render(<Wrapper start={[{ modulId: 1, untertypSchluessel: null }]} beiAenderung={(a) => gemeldet.push(a)} />);
        await userEvent.click(screen.getByRole('combobox', { name: 'Bauart für Brandschutztür' }));
        await userEvent.click(await screen.findByRole('option', { name: '2-flügelig' }));
        expect(gemeldet.at(-1)).toEqual([{ modulId: 1, untertypSchluessel: 'zweiflg' }]);
    });

    it('lässt im gesperrten Zustand nichts ändern', async () => {
        const gemeldet: ModulAuswahl[][] = [];
        render(<Wrapper disabled beiAenderung={(a) => gemeldet.push(a)} />);
        expect(screen.getByRole('radio', { name: /Brandschutztür/ })).toBeDisabled();
        await userEvent.click(screen.getByRole('radio', { name: /Brandschutztür/ }));
        expect(gemeldet).toHaveLength(0);
    });
});
