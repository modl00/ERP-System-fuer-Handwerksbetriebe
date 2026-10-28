import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { Label } from '../../ui/label';
import { Select } from '../../ui/select-custom';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Datenschutzstufe = 'LOKAL' | 'ALLGEMEIN' | 'MIT_VERTRAG' | 'CLOUD';
type Eingriff = 'AUS' | 'NUR_LESEN' | 'MIT_BESTAETIGUNG' | 'ERWEITERT';

interface AnbieterZeile { typ: string; anzeige: string; lokal: boolean; vertrag: boolean }
interface WerkzeugZeile { name: string; beschreibung: string; aendert: boolean; freischaltbar: boolean; freigegeben: boolean }
interface Stand { datenschutz: Datenschutzstufe; eingriff: Eingriff; anbieter: AnbieterZeile[]; werkzeuge: WerkzeugZeile[] }

const BASIS = '/api/settings/ki-obergrenze';
const json = { 'Content-Type': 'application/json' };

const DATENSCHUTZ = [
    { value: 'LOKAL', label: 'Nur im Haus (lokale KI)', beschreibung: 'Nichts verlässt den Betrieb. Cloud-Anbieter sind gesperrt.' },
    { value: 'ALLGEMEIN', label: 'Nur allgemeine Anfragen', beschreibung: 'Cloud-Anbieter sind erlaubt, bekommen aber keine Kunden, Stammdaten, Bankdaten oder Dokumente. Dafür ist kein Vertrag mit dem Anbieter nötig.' },
    { value: 'MIT_VERTRAG', label: 'Anbieter mit Vertrag', beschreibung: 'Daten aus dem Programm gehen nur an Anbieter, für die Sie unten einen Vertrag eingetragen haben.' },
    { value: 'CLOUD', label: 'Alle eingerichteten Anbieter', beschreibung: 'Keine zusätzliche Einschränkung durch diese Einstellung.' },
];

const EINGRIFF = [
    { value: 'AUS', label: 'Aus', beschreibung: 'Keine KI-Funktion im Programm.' },
    { value: 'NUR_LESEN', label: 'Nur lesen', beschreibung: 'Auskünfte und Suchen, nichts ändern.' },
    { value: 'MIT_BESTAETIGUNG', label: 'Mit Bestätigung (empfohlen)', beschreibung: 'Änderungen nur als Vorschlag mit „Ja, ausführen".' },
    { value: 'ERWEITERT', label: 'Erweitert', beschreibung: 'Zusätzlich laufen einzeln freigeschaltete Werkzeuge ohne Rückfrage. Löschen nie.' },
];

/** Ein Satz, was die gewählte Stufe praktisch heißt. */
function wirkung(d: Datenschutzstufe, e: Eingriff): string {
    if (e === 'AUS') return 'Zurzeit ist die KI ausgeschaltet.';
    const daten = {
        LOKAL: 'Es wird nur die lokale KI im Haus benutzt.',
        ALLGEMEIN: 'Cloud-Anbieter sehen nur allgemeine Fragen, nie Daten aus dem Programm.',
        MIT_VERTRAG: 'Daten aus dem Programm gehen nur an Anbieter mit eingetragenem Vertrag.',
        CLOUD: 'Alle eingerichteten Anbieter dürfen Daten aus dem Programm sehen.',
    }[d];
    const tun = {
        NUR_LESEN: 'Die KI darf nur nachschlagen.',
        MIT_BESTAETIGUNG: 'Jede Änderung braucht Ihre Bestätigung.',
        ERWEITERT: 'Freigeschaltete Werkzeuge laufen ohne Rückfrage, alles andere braucht Ihre Bestätigung.',
    }[e];
    return `${daten} ${tun}`;
}

/**
 * Die zentrale Obergrenze für die KI: wohin gehen Daten und was darf die KI tun. Sie steht über allen KI-Funktionen und über den Rechten; was sie verbietet,
 * bietet das Programm nicht an, und der Server prüft es bei jedem Aufruf. Änderungen stehen im KI-Protokoll. Die rechtliche Prüfung (zum Beispiel ob ein
 * Vertrag besteht) bleibt beim Betrieb; das Programm trägt nur ein, was Sie angeben.
 */
