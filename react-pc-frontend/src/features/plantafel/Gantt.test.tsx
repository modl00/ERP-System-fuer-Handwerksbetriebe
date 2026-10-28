import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Gantt } from './Gantt';
import type { Auftrag, Vorgang } from './plantafelApi';

/** Nur Dummy-Daten. 5. Oktober 2026 ist ein Montag. */
const HEUTE = new Date(2026, 9, 5);

function vorgang(teil: Partial<Vorgang> & { id: number; bezeichnung: string; start: string; ende: string }): Vorgang {
    return { typId: 1, typName: 'Planung', farbe: '#6366f1', dauerTage: 2, dauerHerkunft: 'GESCHAETZT', festerStart: null, konflikt: false, status: 'GEPLANT', notiz: null, vorgaenger: [], ...teil };
}

const auftrag = (teil: Partial<Auftrag> = {}): Auftrag => ({
    projektId: 7, bezeichnung: 'Geländer Musterstraße', auftragsnummer: 'A-1', startDatum: null, zielDatum: '2026-10-16', liefertermin: '2026-10-15', ampel: 'KNAPP',
    vorgaenge: [
        vorgang({ id: 1, bezeichnung: 'Planung', start: '2026-10-05', ende: '2026-10-06' }),
        vorgang({ id: 2, bezeichnung: 'Fertigung', typName: 'Fertigung', farbe: '#be123c', dauerTage: 5, dauerHerkunft: 'VON_HAND', start: '2026-10-07', ende: '2026-10-13', vorgaenger: [{ vorgaengerId: 1, versatzTage: 0 }] }),
    ],
    ...teil,
});

function zeige(liste: Auftrag[], rueckrufe: Partial<Parameters<typeof Gantt>[0]> = {}) {
    return render(<Gantt auftraege={liste} heute={HEUTE} onVorgang={vi.fn()} onNeuerVorgang={vi.fn()} onTermine={vi.fn()} {...rueckrufe} />);
}

