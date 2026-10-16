import { useCallback, useEffect, useMemo, useState } from 'react';
import { Factory, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { DecimalInput } from '../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { PageLayout } from '../components/layout/PageLayout';
import { SearchableSelect } from '../components/ui/searchable-select';
import { useToast } from '../components/ui/toast';
import { formatCurrency } from '../components/artikel/formatCurrency';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import {
    EINHEITEN, TRANSPORTARTEN, einheitKurz, ladeFremdleistungen, loescheFremdleistung, speichereFremdleistung,
    type Fremdleistung, type FremdEinheit, type TransportArt,
} from '../features/fremdleistung/fremdleistungApi';

interface LieferantKurz { id: number; lieferantenname: string }

/** Das Formular als Text, damit Zwischenstände wie "1," möglich sind. */
interface Formular {
    lieferantId: string;
    bezeichnung: string;
    einheit: FremdEinheit;
    preis: string;
    mindest: string;
    einmal: string;
    anteil: string;
    charge: string;
    prozent: string;
    transportArt: TransportArt;
    transportBetrag: string;
    transportKm: string;
    fahrten: string;
    sonstigeName: string;
    sonstige: string;
    gueltigAb: string;
    aktiv: boolean;
}

const heute = () => new Date().toISOString().slice(0, 10);
const LEER: Formular = {
    lieferantId: '', bezeichnung: '', einheit: 'KG', preis: '', mindest: '', einmal: '', anteil: '', charge: '', prozent: '', transportArt: 'KEINE', transportBetrag: '',
    transportKm: '', fahrten: '1', sonstigeName: '', sonstige: '', gueltigAb: heute(), aktiv: true,
};
const SELECT = 'min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm';
const text = (n: number | null) => (n == null ? '' : formatDecimalInput(n));

function ausFremdleistung(f: Fremdleistung): Formular {
    return {
        lieferantId: String(f.lieferantId), bezeichnung: f.bezeichnung, einheit: f.einheit, preis: text(f.preisJeEinheit), mindest: text(f.mindestpreis), einmal: text(f.einmalBetrag),
        anteil: text(f.anteilBetrag), charge: text(f.anteilChargenmenge), prozent: text(f.prozentZuschlag), transportArt: f.transportArt, transportBetrag: text(f.transportBetrag),
        transportKm: text(f.transportKm), fahrten: String(f.transportFahrten), sonstigeName: f.sonstigeBezeichnung ?? '', sonstige: text(f.sonstigeBetrag), gueltigAb: f.gueltigAb, aktiv: f.aktiv,
    };
}

/** Eine kurze Zusammenfassung der Kostenarten für die Liste, zum Beispiel "1,20 € je kg · mind. 45,00 € · Transport pauschal 35,00 €". */
function kurztext(f: Fremdleistung): string {
    const teile: string[] = [];
    if (f.preisJeEinheit != null) teile.push(`${formatCurrency(f.preisJeEinheit)} je ${einheitKurz(f.einheit)}`);
    if (f.mindestpreis != null) teile.push(`mind. ${formatCurrency(f.mindestpreis)}`);
    if (f.einmalBetrag != null) teile.push(`einmal ${formatCurrency(f.einmalBetrag)}`);
    if (f.anteilBetrag != null && f.anteilChargenmenge != null) teile.push(`${formatCurrency(f.anteilBetrag)} je Ladung (Charge) à ${formatDecimalInput(f.anteilChargenmenge)} ${einheitKurz(f.einheit)}`);
    if (f.transportArt !== 'KEINE' && f.transportBetrag != null) {
        const art = TRANSPORTARTEN.find(t => t.wert === f.transportArt)?.text.toLowerCase() ?? 'Transport';
        teile.push(`Transport ${art} ${formatCurrency(f.transportBetrag)}`);
    }
    if (f.sonstigeBetrag != null) teile.push(`${f.sonstigeBezeichnung || 'Sonstiges'} ${formatCurrency(f.sonstigeBetrag)}`);
    if (f.prozentZuschlag != null) teile.push(`+ ${formatDecimalInput(f.prozentZuschlag)} %`);
    return teile.join(' · ');
}

/**
 * Die Preisliste der Veredler und Fremdbetriebe: je Veredler (Lieferant) mehrere Fremdleistungen mit Preisstand und den Kostenarten Einheitspreis, Mindestpreis,
 * einmalig, anteilig, Transport, Sonstiges und Prozent. Eine Kostenart, die der Veredler nicht berechnet, bleibt einfach leer.
 */
export default function FremdleistungenPage() {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [liste, setListe] = useState<Fremdleistung[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [dialog, setDialog] = useState<{ id: number | null } | null>(null);
    const [form, setForm] = useState<Formular>(LEER);
    const [meldung, setMeldung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        try {
            setListe(await ladeFremdleistungen());
            setFehler('');
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Fremdleistungen konnten nicht geladen werden.');
        }
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const gruppen = useMemo(() => {
        const nachName = new Map<string, Fremdleistung[]>();
        for (const f of liste ?? []) nachName.set(f.lieferantName, [...(nachName.get(f.lieferantName) ?? []), f]);
        return [...nachName.entries()].sort(([a], [b]) => a.localeCompare(b, 'de'));
    }, [liste]);

    const oeffne = (f: Fremdleistung | null) => {
        setForm(f ? ausFremdleistung(f) : { ...LEER, gueltigAb: heute() });
        setMeldung('');
        setDialog({ id: f?.id ?? null });
    };
    const setze = (teil: Partial<Formular>) => setForm(alt => ({ ...alt, ...teil }));

    const speichere = async () => {
        if (!form.lieferantId) { setMeldung('Bitte den Veredler (Lieferanten) wählen.'); return; }
        if (!form.bezeichnung.trim()) { setMeldung('Bitte angeben, was der Veredler macht, zum Beispiel Feuerverzinken.'); return; }
        const zahl = (t: string, label: string, min = 0, max = 1000000) => validateDecimalInput(t, { label, required: false, min, max });
        const preis = zahl(form.preis, 'Der Preis je Einheit');
        const mindest = zahl(form.mindest, 'Der Mindestpreis');
        const einmal = zahl(form.einmal, 'Der einmalige Betrag');
        const anteil = zahl(form.anteil, 'Der anteilige Betrag');
        const charge = zahl(form.charge, 'Die Chargenmenge', 0.001);
        const prozent = zahl(form.prozent, 'Der Prozentzuschlag', 0, 1000);
        const tBetrag = zahl(form.transportBetrag, 'Der Transportbetrag');
        const tKm = zahl(form.transportKm, 'Die Strecke', 0.01);
        const fahrten = validateDecimalInput(form.fahrten, { label: 'Die Zahl der Fahrten', required: true, min: 1, max: 100, integer: true });
        const sonstige = zahl(form.sonstige, 'Der Betrag der sonstigen Kosten');
        for (const z of [preis, mindest, einmal, anteil, charge, prozent, tBetrag, tKm, fahrten, sonstige]) {
            if (!z.valid) { setMeldung(z.message); return; }
        }
        const werte = (z: { valid: boolean; value?: number | null }) => (z.valid ? z.value ?? null : null);
        setSpeichert(true);
        setMeldung('');
        try {
            await speichereFremdleistung(dialog?.id ?? null, {
                lieferantId: Number(form.lieferantId), bezeichnung: form.bezeichnung.trim(), einheit: form.einheit, preisJeEinheit: werte(preis), mindestpreis: werte(mindest),
                einmalBetrag: werte(einmal), anteilBetrag: werte(anteil), anteilChargenmenge: werte(charge), prozentZuschlag: werte(prozent), transportArt: form.transportArt,
                transportBetrag: form.transportArt === 'KEINE' ? null : werte(tBetrag), transportKm: form.transportArt === 'JE_KM' ? werte(tKm) : null,
                transportFahrten: werte(fahrten) ?? 1, sonstigeBezeichnung: form.sonstigeName.trim() || null, sonstigeBetrag: werte(sonstige), gueltigAb: form.gueltigAb || heute(), aktiv: form.aktiv,
            });
            toast.success('Die Fremdleistung ist gespeichert. Bestehende Kalkulationen zeigen den neuen Preis, sobald Sie dort „Preis übernehmen“ wählen.');
            setDialog(null);
            await laden();
        } catch (e) {
            setMeldung(e instanceof Error ? e.message : 'Die Fremdleistung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const loesche = async (f: Fremdleistung) => {
        if (!await bestaetige({ title: 'Fremdleistung löschen', message: `„${f.bezeichnung}“ bei ${f.lieferantName} wirklich löschen?`, variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await loescheFremdleistung(f.id);
            toast.success('Die Fremdleistung wurde gelöscht.');
            await laden();
        } catch (e) {
            toast.warning(e instanceof Error ? e.message : 'Die Fremdleistung konnte nicht gelöscht werden.');
        }
    };

    const schalte = async (f: Fremdleistung) => {
        try {
            await speichereFremdleistung(f.id, { ...ausEingabe(f), aktiv: !f.aktiv });
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Umschalten hat nicht geklappt.');
        }
    };

    // Die Veredler sind Lieferanten; die Liste ist seitenweise, deshalb wird beim Tippen gesucht
    const sucheLieferant = async (begriff: string) => {
        const res = await fetch(`/api/lieferanten?q=${encodeURIComponent(begriff)}&size=20`);
        if (!res.ok) return [];
        const d = await res.json() as { lieferanten?: LieferantKurz[] };
        return (d.lieferanten ?? []).map(l => ({ value: String(l.id), label: l.lieferantenname }));
    };
    const lieferantName = (liste ?? []).find(f => String(f.lieferantId) === form.lieferantId)?.lieferantName;

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="Fremdleistungen"
            subtitle="Was Veredler und Fremdbetriebe berechnen: Verzinken, Pulverbeschichten, Fräsen … je Veredler mit Preisstand. Die Kalkulation einer Leistung rechnet damit und vergleicht auf Wunsch die Veredler."
            actions={<Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => oeffne(null)}><Plus className="w-4 h-4" /> Neue Fremdleistung</Button>}
        >
            {fehler && <p role="alert" className="mb-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {liste && liste.length === 0 && (
                <Card className="p-8 text-center text-slate-500 border-dashed"><Factory className="w-10 h-10 mx-auto mb-2 text-rose-200" />Noch keine Fremdleistung. Legen Sie zum Beispiel „Feuerverzinken“ bei Ihrer Verzinkerei an.</Card>
            )}
            <div className="space-y-4" data-testid="fremdleistungen">
                {gruppen.map(([veredler, eintraege]) => (
                    <Card key={veredler} className="p-4">
                        <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-slate-900"><Factory className="w-4 h-4 text-rose-600" aria-hidden="true" /> {veredler}</h2>
                        <ul className="divide-y divide-slate-100">
                            {eintraege.map(f => (
                                <li key={f.id} className="flex flex-wrap items-center gap-3 py-2" data-testid={`fremdleistung-${f.id}`}>
                                    <div className="min-w-0 flex-1">
                                        <p className={`font-medium ${f.aktiv ? 'text-slate-900' : 'text-slate-400'}`}>{f.bezeichnung}{!f.aktiv && <span className="ml-2 text-xs font-normal">ausgeschaltet</span>}</p>
                                        <p className="text-xs text-slate-500">{kurztext(f)}</p>
                                        <p className="text-xs text-slate-400">Preisstand {new Date(f.gueltigAb).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })}{f.verwendet > 0 ? ` · in ${f.verwendet} Kalkulationszeile${f.verwendet === 1 ? '' : 'n'}` : ''}</p>
                                    </div>
                                    <Button variant="outline" size="sm" onClick={() => void schalte(f)} aria-label={`${f.bezeichnung} ${f.aktiv ? 'ausschalten' : 'einschalten'}`}>{f.aktiv ? 'Ausschalten' : 'Einschalten'}</Button>
                                    <Button variant="outline" size="sm" onClick={() => oeffne(f)} aria-label={`${f.bezeichnung} bei ${f.lieferantName} bearbeiten`}><Pencil className="w-4 h-4" /></Button>
                                    <Button variant="outline" size="sm" onClick={() => void loesche(f)} aria-label={`${f.bezeichnung} bei ${f.lieferantName} löschen`}><Trash2 className="w-4 h-4" /></Button>
                                </li>
                            ))}
                        </ul>
                    </Card>
                ))}
            </div>

            <Dialog open={dialog != null} onOpenChange={o => { if (!o) setDialog(null); }}>
                <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto" aria-label="Fremdleistung bearbeiten">
                    <DialogHeader>
                        <DialogTitle>{dialog?.id == null ? 'Neue Fremdleistung' : 'Fremdleistung bearbeiten'}</DialogTitle>
                        <DialogDescription>Tragen Sie nur ein, was der Veredler wirklich berechnet. Leere Felder zählen nicht.</DialogDescription>
                    </DialogHeader>
                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="md:col-span-2">
                            <Label>Veredler (Lieferant)</Label>
                            <SearchableSelect value={form.lieferantId} onChange={v => setze({ lieferantId: v })} onSearch={sucheLieferant} selectedLabel={lieferantName} placeholder="Veredler wählen" searchPlaceholder="Lieferant suchen…" />
                        </div>
                        <div>
                            <Label htmlFor="fl-bezeichnung">Was macht der Veredler?</Label>
                            <Input id="fl-bezeichnung" value={form.bezeichnung} onChange={e => setze({ bezeichnung: e.target.value })} placeholder="Feuerverzinken" maxLength={200} />
                            <p className="mt-1 text-xs text-slate-500">Gleiche Bezeichnung bei mehreren Veredlern = Preisvergleich.</p>
                        </div>
                        <div>
                            <Label htmlFor="fl-einheit">Abrechnung</Label>
                            <select id="fl-einheit" className={SELECT} value={form.einheit} onChange={e => setze({ einheit: e.target.value as FremdEinheit })}>
                                {EINHEITEN.map(e => <option key={e.wert} value={e.wert}>{e.text}</option>)}
                            </select>
                        </div>
                        <DecimalInput label={`Preis je ${einheitKurz(form.einheit)} (€)`} value={form.preis} onChange={v => setze({ preis: v })} min={0} max={1000000} />
                        <DecimalInput label="Mindestpreis je Auftrag (€)" value={form.mindest} onChange={v => setze({ mindest: v })} min={0} max={1000000} />
                        <DecimalInput label="Einmalige Kosten je Auftrag (€)" value={form.einmal} onChange={v => setze({ einmal: v })} min={0} max={1000000} />
                        <div />
                        <p className="rounded-lg bg-sky-50 p-3 text-xs text-sky-950 sm:col-span-2" data-testid="charge-hilfe">
                            <b>Ladung (Charge)</b>: Viele Veredler bearbeiten mehrere Aufträge zusammen in <b>einem Durchgang</b> (ein Zinkbad, ein Lkw voll). Was einmal je Ladung kostet (Fahrt, Aufhängen, Anlagenstart),
                            teilt sich auf die Menge der Ladung auf. Beispiel: 90 € je Ladung, eine Ladung fasst 600 kg: Ein Auftrag mit 150 kg bezahlt davon 22,50 €. Rechnet der Veredler nur nach Menge ohne Ladungskosten, lassen Sie beide Felder leer.
                        </p>
                        <DecimalInput label="Kosten je Ladung / Charge (€)" value={form.anteil} onChange={v => setze({ anteil: v })} min={0} max={1000000} />
                        <DecimalInput label={`Eine Ladung fasst (${einheitKurz(form.einheit)})`} value={form.charge} onChange={v => setze({ charge: v })} min={0.001} max={1000000} />
                        <div>
                            <Label htmlFor="fl-transport">Transport</Label>
                            <select id="fl-transport" className={SELECT} value={form.transportArt} onChange={e => setze({ transportArt: e.target.value as TransportArt })}>
                                {TRANSPORTARTEN.map(t => <option key={t.wert} value={t.wert}>{t.text}</option>)}
                            </select>
                        </div>
                        {form.transportArt !== 'KEINE' && (
                            <DecimalInput label={form.transportArt === 'PAUSCHAL' ? 'Transportkosten (€)' : form.transportArt === 'JE_KG' ? 'Euro je kg' : form.transportArt === 'JE_FAHRT' ? 'Euro je Fahrt' : 'Euro je km'}
                                value={form.transportBetrag} onChange={v => setze({ transportBetrag: v })} min={0} max={1000000} />
                        )}
                        {form.transportArt === 'JE_KM' && <DecimalInput label="Strecke einfach (km)" value={form.transportKm} onChange={v => setze({ transportKm: v })} min={0.01} max={1000000} />}
                        {(form.transportArt === 'JE_FAHRT' || form.transportArt === 'JE_KM') && <DecimalInput label="Fahrten je Auftrag" integer value={form.fahrten} onChange={v => setze({ fahrten: v })} min={1} max={100} />}
                        <div>
                            <Label htmlFor="fl-sonst-name">Sonstige Kosten, Bezeichnung</Label>
                            <Input id="fl-sonst-name" value={form.sonstigeName} onChange={e => setze({ sonstigeName: e.target.value })} placeholder="zum Beispiel Entsorgung" maxLength={100} />
                        </div>
                        <DecimalInput label="Sonstige Kosten (€)" value={form.sonstige} onChange={v => setze({ sonstige: v })} min={0} max={1000000} />
                        <DecimalInput label="Zuschlag in Prozent auf alle übrigen Kosten" value={form.prozent} onChange={v => setze({ prozent: v })} min={0} max={1000} />
                        <div>
                            <Label htmlFor="fl-stand">Preisstand (gültig ab)</Label>
                            <Input id="fl-stand" type="date" value={form.gueltigAb} onChange={e => setze({ gueltigAb: e.target.value })} />
                        </div>
                        <label className="flex items-center gap-2 text-sm text-slate-700 md:col-span-2">
                            <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={form.aktiv} onChange={e => setze({ aktiv: e.target.checked })} /> Eingeschaltet (nur dann im Preisvergleich)
                        </label>
                        {meldung && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800 md:col-span-2">{meldung}</p>}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setDialog(null)}>Abbrechen</Button>
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichere()} disabled={speichert}><Save className="w-4 h-4" /> Speichern</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
}

/** Die Eingabe zu einer vorhandenen Fremdleistung (zum Umschalten von „eingeschaltet“, ohne etwas anderes zu ändern). */
function ausEingabe(f: Fremdleistung) {
    return {
        lieferantId: f.lieferantId, bezeichnung: f.bezeichnung, einheit: f.einheit, preisJeEinheit: f.preisJeEinheit, mindestpreis: f.mindestpreis, einmalBetrag: f.einmalBetrag,
        anteilBetrag: f.anteilBetrag, anteilChargenmenge: f.anteilChargenmenge, prozentZuschlag: f.prozentZuschlag, transportArt: f.transportArt, transportBetrag: f.transportBetrag,
        transportKm: f.transportKm, transportFahrten: f.transportFahrten, sonstigeBezeichnung: f.sonstigeBezeichnung, sonstigeBetrag: f.sonstigeBetrag, gueltigAb: f.gueltigAb, aktiv: f.aktiv,
    };
}
