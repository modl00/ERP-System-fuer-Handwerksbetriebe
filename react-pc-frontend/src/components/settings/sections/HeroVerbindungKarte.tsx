import { useEffect, useState } from 'react';
import { DatabaseZap, Loader2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { PasswordField, SaveButton, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Status { schluesselGesetzt: boolean; adresse: string }
interface Ergebnis { ok: boolean; meldung: string; antwort: string }
interface Feld { name: string; argumente: string; typ: string }

const BEISPIEL = '{ contacts { id nr company_name last_name email } }';

/**
 * Datenübernahme aus Hero Handwerkersoftware (Issue #76), Stufe 1: Verbindung herstellen und die Schnittstelle erkunden. Das Programm <strong>liest nur</strong>; es werden keine Daten in Hero
 * geändert und noch keine Daten ins Programm übernommen.
 */
export function HeroVerbindungKarte() {
    const toast = useToast();
    const [status, setStatus] = useState<Status | null>(null);
    const [schluessel, setSchluessel] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [arbeitet, setArbeitet] = useState<'test' | 'schnittstelle' | 'abfrage' | null>(null);
    const [test, setTest] = useState<Ergebnis | null>(null);
    const [felder, setFelder] = useState<Feld[] | null>(null);
    const [abfrage, setAbfrage] = useState(BEISPIEL);
    const [antwort, setAntwort] = useState<Ergebnis | null>(null);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let aktuell = true;
        fetch('/api/settings/import/hero').then(r => (r.ok ? r.json() : null)).then((s: Status | null) => { if (aktuell && s) setStatus(s); }).catch(() => { /* ohne Status bleibt die Karte leer */ });
        return () => { aktuell = false; };
    }, []);

    const senden = async <T,>(url: string, init?: RequestInit): Promise<T> => {
        const res = await fetch(url, init);
        if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Anfrage an Hero ist fehlgeschlagen.'));
        return res.json() as Promise<T>;
    };

    const speichern = async () => {
        setSpeichert(true);
        setFehler('');
        try {
            const s = await senden<Status>('/api/settings/import/hero/schluessel', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ schluessel }) });
            setStatus(s);
            setSchluessel('');
            toast.success(s.schluesselGesetzt ? 'Der Schlüssel ist gespeichert.' : 'Der Schlüssel wurde entfernt.');
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Schlüssel konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const ausfuehren = async (art: 'test' | 'schnittstelle' | 'abfrage') => {
        setArbeitet(art);
        setFehler('');
        try {
            if (art === 'test') setTest(await senden<Ergebnis>('/api/settings/import/hero/test', { method: 'POST' }));
            else if (art === 'schnittstelle') setFelder(await senden<Feld[]>('/api/settings/import/hero/schnittstelle'));
            else setAntwort(await senden<Ergebnis>('/api/settings/import/hero/abfrage', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ abfrage }) }));
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Anfrage an Hero ist fehlgeschlagen.');
        } finally {
            setArbeitet(null);
        }
    };

    return (
        <SettingsCard
            icon={<DatabaseZap className="w-5 h-5 text-rose-600" />}
            title="Hero Handwerkersoftware"
            description={
                <>
                    <p>Hier verbinden Sie das Programm mit Ihrem Hero-Konto, um später Projekte, Kunden und Belege zu übernehmen. Dieser Schritt <strong>liest nur</strong>: Es wird nichts in Hero geändert und noch nichts ins Programm übernommen.</p>
                    <p>Den Schlüssel (API-Key) erhalten Sie vom Hero-Support. Er wird verschlüsselt gespeichert und nie wieder angezeigt.</p>
                </>
            }
        >
            <div className="max-w-xl space-y-3">
                <PasswordField id="hero-schluessel" label="Hero-Schlüssel" value={schluessel} onChange={setSchluessel} isSet={!!status?.schluesselGesetzt} placeholder="Schlüssel einfügen" />
                <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!schluessel.trim() && !status?.schluesselGesetzt}>
                    {schluessel.trim() ? 'Schlüssel speichern' : 'Schlüssel entfernen'}
                </SaveButton>
                {status && <p className="text-xs text-slate-500">Adresse der Schnittstelle: <span className="font-mono break-all">{status.adresse}</span></p>}
            </div>

            {fehler && <p role="alert" className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            {status?.schluesselGesetzt && (
                <div className="mt-6 space-y-5 border-t border-slate-200 pt-5">
                    <div>
                        <Button variant="outline" onClick={() => void ausfuehren('test')} disabled={arbeitet !== null}>
                            {arbeitet === 'test' && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Verbindung testen
                        </Button>
                        {test && <p role="status" className={`mt-2 text-sm ${test.ok ? 'text-emerald-700' : 'text-rose-700'}`}>{test.meldung}</p>}
                    </div>

                    <div>
                        <Button variant="outline" onClick={() => void ausfuehren('schnittstelle')} disabled={arbeitet !== null}>
                            {arbeitet === 'schnittstelle' && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Schnittstelle anzeigen
                        </Button>
                        {felder && (
                            <div className="mt-3 max-h-72 overflow-auto rounded-lg border border-slate-200" data-testid="hero-schnittstelle">
                                <table className="w-full text-sm">
                                    <thead><tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500"><th scope="col" className="px-3 py-2">Abfrage</th><th scope="col" className="px-3 py-2">Angaben</th><th scope="col" className="px-3 py-2">Liefert</th></tr></thead>
                                    <tbody>
                                        {felder.map(f => (
                                            <tr key={f.name} className="border-b border-slate-100 align-top">
                                                <td className="px-3 py-1.5 font-mono">{f.name}</td>
                                                <td className="px-3 py-1.5 font-mono text-xs text-slate-600 break-words">{f.argumente || '–'}</td>
                                                <td className="px-3 py-1.5 font-mono text-xs text-slate-600">{f.typ}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>

                    <div>
                        <Label htmlFor="hero-abfrage">Beispielabfrage (nur lesen)</Label>
                        <textarea id="hero-abfrage" rows={4} value={abfrage} maxLength={5000} onChange={e => setAbfrage(e.target.value)}
                            className="mt-1 w-full rounded-lg border border-slate-200 p-3 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                        <Button variant="outline" className="mt-2" onClick={() => void ausfuehren('abfrage')} disabled={arbeitet !== null || !abfrage.trim()}>
                            {arbeitet === 'abfrage' && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Abfrage ausführen
                        </Button>
                        <p className="mt-1 text-xs text-slate-500">Änderungen an Hero (mutation) sind gesperrt. Die Antwort ist auf 100.000 Zeichen begrenzt. Sie enthält echte Kundendaten, bitte nicht weitergeben.</p>
                        {antwort && (
                            <div className="mt-3" data-testid="hero-antwort">
                                <p role="status" className={`text-sm ${antwort.ok ? 'text-emerald-700' : 'text-rose-700'}`}>{antwort.ok ? 'Antwort von Hero:' : antwort.meldung}</p>
                                {antwort.antwort && <pre className="mt-1 max-h-80 overflow-auto rounded-lg bg-slate-50 p-3 text-xs">{antwort.antwort}</pre>}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </SettingsCard>
    );
}
