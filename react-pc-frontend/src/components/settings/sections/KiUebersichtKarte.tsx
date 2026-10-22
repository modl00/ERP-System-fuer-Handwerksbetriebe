import { useEffect, useState } from 'react';
import { Info, LayoutList } from 'lucide-react';
import { SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

type Anbieter = 'GEMINI' | 'LOKAL' | 'CLAUDE' | 'MISTRAL';

interface FunktionsEinstellung { funktion: string; anzeige: string; anbieter: Anbieter; datenschutz: 'NUR_LOKAL' | 'CLOUD_ERLAUBT' }
interface AnbieterStand { typ: Anbieter; anzeige: string; lokal: boolean; eingerichtet: boolean; modell: string; schluesselGesetzt: boolean; kannBild: boolean; kannPdf: boolean }
interface AnbieterUebersicht { funktionen: FunktionsEinstellung[]; anbieter: AnbieterStand[]; nochGemini: string[] }
interface FunktionsModell { schluessel: string; anzeige: string; modell: string }
interface ModellUebersicht { funktionen: FunktionsModell[] }
interface Obergrenze { datenschutz: string; eingriff: string; anbieter: { typ: Anbieter; lokal: boolean }[] }

export type KiBereich = 'anbieter' | 'grenzen';

type Status = { text: string; art: 'gut' | 'warnung' | 'schlecht' };

interface Zeile {
    schluessel: string;
    anzeige: string;
    anbieter: string;
    modell: string;
    datenschutz: string;
    status: Status;
    aendern: KiBereich;
}

const FARBE: Record<Status['art'], string> = {
    gut: 'bg-emerald-50 text-emerald-800',
    warnung: 'bg-amber-50 text-amber-800',
    schlecht: 'bg-rose-50 text-rose-800',
};

/** KiFunktion.schluessel → KiModellService.Funktion.schluessel (für die Gemini-Modell-Anzeige je Funktion). */
const MODELL_SCHLUESSEL: Record<string, string> = {
    hilfe: 'ki-hilfe',
    scanner: 'scanner',
    'dokument-erkennen': 'dokument-analyse',
    beitrag: 'pro',
    assistent: 'assistent',
    'email-einordnen': 'email-classification',
    'email-text': 'email-text',
    'pdf-auslesen': 'pdf-extractor',
    plantafel: 'plantafel',
    testbox: 'testbox',
    spracheingabe: 'spracheingabe',
};
/** Alle Modell-Schlüssel, die über KiFunktion/KiAnbieterService konfigurierbar sind – kein Gemini-Fest-Eintrag nötig. */
const WAEHLBAR_ALS_MODELL = new Set(Object.values(MODELL_SCHLUESSEL));

function statusVon(a: AnbieterStand | undefined, o: Obergrenze | null, dieseFunktionPruefenObergrenze: boolean): Status {
    if (!a) return { text: 'Nicht eingerichtet', art: 'schlecht' };
    if (dieseFunktionPruefenObergrenze && o) {
        if (o.eingriff === 'AUS') return { text: 'Ausgeschaltet (Grenze)', art: 'schlecht' };
        if (o.datenschutz === 'LOKAL' && !a.lokal) return { text: 'Gesperrt (Grenze)', art: 'schlecht' };
    }
    if (a.eingerichtet) return { text: 'Läuft', art: 'gut' };
    return { text: a.lokal ? 'Nicht eingerichtet' : 'Schlüssel fehlt', art: 'schlecht' };
}

function baueZeilen(an: AnbieterUebersicht, mo: ModellUebersicht | null, ober: Obergrenze | null): Zeile[] {
    const stand = (typ: Anbieter) => an.anbieter.find(a => a.typ === typ);
    const gemini = stand('GEMINI');
    const modellVon = (schluessel: string) => mo?.funktionen.find(f => f.schluessel === schluessel)?.modell ?? '';

    const waehlbar: Zeile[] = an.funktionen.map(f => {
        const a = stand(f.anbieter);
        const modellSchluessel = MODELL_SCHLUESSEL[f.funktion];
        const modell = f.anbieter === 'GEMINI' && modellSchluessel ? modellVon(modellSchluessel) : (a?.modell ?? '');
        return {
            schluessel: f.funktion,
            anzeige: f.anzeige,
            anbieter: a?.anzeige ?? f.anbieter,
            modell,
            datenschutz: f.datenschutz === 'NUR_LOKAL' ? 'Nur im Haus' : 'Cloud erlaubt',
            status: statusVon(a, ober, true),
            aendern: 'anbieter',
        };
    });

    // Die übrigen Funktionen laufen fest über Gemini; ihr Modell steht in den Modell-Einstellungen
    const fest: Zeile[] = (mo?.funktionen ?? [])
        .filter(f => !WAEHLBAR_ALS_MODELL.has(f.schluessel))
        .map(f => ({
            schluessel: f.schluessel,
            anzeige: f.anzeige,
            anbieter: gemini?.anzeige ?? 'Gemini',
            modell: f.modell,
            datenschutz: 'Cloud (Gemini)',
            status: statusVon(gemini, null, false),
            aendern: 'anbieter',
        }));
    return [...waehlbar, ...fest];
}

/**
 * Eine Zeile je KI-Funktion: Welcher Anbieter, welches Modell, wohin dürfen die Daten, läuft es? Geändert wird in „Anbieter und Modelle“ und unter
 * „Grenzen“; hier sieht man nur auf einen Blick, was gerade gilt.
 */
export function KiUebersichtKarte({ onOeffne }: { onOeffne: (bereich: KiBereich) => void }) {
    const [zeilen, setZeilen] = useState<Zeile[] | null>(null);
    const [ober, setOber] = useState<Obergrenze | null>(null);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let abgebrochen = false;
        const laden = async () => {
            const [an, mo, ob] = await Promise.all([
                fetch('/api/settings/ki-anbieter'), fetch('/api/settings/ki-modelle'), fetch('/api/settings/ki-obergrenze'),
            ]);
            if (!an.ok) throw new Error(await parseErrorMessage(an, 'Die KI-Übersicht konnte nicht geladen werden.'));
            const anbieter = await an.json() as AnbieterUebersicht;
            // Ohne die Modell-Liste oder die Grenzen bleibt die Übersicht nutzbar, nur mit weniger Angaben
            const modelle = mo.ok ? await mo.json() as ModellUebersicht : null;
            const grenze = ob.ok ? await ob.json() as Obergrenze : null;
            if (abgebrochen) return;
            setOber(grenze);
            setZeilen(baueZeilen(anbieter, modelle, grenze));
        };
        laden().catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Die KI-Übersicht konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, []);

    if (!zeilen && !fehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<LayoutList className="w-5 h-5 text-rose-600" />}
            title="Was nutzt welche KI-Funktion?"
            description={
                <p className="flex items-start gap-1.5">
                    <span>Alles auf einen Blick.</span>
                    <span title="Geändert wird unter „Anbieter und Modelle“ (Zugang, Modell, Anbieter je Funktion) und unter „Grenzen“ (wie weit die KI gehen darf). Hier steht nur, was gerade gilt."
                        className="inline-flex text-slate-400" role="img" aria-label="Wo wird geändert? Unter Anbieter und Modelle und unter Grenzen.">
                        <Info className="w-4 h-4 mt-0.5" aria-hidden="true" />
                    </span>
                </p>
            }
        >
            {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
            {zeilen && (
                <div className="space-y-3">
                    {ober && (
                        <p className="text-sm text-slate-600" data-testid="ki-uebersicht-grenze">
                            Grenze:{' '}
                            <button type="button" className="text-rose-700 hover:underline" onClick={() => onOeffne('grenzen')}>
                                {ober.eingriff === 'AUS' ? 'KI ausgeschaltet' : ober.datenschutz === 'LOKAL' ? 'nur im Haus' : ober.datenschutz === 'ALLGEMEIN' ? 'nur allgemeine Anfragen' : ober.datenschutz === 'MIT_VERTRAG' ? 'Anbieter mit Vertrag' : 'alle eingerichteten Anbieter'}
                            </button>
                        </p>
                    )}
                    <div className="overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-sm" data-testid="ki-uebersicht">
                            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                                <tr>
                                    <th scope="col" className="px-3 py-2 font-medium">Funktion</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Anbieter</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Modell</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Datenschutz</th>
                                    <th scope="col" className="px-3 py-2 font-medium">Status</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {zeilen.map(z => (
                                    <tr key={z.schluessel} data-testid={`ki-uebersicht-${z.schluessel}`}>
                                        <th scope="row" className="px-3 py-2 text-left font-medium text-slate-800">
                                            <button type="button" className="text-left hover:text-rose-700 hover:underline" onClick={() => onOeffne(z.aendern)}
                                                aria-label={`${z.anzeige} ändern`}>{z.anzeige}</button>
                                        </th>
                                        <td className="px-3 py-2 text-slate-700">{z.anbieter}</td>
                                        <td className="px-3 py-2 font-mono text-xs text-slate-600">{z.modell || '–'}</td>
                                        <td className="px-3 py-2 text-slate-700">{z.datenschutz}</td>
                                        <td className="px-3 py-2">
                                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${FARBE[z.status.art]}`}>{z.status.text}</span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </SettingsCard>
    );
}
