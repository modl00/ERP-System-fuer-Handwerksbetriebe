import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Globe, Loader2, XCircle } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Konfiguration {
    adresse: string;
    tokenGesetzt: boolean;
    adresseAusFirmendaten: boolean;
    eingerichtet: boolean;
}

interface Testergebnis {
    ok: boolean;
    art: string;
    meldung: string;
}

const BASIS = '/api/settings/website-zugang';
const json = { 'Content-Type': 'application/json' };

/**
 * Zugang zur Website für „Neuigkeiten": Adresse und Schlüssel der Beiträge-Schnittstelle, mit Verbindungstest. Der Schlüssel wird gespeichert, aber nie wieder
 * angezeigt. Ohne eigene Adresse gilt die Website aus den Firmendaten.
 */
export function WebsiteZugangKarte() {
    const toast = useToast();
    const [stand, setStand] = useState<Konfiguration | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [adresse, setAdresse] = useState('');
    const [schluessel, setSchluessel] = useState('');
    const [schluesselLoeschen, setSchluesselLoeschen] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [test, setTest] = useState<Testergebnis | 'laeuft' | null>(null);

    const uebernehmen = useCallback((k: Konfiguration) => {
        setStand(k);
        setAdresse(k.adresse);
        setSchluessel('');
        setSchluesselLoeschen(false);
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen der Website konnten nicht geladen werden.'));
                return res.json() as Promise<Konfiguration>;
            })
            .then(k => { if (!abgebrochen) uebernehmen(k); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Einstellungen der Website konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const geaendert = !!stand && (adresse.trim() !== stand.adresse || schluessel !== '' || schluesselLoeschen);
    const kannTesten = !!stand && !geaendert && stand.eingerichtet && test !== 'laeuft';

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(BASIS, {
                method: 'PUT', headers: json,
                // token: null = Schlüssel bleibt, '' = Schlüssel entfernen
                body: JSON.stringify({ adresse: adresse.trim(), token: schluesselLoeschen ? '' : (schluessel !== '' ? schluessel : null) }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen konnten nicht gespeichert werden.'));
            uebernehmen(await res.json() as Konfiguration);
            setTest(null);
            toast.success('Der Zugang zur Website wurde gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const teste = async () => {
        setTest('laeuft');
        try {
            const res = await fetch(`${BASIS}/test`, { method: 'POST', headers: json });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Test ist fehlgeschlagen.'));
            setTest(await res.json() as Testergebnis);
        } catch (e) {
            setTest({ ok: false, art: 'FEHLER', meldung: e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.' });
        }
    };

    if (!stand && !ladefehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Globe className="w-5 h-5 text-rose-600" />}
            title="Website für Neuigkeiten"
            description={
                <p>
                    Hier verbinden Sie die Firmen-Website, auf der die Seite „Neuigkeiten" Beiträge pflegt. Wenn Sie keine passende Website haben, lassen Sie alles leer
                    oder schalten Sie „Neuigkeiten" unter „Module" aus.
                </p>
            }
        >
            {ladefehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>}
            {stand && (
                <div className="space-y-4">
                    <div className="grid gap-3 sm:grid-cols-[10rem_1fr] sm:items-start">
                        <label htmlFor="website-adresse" className="text-sm font-medium text-slate-800 sm:pt-2">Adresse</label>
                        <div className="space-y-1">
                            <Input id="website-adresse" value={adresse} onChange={e => { setAdresse(e.target.value); setTest(null); }}
                                placeholder="https://www.ihre-website.de" spellCheck={false} autoComplete="off" className="font-mono text-sm" />
                            <p className="text-xs text-slate-500">
                                Muss mit https:// beginnen. Leer lassen, dann gilt die Website aus den Firmendaten{stand.adresseAusFirmendaten ? ' (derzeit in Verwendung)' : ''}.
                            </p>
                        </div>

                        <label htmlFor="website-schluessel" className="text-sm font-medium text-slate-800 sm:pt-2">Schlüssel</label>
                        <div className="space-y-1">
                            <Input id="website-schluessel" type="password" value={schluessel} autoComplete="new-password"
                                onChange={e => { setSchluessel(e.target.value); setSchluesselLoeschen(false); setTest(null); }}
                                placeholder={stand.tokenGesetzt && !schluesselLoeschen ? 'Gespeichert, zum Ersetzen neu eingeben' : 'Zugangsschlüssel der Website'}
                                className="font-mono text-sm" />
                            {stand.tokenGesetzt && (
                                <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
                                    <input type="checkbox" checked={schluesselLoeschen}
                                        onChange={e => { setSchluesselLoeschen(e.target.checked); if (e.target.checked) setSchluessel(''); setTest(null); }}
                                        className="h-3.5 w-3.5 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                                    Gespeicherten Schlüssel entfernen
                                </label>
                            )}
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-3">
                        <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>Speichern</SaveButton>
                        <Button variant="outline" onClick={() => void teste()} disabled={!kannTesten}
                            title={geaendert ? 'Zuerst speichern, getestet wird der gespeicherte Stand' : !stand.eingerichtet ? 'Zuerst Adresse und Schlüssel eintragen' : undefined}>
                            {test === 'laeuft' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : null} Verbindung testen
                        </Button>
                        {!geaendert && !stand.eingerichtet && <span className="text-sm text-slate-500">Noch keine Website verbunden.</span>}
                    </div>

                    {test && test !== 'laeuft' && (
                        <div role="status" className={`flex items-start gap-2 text-sm rounded-lg p-3 ${test.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
                            {test.ok ? <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" /> : <XCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />}
                            <span>{test.meldung}</span>
                        </div>
                    )}
                </div>
            )}
        </SettingsCard>
    );
}
