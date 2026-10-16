import { useCallback, useEffect, useState } from 'react';
import { Download, Info, Loader2, ShieldAlert, Trash2 } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useToast } from '../components/ui/toast';
import { ARTEN, OBJEKT_TYPEN, aufbewahrungText, auskunft, datumUndUhrzeit, ereignisText, ladeEinstellungen, ladeImSystem, loescheVon, speichereEinstellungen, type Einstellungen, type Ereignis } from '../features/aktivitaet/aktivitaetApi';

interface Benutzer { id: number; displayName: string; username: string }

const VORGABEN = [{ wert: '30', text: '30 Tage' }, { wert: '90', text: '90 Tage' }, { wert: '365', text: '1 Jahr (Vorgabe)' }, { wert: '0', text: 'unbegrenzt' }];
const ZEITRAEUME = [{ wert: 7, text: 'Letzte 7 Tage' }, { wert: 30, text: 'Letzte 30 Tage' }, { wert: 365, text: 'Letztes Jahr' }];

/**
 * "Aktivitäten im System" (nur Administratoren): Wer hat wann was an einem Objekt gemacht, zur Nachvollziehbarkeit (zum Beispiel "wer hat das Angebot verschickt?").
 * Hier stehen auch die Einstellungen des Tagesverlaufs (für Mitarbeiter, Aufbewahrung) und die Werkzeuge für Auskunft und Löschung auf Wunsch einer Person.
 * Es gibt bewusst keine Auswertung der Stunden einzelner Mitarbeiter.
 */
