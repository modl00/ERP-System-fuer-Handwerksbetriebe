import { useState } from 'react';
import { FolderOpen, Loader2, TriangleAlert } from 'lucide-react';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { SaveButton, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Richtung = 'EINGANG' | 'GESENDET';

interface ServerOrdner { name: string; anzeige: string; vorschlag: Richtung }
interface Eintrag { name: string; richtung: Richtung; seit: string | null }
interface Uebersicht { konfiguriert: boolean; ausgewaehlt: Eintrag[]; ordner: ServerOrdner[] }
interface Zeile { gewaehlt: boolean; richtung: Richtung; nachladen: boolean; war: boolean }

/**
 * „Welche Ordner liest das Programm?" Das Programm liest nur: Es verschiebt, löscht und markiert nichts im Postfach.
 * Für neu gewählte Ordner werden nur Mails ab heute geholt; ältere nur auf ausdrücklichen Wunsch.
 */
export function MailOrdnerAuswahl() {
    const toast = useToast();
    const [laedt, setLaedt] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [zeilen, setZeilen] = useState<Record<string, Zeile>>({});

    const laden = async () => {
        setLaedt(true);
        setFehler('');
        try {
            const res = await fetch('/api/settings/email/ordner');
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Ordner konnten nicht geladen werden.'));
            const u = await res.json() as Uebersicht;
            const bekannt = new Map(u.ausgewaehlt.map(e => [e.name, e]));
            const neu: Record<string, Zeile> = {};
            for (const o of u.ordner) {
                const e = bekannt.get(o.name);
                // Ohne gespeicherte Auswahl ist der Posteingang vorgewählt, gesendete Ordner nach Vorschlag des Servers
                const vorbelegt = !u.konfiguriert && (o.name.toUpperCase() === 'INBOX' || o.vorschlag === 'GESENDET');
                neu[o.name] = { gewaehlt: !!e || vorbelegt, richtung: e ? e.richtung : o.vorschlag, nachladen: false, war: !!e };
            }
            setDaten(u);
            setZeilen(neu);
        } catch (e) {
            setDaten(null);
            setFehler(e instanceof Error ? e.message : 'Die Ordner konnten nicht geladen werden.');
        } finally {
            setLaedt(false);
        }
    };

    const aendere = (name: string, teil: Partial<Zeile>) => setZeilen(z => ({ ...z, [name]: { ...z[name], ...teil } }));
    const anzahl = Object.values(zeilen).filter(z => z.gewaehlt).length;
    const nachladeAnzahl = Object.values(zeilen).filter(z => z.gewaehlt && !z.war && z.nachladen).length;

    const speichern = async () => {
        setSpeichert(true);
        try {
            const body = Object.entries(zeilen).filter(([, z]) => z.gewaehlt).map(([name, z]) => ({ name, richtung: z.richtung, nachladen: z.nachladen && !z.war }));
            const res = await fetch('/api/settings/email/ordner', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Auswahl konnte nicht gespeichert werden.'));
            toast.success('Die Ordner-Auswahl ist gespeichert. Der nächste Abruf nutzt sie.');
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Auswahl konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<FolderOpen className="w-5 h-5 text-rose-600" />}
            title="Welche Ordner liest das Programm?"
            description={
                <>
                    <p>Wählen Sie die Ordner Ihres Postfachs, die im E-Mail-Center erscheinen sollen. Das Programm <strong>liest nur</strong>: Es verschiebt, löscht oder markiert nichts in Ihrem Postfach.</p>
                    <p>Ohne Auswahl liest es wie bisher den Posteingang und die gesendeten Mails.</p>
                </>
            }
        >
            <Button variant="outline" onClick={() => void laden()} disabled={laedt || speichert}>
                {laedt && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} {daten ? 'Ordner neu laden' : 'Ordner vom Server laden'}
            </Button>
            {fehler && <p role="alert" className="mt-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            {daten && (
                <div className="mt-4 space-y-3" data-testid="mail-ordner">
                    {daten.ordner.length === 0 ? <p className="text-sm text-slate-500">Auf dem Server wurden keine Ordner gefunden.</p> : (
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                                    <th scope="col" className="w-8 py-2"><span className="sr-only">Lesen</span></th>
                                    <th scope="col" className="py-2 pr-3 font-semibold">Ordner</th>
                                    <th scope="col" className="px-2 py-2 font-semibold">Das sind</th>
                                    <th scope="col" className="pl-2 py-2 font-semibold">Ältere Mails</th>
                                </tr>
                            </thead>
                            <tbody>
                                {daten.ordner.map(o => {
                                    const z = zeilen[o.name];
                                    if (!z) return null;
                                    return (
                                        <tr key={o.name} className="border-b border-slate-100" data-testid={`ordner-${o.name}`}>
                                            <td className="py-2"><input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${o.name} lesen`} checked={z.gewaehlt} onChange={e => aendere(o.name, { gewaehlt: e.target.checked })} /></td>
                                            <td className="py-2 pr-3 break-all"><span className="font-medium text-slate-900">{o.anzeige}</span>{o.anzeige !== o.name && <span className="block text-xs text-slate-500">{o.name}</span>}</td>
                                            <td className="px-2 py-2">
                                                <select aria-label={`${o.name}: Art`} value={z.richtung} disabled={!z.gewaehlt} onChange={e => aendere(o.name, { richtung: e.target.value as Richtung })}
                                                    className="min-h-9 rounded-md border border-slate-300 bg-white px-2 text-sm disabled:opacity-50">
                                                    <option value="EINGANG">Eingang</option>
                                                    <option value="GESENDET">Gesendet</option>
                                                </select>
                                            </td>
                                            <td className="pl-2 py-2 text-slate-600">
                                                {z.war ? <span className="text-xs text-slate-500">wie beim Wählen festgelegt</span> : (
                                                    <label className="inline-flex items-center gap-2 text-sm">
                                                        <input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${o.name}: ältere Mails nachladen`} checked={z.nachladen} disabled={!z.gewaehlt} onChange={e => aendere(o.name, { nachladen: e.target.checked })} /> nachladen
                                                    </label>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    )}
                    {nachladeAnzahl > 0 && (
                        <p role="note" className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                            <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                            <span>Beim Nachladen holt das Programm <strong>alle</strong> Mails aus {nachladeAnzahl === 1 ? 'diesem Ordner' : 'diesen Ordnern'}. Bei großen Ordnern kann das lange dauern, den Server belasten und die automatische Einordnung (KI) für jede Mail auslösen. Ohne Haken werden nur Mails ab heute geholt.</span>
                        </p>
                    )}
                    <SaveButton onClick={() => void speichern()} saving={speichert} disabled={anzahl === 0}>Auswahl speichern ({anzahl})</SaveButton>
                </div>
            )}
        </SettingsCard>
    );
}