describe('Gantt', () => {
    it('zeigt Auftrag mit Ampel und Lieferzeit und die Vorgänge als Balken mit Beschreibung', () => {
        zeige([auftrag()]);

        const kopf = within(screen.getByTestId('auftrag-7'));
        expect(kopf.getByText('Geländer Musterstraße')).toBeInTheDocument();
        expect(kopf.getByText('Knapp')).toBeInTheDocument();
        expect(kopf.getByText('Lieferzeit 15.10.26')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^Planung, 05\.10\.2026 bis 06\.10\.2026, 2 Arbeitstage, Dauer geschätzt$/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^Fertigung, 07\.10\.2026 bis 13\.10\.2026, 5 Arbeitstage$/ })).toBeInTheDocument();
    });

    it('nennt bei Verspätung die Arbeitstage hinter der Ampel, ein Tag in der Einzahl', () => {
        zeige([auftrag({ ampel: 'VERSPAETET', verspaetungTage: 3 }), auftrag({ projektId: 8, ampel: 'VERSPAETET', verspaetungTage: 1 }), auftrag({ projektId: 9, ampel: 'OK' })]);

        expect(within(screen.getByTestId('auftrag-7')).getByText('Verspätet +3 Tage')).toBeInTheDocument();
        expect(within(screen.getByTestId('auftrag-8')).getByText('Verspätet +1 Tag')).toBeInTheDocument();
        expect(within(screen.getByTestId('auftrag-9')).getByText('Im Plan')).toBeInTheDocument();
    });

    it('zeichnet für jede Abhängigkeit einen Pfeil und färbt den bei Konflikt rot', () => {
        const mitKonflikt = auftrag();
        mitKonflikt.vorgaenge[1] = { ...mitKonflikt.vorgaenge[1], festerStart: '2026-10-06', konflikt: true };
        const { container } = zeige([mitKonflikt]);

        const pfeile = container.querySelectorAll('svg path[marker-end]');
        expect(pfeile).toHaveLength(1);
        expect(pfeile[0]).toHaveAttribute('stroke', '#dc2626');
        expect(screen.getByRole('button', { name: /Konflikt: Die Vorgänger sind erst nach dem festen Start fertig/ })).toBeInTheDocument();
    });

    it('Klicks auf Balken, Namen, „Vorgang hinzufügen" und „Termine" melden den richtigen Auftrag und Vorgang', () => {
        const onVorgang = vi.fn();
        const onNeu = vi.fn();
        const onTermine = vi.fn();
        const a = auftrag();
        zeige([a], { onVorgang, onNeuerVorgang: onNeu, onTermine });

        fireEvent.click(screen.getByRole('button', { name: /^Fertigung, 07\.10/ }));
        expect(onVorgang).toHaveBeenLastCalledWith(a, a.vorgaenge[1]);
        fireEvent.click(screen.getByRole('button', { name: /Vorgang zu Geländer Musterstraße hinzufügen/ }));
        expect(onNeu).toHaveBeenCalledWith(a);
        fireEvent.click(screen.getByRole('button', { name: /Termine von Geländer Musterstraße ändern/ }));
        expect(onTermine).toHaveBeenCalledWith(a);
    });

    it('ein fertiger Vorgang und ein Meilenstein werden beschrieben, ein Vorgang ohne Termine bekommt keinen Balken', () => {
        const a = auftrag({
            vorgaenge: [
                vorgang({ id: 1, bezeichnung: 'Abnahme', start: '2026-10-05', ende: '2026-10-05', dauerTage: 0, dauerHerkunft: 'VON_HAND', status: 'FERTIG' }),
                vorgang({ id: 2, bezeichnung: 'Ohne Termine', start: null as unknown as string, ende: null as unknown as string }),
            ],
        });
        zeige([a]);

        expect(screen.getByRole('button', { name: /Abnahme, 05\.10\.2026 bis 05\.10\.2026, Meilenstein, fertig/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Ohne Termine,/ })).not.toBeInTheDocument();
    });

    it('ein Balken lässt sich auf einen anderen Starttag ziehen; ein Klick ohne Bewegung öffnet ihn, ohne Rückruf ist nichts ziehbar', () => {
        const onVerschieben = vi.fn();
        const onVorgang = vi.fn();
        const a = auftrag();
        zeige([a], { onVerschieben, onVorgang });
        const balken = screen.getByRole('button', { name: /^Fertigung,/ });

        // 2 Tage à 28 Pixel nach rechts: Mittwoch 7.10. → Freitag 9.10.
        fireEvent.pointerDown(balken, { pointerId: 1, button: 0, clientX: 100, clientY: 50 });
        fireEvent.pointerMove(balken, { pointerId: 1, clientX: 156, clientY: 50 });
        fireEvent.pointerUp(balken, { pointerId: 1, clientX: 156, clientY: 50 });
        fireEvent.click(balken);
        expect(onVerschieben).toHaveBeenCalledWith(a, a.vorgaenge[1], '2026-10-09');
        expect(onVorgang).not.toHaveBeenCalled();

        fireEvent.pointerDown(balken, { pointerId: 1, button: 0, clientX: 100, clientY: 50 });
        fireEvent.pointerUp(balken, { pointerId: 1, clientX: 100, clientY: 50 });
        fireEvent.click(balken);
        expect(onVorgang).toHaveBeenCalledWith(a, a.vorgaenge[1]);
        expect(onVerschieben).toHaveBeenCalledTimes(1);
    });

    it('ohne Rückruf zum Verschieben passiert beim Ziehen nichts', () => {
        const onVorgang = vi.fn();
        zeige([auftrag()], { onVorgang });
        const balken = screen.getByRole('button', { name: /^Fertigung,/ });

        fireEvent.pointerDown(balken, { pointerId: 1, button: 0, clientX: 100, clientY: 50 });
        fireEvent.pointerMove(balken, { pointerId: 1, clientX: 156, clientY: 50 });
        fireEvent.pointerUp(balken, { pointerId: 1, clientX: 156, clientY: 50 });
        fireEvent.click(balken);

        expect(onVorgang).toHaveBeenCalledTimes(1);
    });

    it('ohne Aufträge wird nichts gezeichnet', () => {
        const { container } = zeige([]);
        expect(container).toBeEmptyDOMElement();
    });
});
