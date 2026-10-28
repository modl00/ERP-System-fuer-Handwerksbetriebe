import { SACHKONTO_TYP_LABEL } from './belegFormat';
import type { Sachkonto } from '../../types';

interface Props {
    /** Das gewählte Konto, oder `null` wenn keines gewählt ist. */
    konto: Sachkonto | null;
    /** Wie viele Konten es insgesamt zur Auswahl gibt. */
    anzahl: number;
}

/**
 * Erklärt unter der Konto-Auswahl, wofür das gewählte Konto gedacht ist (Art und Beschreibung).
 * Ohne diese Zeile stand im Auswahlfeld nur „4120 Löhne & Gehälter" und niemand sah, was dort
 * hingehört. Gibt es gar keine Konten, sagt der Text das, statt ein leeres Feld zu lassen.
 */
export function SachkontoInfo({ konto, anzahl }: Props) {
    if (anzahl === 0) {
        return (
            <p role="status" data-testid="sachkonto-info" className="mt-2 text-sm text-amber-700">
                Es sind noch keine Konten angelegt. Bitte melden Sie das einem Administrator.
            </p>
        );
    }
    if (!konto) {
        return (
            <p data-testid="sachkonto-info" className="mt-2 text-sm text-slate-500">
                Wählen Sie ein Konto, dann steht hier, wofür es gedacht ist. Im Auswahlfeld steht die Erklärung unter jedem Namen.
            </p>
        );
    }
    const beschreibung = konto.beschreibung?.trim();
    return (
        <p data-testid="sachkonto-info" className="mt-2 text-sm text-slate-600">
            <span className="font-medium text-slate-800">{SACHKONTO_TYP_LABEL[konto.kontoTyp]}:</span>{' '}
            {beschreibung || 'Für dieses Konto ist keine Beschreibung hinterlegt.'}
        </p>
    );
}
