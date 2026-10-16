import { useEffect, useState } from 'react';
import { ScrollText } from 'lucide-react';
import { SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Eintrag {
    id: number;
    zeit: string;
    benutzer: string | null;
    funktion: string;
    anbieter: string;
    modell: string | null;
    werkzeug: string;
    status: string;
    ergebnis: string | null;
}

const STATUS: Record<string, string> = {
    AUSGEFUEHRT: 'Ausgeführt', VORGESCHLAGEN: 'Vorgeschlagen', BESTAETIGT: 'Bestätigt', VERWORFEN: 'Verworfen', FEHLER: 'Fehler',
};

/** „zeit_buchen“ → „Zeit buchen“ */
function werkzeugText(name: string): string {
    const text = name.replace(/_/g, ' ').trim();
    return text ? text.charAt(0).toUpperCase() + text.slice(1) : name;
}

function zeitText(zeit: string): string {
    const d = new Date(zeit);
    return Number.isNaN(d.getTime()) ? zeit : d.toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' });
}

/** Was die KI getan hat oder tun wollte, neueste zuerst. Zeigt die letzten 100 Einträge. */
export function KiProtokollKarte() {
    const [eintraege, setEintraege] = useState<Eintrag[] | null>(null);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/settings/ki-protokoll?limit=100')
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Das Protokoll konnte nicht geladen werden.'));
                return res.json() as Promise<Eintrag[]>;
            })
            .then(e => { if (!abgebrochen) setEintraege(e); })
            .catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Das Protokoll konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, []);

    if (!eintraege && !fehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<ScrollText className="w-5 h-5 text-rose-600" />}
            title="KI-Protokoll"
            description={<p>Was die KI getan oder vorgeschlagen hat, und jede Änderung an den Grenzen. Neueste zuerst, die letzten 100 Einträge.</p>}
        >
            {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
            {eintraege && eintraege.length === 0 && <p className="text-sm text-slate-500">Noch nichts protokolliert.</p>}
            {eintraege && eintraege.length > 0 && (
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-sm" data-testid="ki-protokoll">
                        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                            <tr>
                                <th scope="col" className="px-3 py-2 font-medium">Wann</th>
                                <th scope="col" className="px-3 py-2 font-medium">Wer</th>
                                <th scope="col" className="px-3 py-2 font-medium">Aktion</th>
                                <th scope="col" className="px-3 py-2 font-medium">Anbieter</th>
                                <th scope="col" className="px-3 py-2 font-medium">Status</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {eintraege.map(e => (
                                <tr key={e.id} className="align-top">
                                    <td className="px-3 py-2 whitespace-nowrap text-slate-600">{zeitText(e.zeit)}</td>
                                    <td className="px-3 py-2 text-slate-700">{e.benutzer || '–'}</td>
                                    <td className="px-3 py-2 text-slate-800">
                                        {e.ergebnis ? (
                                            <details>
                                                <summary className="cursor-pointer">{werkzeugText(e.werkzeug)}</summary>
                                                <p className="mt-1 text-xs text-slate-600 whitespace-pre-wrap">{e.ergebnis}</p>
                                            </details>
                                        ) : werkzeugText(e.werkzeug)}
                                    </td>
                                    <td className="px-3 py-2 text-slate-700">{e.anbieter}{e.modell ? ` (${e.modell})` : ''}</td>
                                    <td className="px-3 py-2 text-slate-700">{STATUS[e.status] ?? e.status}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </SettingsCard>
    );
}
