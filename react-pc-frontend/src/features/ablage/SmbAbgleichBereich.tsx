import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowUp, CheckCircle2, Eye, Folder, Loader2, Network, PlugZap, RefreshCw } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { SettingsCard } from '../../components/settings/settingsUi';
import { useToast } from '../../components/ui/toast';
import { gleicheAb, ladeFreigaben, ladeOrdner, ladeSmb, speichereSmb, testeSmb, type SmbAbgleichErgebnis, type SmbEingabe } from './ablageApi';

const LEER: SmbEingabe = { aktiv: false, server: '', freigabe: '', unterordner: '', benutzer: '', domaene: '', passwort: '', passwortLoeschen: false, intervallMinuten: 0 };

const ABSTAENDE = [{ wert: 0, text: 'Nur auf Knopfdruck' }, { wert: 5, text: 'Alle 5 Minuten' }, { wert: 15, text: 'Alle 15 Minuten' }, { wert: 60, text: 'Jede Stunde' }];

function zeitText(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

/**
 * Einstellungen, Dateien: Abgleich mit einer Netzwerkfreigabe (SMB). Das Programm legt dort eine lesbare Ordnerstruktur an und gleicht in beide
 * Richtungen ab. Die Ablage des Programms bleibt unverändert, es wird auf keiner Seite etwas gelöscht. Benutzer und Passwort dürfen leer bleiben (Gastzugang).
 */
export function SmbAbgleichBereich() {
    const toast = useToast();
    const [eingabe, setEingabe] = useState<SmbEingabe>(LEER);
    const [passwortGesetzt, setPasswortGesetzt] = useState(false);
    const [verschluesselt, setVerschluesselt] = useState(false);
    const [letzter, setLetzter] = useState<{ zeit: string; text: string } | null>(null);
    const [laedt, setLaedt] = useState(true);
    const [speichert, setSpeichert] = useState(false);
    const [verbindet, setVerbindet] = useState(false);
    const [freigaben, setFreigaben] = useState<string[] | null>(null);
    const [verbindungsFehler, setVerbindungsFehler] = useState('');
    const [ordner, setOrdner] = useState<string[]>([]);
    const [ordnerLaedt, setOrdnerLaedt] = useState(false);
    const [ordnerFehler, setOrdnerFehler] = useState('');
    const [neuerOrdner, setNeuerOrdner] = useState('');
    const [testErgebnis, setTestErgebnis] = useState<{ success: boolean; message: string } | null>(null);
    const [laeuft, setLaeuft] = useState<'vorschau' | 'abgleich' | null>(null);
    const [ergebnis, setErgebnis] = useState<SmbAbgleichErgebnis | null>(null);

    useEffect(() => {
        let abgebrochen = false;
        ladeSmb()
            .then(s => {
                if (abgebrochen) return;
                setEingabe({ aktiv: s.aktiv === true, server: s.server ?? '', freigabe: s.freigabe ?? '', unterordner: s.unterordner ?? '', benutzer: s.benutzer ?? '', domaene: s.domaene ?? '', passwort: '', passwortLoeschen: false, intervallMinuten: s.intervallMinuten ?? 0 });
                setLetzter(s.letzterLauf ? { zeit: s.letzterLauf, text: s.letztesErgebnis ?? '' } : null);
                setPasswortGesetzt(s.passwortGesetzt === true);
                setVerschluesselt(s.verschluesselt !== false);
            })
            .catch(e => { if (!abgebrochen) toast.error(e instanceof Error ? e.message : 'Die Einstellungen der Netzwerkfreigabe konnten nicht geladen werden.'); })
            .finally(() => { if (!abgebrochen) setLaedt(false); });
        return () => { abgebrochen = true; };
    }, [toast]);

    const aendere = (teil: Partial<SmbEingabe>) => { setEingabe(e => ({ ...e, ...teil })); setTestErgebnis(null); };

    const speichere = async () => {
        setSpeichert(true);
        try {
            const ziel = zielPfad();
            const zuSpeichern = { ...eingabe, unterordner: ziel };
            await speichereSmb(zuSpeichern);
            setEingabe(zuSpeichern);
            setNeuerOrdner('');
            toast.success('Gespeichert.');
            try {
                setTestErgebnis(await testeSmb({ ...zuSpeichern, passwort: '' }));
            } catch (e) {
                setTestErgebnis({ success: false, message: e instanceof Error ? e.message : 'Der Test konnte nicht ausgeführt werden.' });
            }
            if (eingabe.passwortLoeschen) setPasswortGesetzt(false);
            else if (eingabe.passwort) setPasswortGesetzt(true);
            setEingabe(e => ({ ...e, passwort: '', passwortLoeschen: false }));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Speichern hat nicht geklappt.');
        } finally {
            setSpeichert(false);
        }
    };

    const zeigeOrdner = async (freigabe: string, pfad: string) => {
        setOrdnerLaedt(true);
        setOrdnerFehler('');
        try {
            const r = await ladeOrdner(eingabe, freigabe, pfad);
            if (r.success) setOrdner(r.ordner ?? []);
            else { setOrdner([]); setOrdnerFehler(r.message ?? 'Die Ordner konnten nicht geladen werden.'); }
        } catch (e) {
            setOrdner([]);
            setOrdnerFehler(e instanceof Error ? e.message : 'Die Ordner konnten nicht geladen werden.');
        } finally {
            setOrdnerLaedt(false);
        }
    };

    const verbinde = async () => {
        setVerbindet(true);
        setVerbindungsFehler('');
        setTestErgebnis(null);
        try {
            const r = await ladeFreigaben(eingabe);
            if (r.success) {
                const liste = r.freigaben ?? [];
                setFreigaben(liste);
                if (liste.length === 0) setVerbindungsFehler('Das Gerät hat keine Freigaben gemeldet. Tragen Sie den Namen der Freigabe unten von Hand ein.');
                if (eingabe.freigabe && liste.includes(eingabe.freigabe)) await zeigeOrdner(eingabe.freigabe, eingabe.unterordner);
            } else {
                setFreigaben(null);
                setVerbindungsFehler(r.message ?? 'Die Anmeldung hat nicht geklappt.');
            }
        } catch (e) {
            setFreigaben(null);
            setVerbindungsFehler(e instanceof Error ? e.message : 'Die Anmeldung hat nicht geklappt.');
        } finally {
            setVerbindet(false);
        }
    };

    const waehleFreigabe = (name: string) => {
        setEingabe(e => ({ ...e, freigabe: name, unterordner: '' }));
        setNeuerOrdner('');
        setTestErgebnis(null);
        if (name) void zeigeOrdner(name, '');
        else setOrdner([]);
    };

    const geheIn = (name: string) => {
        const neu = eingabe.unterordner ? `${eingabe.unterordner}/${name}` : name;
        setEingabe(e => ({ ...e, unterordner: neu }));
        setNeuerOrdner('');
        setTestErgebnis(null);
        void zeigeOrdner(eingabe.freigabe, neu);
    };

    const geheHoch = () => {
        const teile = eingabe.unterordner.split('/').filter(Boolean);
        teile.pop();
        const neu = teile.join('/');
        setEingabe(e => ({ ...e, unterordner: neu }));
        setNeuerOrdner('');
        setTestErgebnis(null);
        void zeigeOrdner(eingabe.freigabe, neu);
    };

    const starte = async (probelauf: boolean) => {
        setLaeuft(probelauf ? 'vorschau' : 'abgleich');
        try {
            const e = await gleicheAb(probelauf);
            setErgebnis(e);
            if (!probelauf) {
                toast.success('Abgleich abgeschlossen.');
                ladeSmb().then(st => setLetzter(st.letzterLauf ? { zeit: st.letzterLauf, text: st.letztesErgebnis ?? '' } : null)).catch(() => undefined);
            }
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das hat nicht geklappt.');
        } finally {
            setLaeuft(null);
        }
    };

    const zielPfad = () => [eingabe.unterordner, neuerOrdner.trim()].filter(Boolean).join('/');
    const ausgefuellt = eingabe.server.trim() !== '' && eingabe.freigabe.trim() !== '';
    const ziel = ausgefuellt ? `smb://${eingabe.server.trim()}/${eingabe.freigabe}${zielPfad() ? '/' + zielPfad() : ''}` : '';

    return (
        <SettingsCard
            className="mt-6"
            icon={<Network className="w-5 h-5 text-rose-600" />}
            title="Netzwerkordner abgleichen (Test)"
            description={
                <>
                    <p>
                        Das Programm legt auf einer Netzwerkfreigabe (NAS, Server, anderer Rechner) eine <strong>übersichtliche Ordnerstruktur</strong> an
                        (Projekte, Jahr, Projektname, Dokumentgruppe) und gleicht sie <strong>in beide Richtungen</strong> ab.
                    </p>
                    <p>
                        Neue und geänderte Dokumente gehen auf die Freigabe. Neue Dateien, die Sie dort in einen Projektordner legen, übernimmt das Programm ins Projekt.
                        <strong> Gelöscht wird auf keiner Seite etwas.</strong> Ändern beide Seiten dieselbe Datei, wird die Fassung von der Freigabe als eigenes Dokument gesichert.
                        Ihre bisherige Ablage bleibt unverändert. Das ist ein Test: bitte zuerst mit der Vorschau prüfen.
                    </p>
                </>
            }
        >
            {laedt ? (
                <div className="flex items-center gap-2 text-sm text-slate-500" role="status"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Wird geladen …</div>
            ) : (
                <div className="space-y-4">
                    <ol className="list-decimal pl-5 text-sm text-slate-600 space-y-0.5">
                        <li>Gerät, Benutzer und Passwort eintragen und auf <strong>Verbinden</strong> klicken.</li>
                        <li>Freigabe wählen und den Ordner anklicken, in dem alles abgelegt werden soll.</li>
                        <li>Speichern und den Abgleich einschalten.</li>
                    </ol>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="space-y-1">
                            <Label htmlFor="smb-server">Gerät (Name oder IP-Adresse)</Label>
                            <Input id="smb-server" value={eingabe.server} placeholder="192.168.178.21" onChange={e => { aendere({ server: e.target.value }); setFreigaben(null); }} />
                        </div>
                        <div className="space-y-1">
                            <Label htmlFor="smb-benutzer">Benutzer (leer = Gastzugang)</Label>
                            <Input id="smb-benutzer" value={eingabe.benutzer} autoComplete="off" onChange={e => { aendere({ benutzer: e.target.value }); setFreigaben(null); }} />
                        </div>
                        <div className="space-y-1">
                            <Label htmlFor="smb-passwort">Passwort</Label>
                            <Input id="smb-passwort" type="password" value={eingabe.passwort} autoComplete="new-password"
                                placeholder={passwortGesetzt ? 'gespeichert, leer lassen zum Behalten' : ''}
                                onChange={e => { aendere({ passwort: e.target.value, passwortLoeschen: false }); setFreigaben(null); }} />
                        </div>
                    </div>
                    {passwortGesetzt && (
                        <label className="flex items-center gap-2 text-xs text-slate-600">
                            <input type="checkbox" checked={eingabe.passwortLoeschen} onChange={e => aendere({ passwortLoeschen: e.target.checked, passwort: '' })} />
                            Gespeichertes Passwort entfernen
                        </label>
                    )}
                    {!verschluesselt && passwortGesetzt && (
                        <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-md p-2">
                            Das Passwort liegt unverschlüsselt in den Einstellungen, weil kein Verschlüsselungsschlüssel (mail.credentials.encryption-key) eingerichtet ist.
                        </p>
                    )}
                    <details className="text-sm">
                        <summary className="cursor-pointer text-slate-600">Erweitert: Domäne</summary>
                        <div className="mt-2 max-w-xs space-y-1">
                            <Label htmlFor="smb-domaene">Domäne (meist leer)</Label>
                            <Input id="smb-domaene" value={eingabe.domaene} autoComplete="off" onChange={e => aendere({ domaene: e.target.value })} />
                        </div>
                    </details>

                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={verbinde} disabled={eingabe.server.trim() === '' || verbindet}>
                        {verbindet ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <PlugZap className="w-4 h-4" />} Verbinden
                    </Button>

                    {verbindungsFehler && (
                        <p role="alert" className="text-sm rounded-md border p-2 flex items-start gap-2 text-rose-800 bg-rose-50 border-rose-200">
                            <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" /> {verbindungsFehler}
                        </p>
                    )}

                    {(freigaben != null || verbindungsFehler !== '' || eingabe.freigabe !== '') && (
                        <div className="rounded-lg border border-slate-200 p-4 space-y-3">
                            {freigaben != null && freigaben.length > 0 ? (
                                <div className="space-y-1 max-w-sm">
                                    <Label htmlFor="smb-freigabe-wahl">Freigabe</Label>
                                    <select id="smb-freigabe-wahl" className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" value={eingabe.freigabe}
                                        onChange={e => waehleFreigabe(e.target.value)}>
                                        <option value="">Bitte wählen</option>
                                        {freigaben.map(f => <option key={f} value={f}>{f}</option>)}
                                    </select>
                                </div>
                            ) : (
                                <div className="space-y-1 max-w-sm">
                                    <Label htmlFor="smb-freigabe">Name der Freigabe (von Hand)</Label>
                                    <div className="flex gap-2">
                                        <Input id="smb-freigabe" value={eingabe.freigabe} placeholder="Unternehmen" onChange={e => aendere({ freigabe: e.target.value, unterordner: '' })} />
                                        <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" disabled={eingabe.freigabe.trim() === ''}
                                            onClick={() => void zeigeOrdner(eingabe.freigabe, '')}>Ordner anzeigen</Button>
                                    </div>
                                </div>
                            )}

                            {eingabe.freigabe !== '' && (
                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2 text-sm">
                                        <Button variant="outline" size="sm" className="border-slate-300" onClick={geheHoch} disabled={eingabe.unterordner === '' || ordnerLaedt} aria-label="Eine Ebene höher">
                                            <ArrowUp className="w-4 h-4" /> Eine Ebene höher
                                        </Button>
                                        <span className="font-mono text-slate-800 break-all" data-testid="smb-pfad">{eingabe.freigabe}{eingabe.unterordner ? ` / ${eingabe.unterordner.split('/').join(' / ')}` : ''}</span>
                                    </div>
                                    {ordnerLaedt ? (
                                        <p className="text-sm text-slate-500 flex items-center gap-2" role="status"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Ordner werden geladen …</p>
                                    ) : ordnerFehler ? (
                                        <p role="alert" className="text-sm text-rose-800">{ordnerFehler}</p>
                                    ) : ordner.length === 0 ? (
                                        <p className="text-sm text-slate-500">Hier gibt es keine weiteren Ordner. Sie können unten einen neuen anlegen lassen.</p>
                                    ) : (
                                        <ul className="grid grid-cols-1 sm:grid-cols-2 gap-1 max-h-56 overflow-y-auto" aria-label="Ordner">
                                            {ordner.map(o => (
                                                <li key={o}>
                                                    <button type="button" onClick={() => geheIn(o)} className="w-full flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-slate-800 hover:bg-rose-50">
                                                        <Folder className="w-4 h-4 text-slate-400" aria-hidden="true" /> {o}
                                                    </button>
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                    <div className="space-y-1 max-w-sm">
                                        <Label htmlFor="smb-neu">Neuer Ordner hier (optional, wird angelegt)</Label>
                                        <Input id="smb-neu" value={neuerOrdner} placeholder="ERP" onChange={e => { setNeuerOrdner(e.target.value); setTestErgebnis(null); }} />
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {ziel !== '' && (
                        <p className="text-sm text-slate-700">Das Programm legt die Ordner hier an: <strong className="font-mono break-all" data-testid="smb-ziel">{ziel}</strong></p>
                    )}

                    <label className="flex items-center gap-2 text-sm text-slate-800">
                        <input type="checkbox" checked={eingabe.aktiv} onChange={e => aendere({ aktiv: e.target.checked })} />
                        Abgleich einschalten
                    </label>

                    <div className="space-y-1 max-w-xs">
                        <Label htmlFor="smb-intervall">Automatisch abgleichen</Label>
                        <select id="smb-intervall" className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm" value={eingabe.intervallMinuten}
                            onChange={e => aendere({ intervallMinuten: Number(e.target.value) })}>
                            {ABSTAENDE.map(a => <option key={a.wert} value={a.wert}>{a.text}</option>)}
                        </select>
                    </div>

                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichere} disabled={!ausgefuellt || speichert}>
                        {speichert && <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" />} Speichern und Zugriff prüfen
                    </Button>

                    {testErgebnis && (
                        <p role={testErgebnis.success ? 'status' : 'alert'}
                            className={`text-sm rounded-md border p-2 flex items-start gap-2 ${testErgebnis.success ? 'text-emerald-800 bg-emerald-50 border-emerald-200' : 'text-rose-800 bg-rose-50 border-rose-200'}`}>
                            {testErgebnis.success ? <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" /> : <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />}
                            {testErgebnis.message}
                        </p>
                    )}

                    <div className="border-t border-slate-200 pt-4 space-y-3">
                        {letzter && (
                            <p className="text-sm text-slate-700" data-testid="smb-letzter">Letzter Abgleich: <strong>{zeitText(letzter.zeit)}</strong>{letzter.text ? ` (${letzter.text})` : ''}</p>
                        )}
                        <p className="text-sm text-slate-600">Der Abgleich nutzt die <strong>gespeicherten</strong> Angaben. Bitte zuerst speichern und „Abgleich einschalten" wählen.</p>
                        <div className="flex flex-wrap gap-2">
                            <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => starte(true)} disabled={laeuft != null}>
                                {laeuft === 'vorschau' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Eye className="w-4 h-4" />} Vorschau
                            </Button>
                            <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => starte(false)} disabled={laeuft != null}>
                                {laeuft === 'abgleich' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <RefreshCw className="w-4 h-4" />} Jetzt abgleichen
                            </Button>
                        </div>
                    </div>

                    {ergebnis && (
                        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm space-y-2" data-testid="smb-ergebnis" role="status">
                            <p className="font-medium text-slate-900 flex items-center gap-2">
                                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                                {ergebnis.probelauf ? 'Vorschau (es wurde nichts geändert)' : 'Abgeglichen'}
                            </p>
                            <ul className="text-slate-700 grid grid-cols-2 sm:grid-cols-3 gap-x-4">
                                <li>auf die Freigabe: <strong>{ergebnis.hochgeladen}</strong></li>
                                <li>neu vom Gerät: <strong>{ergebnis.neuVomGeraet}</strong></li>
                                <li>vom Gerät übernommen: <strong>{ergebnis.vomGeraetUebernommen}</strong></li>
                                <li>Konflikte: <strong>{ergebnis.konflikte}</strong></li>
                                <li>unverändert: <strong>{ergebnis.unveraendert}</strong></li>
                            </ul>
                            {ergebnis.hinweise.length > 0 && (
                                <details>
                                    <summary className="cursor-pointer text-slate-600">{ergebnis.hinweise.length} Hinweise</summary>
                                    <ul className="mt-1 list-disc pl-5 text-xs text-slate-700 space-y-0.5">
                                        {ergebnis.hinweise.map(h => <li key={h} className="break-all">{h}</li>)}
                                    </ul>
                                </details>
                            )}
                            {ergebnis.fehler.length > 0 && (
                                <div role="alert" className="text-rose-800">
                                    <p className="font-medium">{ergebnis.fehler.length} Probleme:</p>
                                    <ul className="list-disc pl-5 text-xs space-y-0.5">
                                        {ergebnis.fehler.slice(0, 5).map(f => <li key={f} className="break-all">{f}</li>)}
                                    </ul>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}
        </SettingsCard>
    );
}
