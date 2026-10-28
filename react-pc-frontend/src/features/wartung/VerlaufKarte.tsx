import { useEffect, useState } from 'react';
import { History } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { ladeVerlauf } from './api';
import { STATUS_ANZEIGE } from './format';
import type { WartungVerlaufEintrag } from './types';

interface Props {
    protokollId: number;
    /** Ändert sich bei jedem Statuswechsel und lädt den Verlauf neu. */
    stand: string;
}

const zeitText = (iso: string) => {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
};

/** Wer das Protokoll wann abgeschlossen, wieder geöffnet oder archiviert hat (#238). Ohne Einträge zeigt die Karte nichts. */
export function VerlaufKarte({ protokollId, stand }: Props) {
    const [eintraege, setEintraege] = useState<WartungVerlaufEintrag[]>([]);
    const [fehler, setFehler] = useState(false);

    useEffect(() => {
        let aktuell = true;
        ladeVerlauf(protokollId).then(
            (e) => { if (aktuell) { setEintraege(e); setFehler(false); } },
            () => { if (aktuell) setFehler(true); },
        );
        return () => { aktuell = false; };
    }, [protokollId, stand]);

    if (!fehler && eintraege.length === 0) return null;
    return (
        <Card className="p-4" data-testid="wartung-verlauf">
            <h3 className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-900">
                <History className="h-4 w-4 text-rose-500" aria-hidden="true" /> Verlauf
            </h3>
            {fehler ? (
                <p className="text-sm text-slate-500" role="status">Der Verlauf konnte nicht geladen werden.</p>
            ) : (
                <ul className="space-y-1.5 text-sm text-slate-700">
                    {eintraege.map((e, i) => (
                        <li key={`${e.zeit}-${i}`}>
                            <span className="font-medium">{STATUS_ANZEIGE[e.nachStatus].label}</span>
                            <span className="text-slate-500"> (vorher {STATUS_ANZEIGE[e.vonStatus].label.toLowerCase()})</span>
                            {' '}· {zeitText(e.zeit)}{e.benutzer ? ` · ${e.benutzer}` : ''}
                            {e.grund && <span className="block text-slate-600">„{e.grund}"</span>}
                        </li>
                    ))}
                </ul>
            )}
        </Card>
    );
}
