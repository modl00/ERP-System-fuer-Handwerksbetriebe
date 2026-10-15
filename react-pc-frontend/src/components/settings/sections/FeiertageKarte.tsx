import { useEffect, useState } from 'react';
import { CalendarDays, Loader2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { SettingsCard } from '../settingsUi';

interface Land { kuerzel: string; name: string }
interface Antwort { bundesland: string; auswahl: Land[] }

async function lese(res: Response, fallback: string): Promise<Antwort> {
    if (!res.ok) {
        let meldung = fallback;
        try { const b = await res.json() as { message?: string }; if (b?.message) meldung = b.message; } catch { /* keine Meldung vom Server */ }
        throw new Error(meldung);
    }
    return res.json() as Promise<Antwort>;
}

/** Das Bundesland, nach dem die Feiertage gelten: für Zeitkonto, Lohn und Plantafel. */
export function FeiertageKarte() {
    const toast = useToast();
    const [auswahl, setAuswahl] = useState<Land[]>([]);
    const [gespeichert, setGespeichert] = useState('');
    const [land, setLand] = useState('');
    const [laedt, setLaedt] = useState(true);
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        fetch('/api/settings/feiertage-bundesland')
            .then(r => lese(r, 'Das Bundesland konnte nicht geladen werden.'))
            .then(a => { if (aktuell) { setAuswahl(a.auswahl); setGespeichert(a.bundesland); setLand(a.bundesland); } })
            .catch(e => { if (aktuell) toast.error(e instanceof Error ? e.message : 'Das Bundesland konnte nicht geladen werden.'); })
            .finally(() => { if (aktuell) setLaedt(false); });
        return () => { aktuell = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const speichere = async () => {
        setSpeichert(true);
        try {
            const a = await lese(await fetch('/api/settings/feiertage-bundesland', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ bundesland: land }) }),
                'Das Bundesland konnte nicht gespeichert werden.');
            setGespeichert(a.bundesland);
            setLand(a.bundesland);
            toast.success('Das Bundesland für die Feiertage ist gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Bundesland konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<CalendarDays className="w-5 h-5 text-rose-600" />}
            title="Feiertage"
            description={(
                <>
                    <p>In welchem Bundesland liegt der Betrieb? Danach richten sich die gesetzlichen Feiertage in der Zeiterfassung (Sollstunden, Urlaub), in der Lohnrechnung und in der Plantafel.</p>
                    <p className="text-amber-700">Achtung: Die Umstellung gilt für alle Zeiträume, auch für schon vergangene Monate. Stellen Sie sie am besten nur einmal zu Beginn ein, nicht mitten im Jahr.</p>
                </>
            )}
        >
            {laedt ? <Loader2 className="h-5 w-5 animate-spin text-slate-400" aria-label="Lädt" /> : (
                <div className="flex flex-wrap items-end gap-3">
                    <div className="space-y-1.5">
                        <Label htmlFor="feiertage-bundesland">Bundesland</Label>
                        <select id="feiertage-bundesland" value={land} onChange={e => setLand(e.target.value)}
                            className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                            {auswahl.map(l => <option key={l.kuerzel} value={l.kuerzel}>{l.name}</option>)}
                        </select>
                    </div>
                    <Button onClick={() => void speichere()} disabled={speichert || land === gespeichert} className="bg-rose-600 text-white hover:bg-rose-700">Speichern</Button>
                </div>
            )}
        </SettingsCard>
    );
}