const AktivitaetenImSystem = () => {
    const toast = useToast();
    const confirm = useConfirm();
    const [einstellungen, setEinstellungen] = useState<Einstellungen | null>(null);
    const [preset, setPreset] = useState('365');
    const [eigene, setEigene] = useState('');
    const [liste, setListe] = useState<Ereignis[] | null>(null);
    const [typ, setTyp] = useState('');
    const [art, setArt] = useState('');
    const [tage, setTage] = useState(30);
    const [fehler, setFehler] = useState('');
    const [benutzer, setBenutzer] = useState<Benutzer[]>([]);
    const [person, setPerson] = useState('');

    const uebernimm = (e: Einstellungen) => {
        setEinstellungen(e);
        const wert = String(e.aufbewahrungTage);
        setPreset(VORGABEN.some((v) => v.wert === wert) ? wert : 'eigene');
        setEigene(VORGABEN.some((v) => v.wert === wert) ? '' : wert);
    };

    useEffect(() => {
        ladeEinstellungen().then(uebernimm).catch((e: unknown) => setFehler(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht geladen werden.'));
        fetch('/api/frontend-users').then((r) => (r.ok ? r.json() : [])).then((l: Benutzer[]) => setBenutzer(Array.isArray(l) ? l : [])).catch(() => setBenutzer([]));
    }, []);

    const laden = useCallback(() => {
        ladeImSystem({ objektTyp: typ || undefined, art: art || undefined, tage })
            .then((l) => { setListe(l); setFehler(''); })
            .catch((e: unknown) => { setListe([]); setFehler(e instanceof Error ? e.message : 'Die Aktivitäten konnten nicht geladen werden.'); });
    }, [typ, art, tage]);
    useEffect(() => { laden(); }, [laden]);

    const mitarbeiterSchalter = async (an: boolean) => {
        if (an && !(await confirm({
            title: 'Tagesverlauf für Mitarbeiter einschalten?',
            message: 'Dann werden auch die Aktionen der Mitarbeiter mit Art, Objekt und Uhrzeit gespeichert. Jeder Mitarbeiter sieht nur seinen eigenen Verlauf, es gibt keine Auswertung gegen einzelne Mitarbeiter. Bitte teilen Sie das Ihren Mitarbeitern vorher mit (gegebenenfalls Betriebsvereinbarung).',
            confirmLabel: 'Einschalten',
            variant: 'info',
        }))) return;
        try { uebernimm(await speichereEinstellungen({ mitarbeiterAktiv: an })); toast.success(an ? 'Der Tagesverlauf gilt jetzt auch für Mitarbeiter.' : 'Der Tagesverlauf ist für Mitarbeiter ausgeschaltet.'); } catch (e) { toast.error(e instanceof Error ? e.message : 'Das hat nicht geklappt.'); }
    };

    const aufbewahrungSpeichern = async () => {
        const wert = preset === 'eigene' ? Number(eigene.trim()) : Number(preset);
        if (!Number.isInteger(wert) || wert < 0 || wert > 36500) { toast.error('Bitte eine ganze Zahl von Tagen eintragen (0 = unbegrenzt).'); return; }
        if (wert === 0 && !(await confirm({
            title: 'Unbegrenzt aufbewahren?',
            message: 'Nach der Datenschutz-Grundverordnung sollen personenbezogene Daten nur so lange gespeichert werden, wie sie gebraucht werden. Möchten Sie den Tagesverlauf trotzdem unbegrenzt aufbewahren?',
            confirmLabel: 'Unbegrenzt aufbewahren',
            variant: 'info',
        }))) return;
        try { uebernimm(await speichereEinstellungen({ aufbewahrungTage: wert })); toast.success('Die Aufbewahrung ist gespeichert.'); } catch (e) { toast.error(e instanceof Error ? e.message : 'Das hat nicht geklappt.'); }
    };

    const gewaehlt = benutzer.find((b) => String(b.id) === person);

    const auskunftHerunterladen = async () => {
        if (!gewaehlt) return;
        try {
            const daten = await auskunft(gewaehlt.id);
            const blob = new Blob([JSON.stringify(daten, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `Tagesverlauf_${gewaehlt.username.replace(/[^A-Za-z0-9._-]/g, '_')}.json`;
            a.click();
            URL.revokeObjectURL(url);
            toast.success(`Auskunft mit ${daten.length} ${daten.length === 1 ? 'Ereignis' : 'Ereignissen'} heruntergeladen.`);
        } catch (e) { toast.error(e instanceof Error ? e.message : 'Die Auskunft konnte nicht erstellt werden.'); }
    };

    const verlaufLoeschen = async () => {
        if (!gewaehlt) return;
        if (!(await confirm({ title: 'Verlauf löschen?', message: `Alle gespeicherten Ereignisse von ${gewaehlt.displayName} werden gelöscht. Das lässt sich nicht rückgängig machen.`, confirmLabel: 'Löschen', variant: 'danger' }))) return;
        try { const r = await loescheVon(gewaehlt.id); toast.success(`${r.geloescht} ${r.geloescht === 1 ? 'Ereignis' : 'Ereignisse'} gelöscht.`); laden(); ladeEinstellungen().then(uebernimm).catch(() => undefined); } catch (e) { toast.error(e instanceof Error ? e.message : 'Das Löschen hat nicht geklappt.'); }
    };

    return (
        <PageLayout ribbonCategory="Administration" title="AKTIVITÄTEN IM SYSTEM" subtitle="Wer hat wann was gemacht? Zur Nachvollziehbarkeit, ohne Inhalte und ohne Stundenauswertung.">
            <div className="space-y-4">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

                <Card className="space-y-4 p-4">
                    <h2 className="text-sm font-semibold text-slate-900">Einstellungen</h2>
                    {!einstellungen ? <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Wird geladen …</p> : (
                        <>
                            <label className="flex items-start gap-3">
                                <input type="checkbox" className="mt-1 h-5 w-5 accent-rose-600" checked={einstellungen.mitarbeiterAktiv} onChange={(e) => void mitarbeiterSchalter(e.target.checked)} />
                                <span>
                                    <span className="block text-sm font-medium text-slate-900">Tagesverlauf auch für Mitarbeiter</span>
                                    <span className="block text-xs text-slate-500">Administratoren werden immer aufgezeichnet. Für Mitarbeiter ist es ausgeschaltet, bis Sie es einschalten.</span>
                                </span>
                            </label>
                            {einstellungen.mitarbeiterAktiv && (
                                <p className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"><ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                                    Diese Ereignisse werden gespeichert, nur der jeweilige Mitarbeiter sieht seinen eigenen Verlauf, es gibt keine Auswertung gegen ihn. Bitte teilen Sie das Ihren Mitarbeitern mit.</p>
                            )}
                            <div className="flex flex-wrap items-end gap-3">
                                <div>
                                    <label htmlFor="aufbewahrung" className="mb-1 block text-sm font-medium text-slate-700">Aufbewahrung</label>
                                    <select id="aufbewahrung" value={preset} onChange={(e) => setPreset(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500">
                                        {VORGABEN.map((v) => <option key={v.wert} value={v.wert}>{v.text}</option>)}
                                        <option value="eigene">eigene Anzahl Tage</option>
                                    </select>
                                </div>
                                {preset === 'eigene' && (
                                    <div>
                                        <label htmlFor="aufbewahrung-eigene" className="mb-1 block text-sm font-medium text-slate-700">Tage</label>
                                        <input id="aufbewahrung-eigene" inputMode="numeric" value={eigene} onChange={(e) => setEigene(e.target.value)} className="w-24 rounded-lg border border-slate-300 px-3 py-2 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                    </div>
                                )}
                                <Button variant="outline" onClick={() => void aufbewahrungSpeichern()}>Speichern</Button>
                                <p className="text-xs text-slate-500">Jetzt: {aufbewahrungText(einstellungen.aufbewahrungTage)}, {einstellungen.ereignisseGesamt} Ereignisse gespeichert. Die Prüfprotokolle der Rechnungen und Belege haben ihre eigenen gesetzlichen Fristen.</p>
                            </div>
                        </>
                    )}
                </Card>

                <div className="flex flex-wrap items-center gap-3">
                    <select aria-label="Objektart" value={typ} onChange={(e) => setTyp(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500">
                        <option value="">Alle Objekte</option>
                        {OBJEKT_TYPEN.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                    <select aria-label="Art" value={art} onChange={(e) => setArt(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500">
                        <option value="">Alle Arten</option>
                        {ARTEN.map((a) => <option key={a.wert} value={a.wert}>{a.text}</option>)}
                    </select>
                    <select aria-label="Zeitraum" value={tage} onChange={(e) => setTage(Number(e.target.value))} className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500">
                        {ZEITRAEUME.map((z) => <option key={z.wert} value={z.wert}>{z.text}</option>)}
                    </select>
                </div>

                {liste === null && <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Die Aktivitäten werden geladen …</p>}
                {liste !== null && liste.length === 0 && !fehler && <Card className="p-6 text-center text-slate-600">Für diese Auswahl gibt es keine Einträge.</Card>}
                {liste !== null && liste.length > 0 && (
                    <Card className="overflow-x-auto p-0">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Wann</th><th className="px-4 py-3">Wer</th><th className="px-4 py-3">Was</th></tr></thead>
                            <tbody className="divide-y divide-slate-100">
                                {liste.map((e) => (
                                    <tr key={e.id}>
                                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{datumUndUhrzeit(e.zeitpunkt)}</td>
                                        <td className="px-4 py-3 text-slate-900">{e.benutzer ?? '–'}</td>
                                        <td className="px-4 py-3 text-slate-800">{ereignisText(e)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </Card>
                )}
                {liste !== null && liste.length >= 200 && <p className="flex items-center gap-2 text-xs text-slate-500"><Info className="h-3.5 w-3.5" /> Es werden die neuesten 200 Einträge gezeigt. Mit den Filtern lässt sich eingrenzen.</p>}

                <Card className="space-y-3 p-4">
                    <h2 className="text-sm font-semibold text-slate-900">Auskunft und Löschung auf Wunsch einer Person</h2>
                    <p className="text-xs text-slate-500">Eine Person darf wissen, was über sie gespeichert ist, und die Löschung verlangen.</p>
                    <div className="flex flex-wrap items-center gap-3">
                        <select aria-label="Person" value={person} onChange={(e) => setPerson(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500">
                            <option value="">Person wählen …</option>
                            {benutzer.map((b) => <option key={b.id} value={b.id}>{b.displayName}</option>)}
                        </select>
                        <Button variant="outline" size="sm" onClick={() => void auskunftHerunterladen()} disabled={!gewaehlt}><Download className="h-4 w-4" /> Auskunft herunterladen</Button>
                        <Button variant="outline" size="sm" onClick={() => void verlaufLoeschen()} disabled={!gewaehlt}><Trash2 className="h-4 w-4" /> Verlauf löschen</Button>
                    </div>
                </Card>
            </div>
        </PageLayout>
    );
};

export default AktivitaetenImSystem;