export function KiObergrenzeKarte() {
    const toast = useToast();
    const [stand, setStand] = useState<Stand | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [datenschutz, setDatenschutz] = useState<Datenschutzstufe>('CLOUD');
    const [eingriff, setEingriff] = useState<Eingriff>('MIT_BESTAETIGUNG');
    const [vertrag, setVertrag] = useState<Record<string, boolean>>({});
    const [freigabe, setFreigabe] = useState<Set<string>>(new Set());
    const [speichert, setSpeichert] = useState(false);

    const uebernehmen = (s: Stand) => {
        setStand(s);
        setDatenschutz(s.datenschutz);
        setEingriff(s.eingriff);
        setVertrag(Object.fromEntries(s.anbieter.filter(a => !a.lokal).map(a => [a.typ, a.vertrag])));
        setFreigabe(new Set(s.werkzeuge.filter(w => w.freigegeben).map(w => w.name)));
    };

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht geladen werden.'));
                return res.json() as Promise<Stand>;
            })
            .then(s => { if (!abgebrochen) uebernehmen(s); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Einstellung konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, []);

    if (ladefehler) return <SettingsCard icon={<ShieldCheck className="w-5 h-5 text-rose-600" />} title="Wie weit darf die KI gehen?"><p role="alert" className="text-sm text-rose-700">{ladefehler}</p></SettingsCard>;
    if (!stand) return <SectionLoading />;

    const geaendert = datenschutz !== stand.datenschutz || eingriff !== stand.eingriff
        || stand.anbieter.some(a => !a.lokal && (vertrag[a.typ] ?? false) !== a.vertrag)
        || stand.werkzeuge.some(w => w.freigegeben !== freigabe.has(w.name));
    const freischaltbare = stand.werkzeuge.filter(w => w.freischaltbar);

    const schalte = (name: string) => setFreigabe(alt => { const neu = new Set(alt); if (neu.has(name)) neu.delete(name); else neu.add(name); return neu; });

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(BASIS, {
                method: 'PUT',
                headers: json,
                body: JSON.stringify({ datenschutz, eingriff, vertrag, ohneRueckfrage: eingriff === 'ERWEITERT' ? [...freigabe] : [] }),
            });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Die Einstellung konnte nicht gespeichert werden.'));
                return;
            }
            uebernehmen(await res.json() as Stand);
            toast.success('Gespeichert. Die Änderung steht im KI-Protokoll.');
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<ShieldCheck className="w-5 h-5 text-rose-600" />}
            title="Wie weit darf die KI gehen?"
            description={
                <>
                    <p>
                        Diese Einstellung steht über allen KI-Funktionen und über den Rechten der Mitarbeiter. Was sie verbietet, bietet das Programm gar nicht erst an.
                        Jede Änderung wird im KI-Protokoll festgehalten.
                    </p>
                    <p data-testid="ki-obergrenze-wirkung" className="font-medium text-slate-700">{wirkung(datenschutz, eingriff)}</p>
                </>
            }
        >
            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
                <div>
                    <Label htmlFor="ki-obergrenze-datenschutz">Wohin dürfen Daten gehen?</Label>
                    <Select id="ki-obergrenze-datenschutz" value={datenschutz} onChange={v => setDatenschutz(v as Datenschutzstufe)} options={DATENSCHUTZ} />
                </div>
                <div>
                    <Label htmlFor="ki-obergrenze-eingriff">Was darf die KI tun?</Label>
                    <Select id="ki-obergrenze-eingriff" value={eingriff} onChange={v => setEingriff(v as Eingriff)} options={EINGRIFF} />
                </div>
            </div>

            {datenschutz === 'MIT_VERTRAG' && (
                <fieldset className="mt-5 rounded-lg border border-slate-200 p-4">
                    <legend className="px-1 text-sm font-medium text-slate-800">Mit welchen Anbietern besteht ein Vertrag?</legend>
                    <p className="mb-2 text-xs text-slate-500">
                        Gemeint ist ein Vertrag zur Auftragsverarbeitung, nach dem der Anbieter keine Kundendaten speichert oder weiterverwendet. Das Programm trägt nur ein, was Sie
                        angeben; die rechtliche Prüfung bleibt bei Ihnen.
                    </p>
                    {stand.anbieter.filter(a => !a.lokal).map(a => (
                        <label key={a.typ} className="flex min-h-9 items-center gap-2 text-sm text-slate-700">
                            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 accent-rose-600" checked={vertrag[a.typ] ?? false}
                                onChange={e => setVertrag(alt => ({ ...alt, [a.typ]: e.target.checked }))} />
                            Mit {a.anzeige} besteht ein Vertrag
                        </label>
                    ))}
                </fieldset>
            )}

            {eingriff === 'ERWEITERT' && (
                <fieldset className="mt-5 rounded-lg border border-amber-200 bg-amber-50 p-4">
                    <legend className="px-1 text-sm font-medium text-amber-900">Diese Werkzeuge dürfen ohne Rückfrage laufen</legend>
                    <p className="mb-2 text-xs text-amber-900">
                        Nur für unkritische Dinge einschalten. Texte aus Dokumenten oder E-Mails können die KI zu Aktionen verleiten; jede Aktion ohne Rückfrage steht im
                        KI-Protokoll. Werkzeuge, die etwas löschen, gibt es hier nie. Was nicht angehakt ist, braucht weiter Ihre Bestätigung.
                    </p>
                    {freischaltbare.length === 0 && <p className="text-sm text-slate-600">Es gibt keine ändernden Werkzeuge, die sich freischalten lassen.</p>}
                    {freischaltbare.map(w => (
                        <label key={w.name} className="flex min-h-9 items-start gap-2 py-1 text-sm text-slate-800">
                            <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-rose-600" checked={freigabe.has(w.name)} onChange={() => schalte(w.name)} aria-label={w.name} />
                            <span><span className="font-mono text-xs">{w.name}</span><span className="block text-xs text-slate-600">{w.beschreibung}</span></span>
                        </label>
                    ))}
                </fieldset>
            )}

            <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>Speichern</SaveButton>
        </SettingsCard>
    );
}
