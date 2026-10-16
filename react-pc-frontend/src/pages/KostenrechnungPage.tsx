import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Calculator, Cog, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { KostensaetzeTab } from '../features/kostenrechnung/KostensaetzeTab';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { DecimalInput } from '../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { formatCurrency } from '../components/artikel/formatCurrency';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import {
    ladeArbeitsgaenge, ladeMaschinen, ladeVerlauf, loescheMaschine, rechneMaschine, speichereMaschine,
    type Aufstellung, type ArbeitsgangAuswahl, type Maschine, type Verlaufseintrag,
} from '../features/kostenrechnung/maschinenApi';

/** Das Formular als Text, damit Zwischenstände wie "1," möglich sind. */
interface Formular {
    name: string;
    anschaffung: string;
    rest: string;
    jahre: string;
    zins: string;
    wartung: string;
    platz: string;
    versicherung: string;
    kw: string;
    strom: string;
    auslastung: string;
    stunden: string;
    handAn: boolean;
    hand: string;
    aktiv: boolean;
    bemerkung: string;
    arbeitsgangIds: number[];
}

const LEER: Formular = {
    name: '', anschaffung: '', rest: '', jahre: '', zins: '', wartung: '', platz: '', versicherung: '', kw: '', strom: '', auslastung: '', stunden: '', handAn: false, hand: '',
    aktiv: true, bemerkung: '', arbeitsgangIds: [],
};
const text = (n: number | null) => (n == null ? '' : formatDecimalInput(n));

function ausMaschine(m: Maschine): Formular {
    return {
        name: m.name, anschaffung: text(m.anschaffungswert), rest: text(m.restwert), jahre: text(m.nutzungsdauerJahre), zins: text(m.zinssatzProzent), wartung: text(m.wartungJeJahr),
        platz: text(m.platzJeJahr), versicherung: text(m.versicherungJeJahr), kw: text(m.leistungKw), strom: text(m.strompreisJeKwh), auslastung: text(m.auslastungProzent),
        stunden: text(m.laufstundenJeJahr), handAn: m.satzVonHand != null, hand: text(m.satzVonHand), aktiv: m.aktiv, bemerkung: m.bemerkung ?? '', arbeitsgangIds: m.arbeitsgaenge.map(a => a.id),
    };
}

/** Wandelt einen Entwurf in eine Zahl; ungültig oder leer ergibt {@code null} (für die Vorschau, die Fehler zeigt erst das Speichern). */
function zahlOderNull(entwurf: string): number | null {
    const r = validateDecimalInput(entwurf, { label: 'Wert', required: false, min: 0 });
    return r.valid ? r.value ?? null : null;
}

const euro = (n: number | null) => (n == null ? '–' : formatCurrency(n));

/**
 * Kostenrechnung (Konzept #166). Heute der Bereich „Maschinen“: Was kostet eine Maschinenstunde? Der Rechner erklärt den Satz Schritt für Schritt, er lässt sich auch von Hand
 * festlegen; die Arbeitsgänge, die die Maschine nutzen, rechnen in der Kalkulation damit. Personal, Gemeinkosten und Verrechnungssätze folgen.
 */
export default function KostenrechnungPage() {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [liste, setListe] = useState<Maschine[] | null>(null);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<ArbeitsgangAuswahl[]>([]);
    const [fehler, setFehler] = useState('');
    const [bereich, setBereich] = useState<'maschinen' | 'kostensaetze'>('maschinen');
    const [dialog, setDialog] = useState<{ id: number | null } | null>(null);
    const [form, setForm] = useState<Formular>(LEER);
    const [meldung, setMeldung] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [vorschau, setVorschau] = useState<Aufstellung | null>(null);
    const [verlauf, setVerlauf] = useState<Verlaufseintrag[] | null>(null);
    const vorschauNr = useRef(0);

    const laden = useCallback(async () => {
        try {
            const [m, a] = await Promise.all([ladeMaschinen(), ladeArbeitsgaenge()]);
            setListe(m);
            setArbeitsgaenge(a);
            setFehler('');
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Maschinen konnten nicht geladen werden.');
        }
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const setze = (teil: Partial<Formular>) => setForm(alt => ({ ...alt, ...teil }));

    // Die Vorschau rechnet der Server (eine Rechenvorschrift für Vorschau und Speichern), kurz nach dem Tippen
    useEffect(() => {
        if (!dialog) return;
        const nr = ++vorschauNr.current;
        const t = window.setTimeout(() => {
            rechneMaschine({
                anschaffungswert: zahlOderNull(form.anschaffung), restwert: zahlOderNull(form.rest), nutzungsdauerJahre: zahlOderNull(form.jahre), zinssatzProzent: zahlOderNull(form.zins),
                wartungJeJahr: zahlOderNull(form.wartung), platzJeJahr: zahlOderNull(form.platz), versicherungJeJahr: zahlOderNull(form.versicherung), leistungKw: zahlOderNull(form.kw),
                strompreisJeKwh: zahlOderNull(form.strom), auslastungProzent: zahlOderNull(form.auslastung), laufstundenJeJahr: zahlOderNull(form.stunden),
            }).then(a => { if (nr === vorschauNr.current) setVorschau(a); }).catch(() => { if (nr === vorschauNr.current) setVorschau(null); });
        }, 350);
        return () => window.clearTimeout(t);
    }, [dialog, form.anschaffung, form.rest, form.jahre, form.zins, form.wartung, form.platz, form.versicherung, form.kw, form.strom, form.auslastung, form.stunden]);

    const oeffne = (m: Maschine | null) => {
        setForm(m ? ausMaschine(m) : LEER);
        setVorschau(m ? m.aufstellung : null);
        setVerlauf(null);
        setMeldung('');
        setDialog({ id: m?.id ?? null });
        if (m) ladeVerlauf(m.id).then(setVerlauf).catch(() => setVerlauf([]));
    };

    const speichere = async () => {
        if (!form.name.trim()) { setMeldung('Bitte einen Namen eintragen, zum Beispiel „Kappsäge“.'); return; }
        const zahl = (t: string, label: string, max: number, min = 0) => validateDecimalInput(t, { label, required: false, min, max });
        const pruefungen = [
            zahl(form.anschaffung, 'Der Anschaffungswert', 100000000), zahl(form.rest, 'Der Restwert', 100000000), zahl(form.jahre, 'Die Nutzungsdauer', 100),
            zahl(form.zins, 'Der Zinssatz', 100), zahl(form.wartung, 'Die Wartung', 100000000), zahl(form.platz, 'Die Platzkosten', 100000000),
            zahl(form.versicherung, 'Die Versicherung', 100000000), zahl(form.kw, 'Die Leistung', 100000), zahl(form.strom, 'Der Strompreis', 100),
            zahl(form.auslastung, 'Die Auslastung', 100), zahl(form.stunden, 'Die Laufstunden', 8784),
        ];
        for (const p of pruefungen) {
            if (!p.valid) { setMeldung(p.message); return; }
        }
        let hand: number | null = null;
        if (form.handAn) {
            const h = validateDecimalInput(form.hand, { label: 'Der Satz je Stunde', required: true, min: 0.01, max: 1000 });
            if (!h.valid) { setMeldung(h.message); return; }
            hand = h.value ?? null;
        }
        const [anschaffung, rest, jahre, zins, wartung, platz, versicherung, kw, strom, auslastung, stunden] = pruefungen.map(p => (p.valid ? p.value ?? null : null));
        setSpeichert(true);
        setMeldung('');
        try {
            await speichereMaschine(dialog?.id ?? null, {
                name: form.name.trim(), anschaffungswert: anschaffung, restwert: rest, nutzungsdauerJahre: jahre, zinssatzProzent: zins, wartungJeJahr: wartung, platzJeJahr: platz,
                versicherungJeJahr: versicherung, leistungKw: kw, strompreisJeKwh: strom, auslastungProzent: auslastung, laufstundenJeJahr: stunden, satzVonHand: hand,
                aktiv: form.aktiv, bemerkung: form.bemerkung.trim() || null, arbeitsgangIds: form.arbeitsgangIds,
            });
            toast.success('Die Maschine ist gespeichert. Leistungen zeigen den neuen Satz, sobald Sie dort „Preis übernehmen“ wählen.');
            setDialog(null);
            await laden();
        } catch (e) {
            setMeldung(e instanceof Error ? e.message : 'Die Maschine konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const loesche = async (m: Maschine) => {
        if (!await bestaetige({ title: 'Maschine löschen', message: `„${m.name}“ wirklich löschen?`, variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await loescheMaschine(m.id);
            toast.success('Die Maschine wurde gelöscht.');
            await laden();
        } catch (e) {
            toast.warning(e instanceof Error ? e.message : 'Die Maschine konnte nicht gelöscht werden.');
        }
    };

    const maschinenName = useMemo(() => new Map((liste ?? []).map(m => [m.id, m.name])), [liste]);
    const umschalten = (id: number, an: boolean) => setForm(alt => ({ ...alt, arbeitsgangIds: an ? [...new Set([...alt.arbeitsgangIds, id])] : alt.arbeitsgangIds.filter(x => x !== id) }));

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="Kostenrechnung"
            subtitle="Was kostet mich eine Stunde, und was verlange ich? Kostensätze und Verrechnungssätze mit sichtbarer Herleitung, dazu die Maschinenliste. Die Sätze der Arbeitsgänge und Angebote ändern sich dadurch nicht."
            actions={bereich === 'maschinen' ? <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => oeffne(null)}><Plus className="w-4 h-4" /> Neue Maschine</Button> : undefined}
        >
            <div role="tablist" aria-label="Bereiche der Kostenrechnung" className="mb-4 flex gap-1 border-b border-slate-200">
                {([['maschinen', 'Maschinen'], ['kostensaetze', 'Kostensätze']] as const).map(([id, label]) => (
                    <button key={id} type="button" role="tab" aria-selected={bereich === id} onClick={() => setBereich(id)}
                        className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${bereich === id ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>{label}</button>
                ))}
            </div>

            {bereich === 'kostensaetze' && <KostensaetzeTab />}
            {bereich === 'maschinen' && <>

            {fehler && <p role="alert" className="mb-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {liste && liste.length === 0 && (
                <Card className="p-8 text-center text-slate-500 border-dashed"><Cog className="w-10 h-10 mx-auto mb-2 text-rose-200" />Noch keine Maschine. Legen Sie zum Beispiel Ihre Kappsäge oder Schweißanlage an; der Rechner zeigt, was eine Stunde kostet.</Card>
            )}
            <div className="space-y-3" data-testid="maschinen">
                {(liste ?? []).map(m => (
                    <Card key={m.id} className="p-4" data-testid={`maschine-${m.id}`}>
                        <div className="flex flex-wrap items-start gap-3">
                            <div className="min-w-0 flex-1">
                                <p className={`flex items-center gap-2 font-semibold ${m.aktiv ? 'text-slate-900' : 'text-slate-400'}`}>
                                    <Cog className="w-4 h-4 text-rose-600" aria-hidden="true" /> {m.name}
                                    {!m.aktiv && <span className="text-xs font-normal">ausgeblendet</span>}
                                </p>
                                <p className="mt-1 text-sm text-slate-700">
                                    <strong>{euro(m.kostensatz)}</strong> je Stunde
                                    <span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${m.vonHand ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'}`}>{m.vonHand ? 'von Hand festgelegt' : 'errechnet'}</span>
                                    {m.vonHand && m.aufstellung.summe != null && <span className="ml-2 text-xs text-slate-500">(Rechner: {euro(m.aufstellung.summe)})</span>}
                                </p>
                                <p className="mt-1 text-xs text-slate-500">
                                    {m.arbeitsgaenge.length === 0 ? 'Noch keinem Arbeitsgang zugeordnet' : `Genutzt von: ${m.arbeitsgaenge.map(a => a.beschreibung).join(', ')}`}
                                </p>
                            </div>
                            <Button variant="outline" size="sm" onClick={() => oeffne(m)} aria-label={`${m.name} bearbeiten`}><Pencil className="w-4 h-4" /></Button>
                            <Button variant="outline" size="sm" onClick={() => void loesche(m)} aria-label={`${m.name} löschen`}><Trash2 className="w-4 h-4" /></Button>
                        </div>
                    </Card>
                ))}
            </div>

            </>}

            <Dialog open={dialog != null} onOpenChange={o => { if (!o) setDialog(null); }}>
                <DialogContent className="sm:max-w-[680px] max-h-[90vh] overflow-y-auto" aria-label="Maschine bearbeiten">
                    <DialogHeader>
                        <DialogTitle>{dialog?.id == null ? 'Neue Maschine' : 'Maschine bearbeiten'}</DialogTitle>
                        <DialogDescription>Tragen Sie ein, was Sie wissen. Fehlende Angaben zählen als 0. Der Satz je Stunde erscheint unten.</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4">
                        <div>
                            <label htmlFor="m-name" className="mb-1 block text-sm font-medium text-slate-700">Name der Maschine</label>
                            <Input id="m-name" value={form.name} onChange={e => setze({ name: e.target.value })} maxLength={100} placeholder="Kappsäge" />
                        </div>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-semibold text-slate-800">Anschaffung</legend>
                            <div className="grid gap-3 sm:grid-cols-2">
                                <DecimalInput label="Anschaffungswert (€)" value={form.anschaffung} onChange={v => setze({ anschaffung: v })} min={0} max={100000000} />
                                <DecimalInput label="Restwert am Ende (€)" value={form.rest} onChange={v => setze({ rest: v })} min={0} max={100000000} />
                                <DecimalInput label="Nutzungsdauer (Jahre)" value={form.jahre} onChange={v => setze({ jahre: v })} min={0} max={100} />
                                <DecimalInput label="Zinssatz (%)" value={form.zins} onChange={v => setze({ zins: v })} min={0} max={100} />
                            </div>
                        </fieldset>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-semibold text-slate-800">Laufende Kosten je Jahr</legend>
                            <div className="grid gap-3 sm:grid-cols-3">
                                <DecimalInput label="Wartung (€)" value={form.wartung} onChange={v => setze({ wartung: v })} min={0} max={100000000} />
                                <DecimalInput label="Platz (€)" value={form.platz} onChange={v => setze({ platz: v })} min={0} max={100000000} />
                                <DecimalInput label="Versicherung (€)" value={form.versicherung} onChange={v => setze({ versicherung: v })} min={0} max={100000000} />
                            </div>
                        </fieldset>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-semibold text-slate-800">Strom</legend>
                            <div className="grid gap-3 sm:grid-cols-3">
                                <DecimalInput label="Leistung (kW)" value={form.kw} onChange={v => setze({ kw: v })} min={0} max={100000} />
                                <DecimalInput label="Strompreis (€ je kWh)" value={form.strom} onChange={v => setze({ strom: v })} min={0} max={100} />
                                <DecimalInput label="Auslastung (%)" value={form.auslastung} onChange={v => setze({ auslastung: v })} min={0} max={100} />
                            </div>
                            <p className="text-xs text-slate-500">Auslastung: wie viel der Leistung die Maschine im Schnitt wirklich zieht. Leer heißt 100 %.</p>
                        </fieldset>

                        <div className="max-w-xs">
                            <DecimalInput label="Laufstunden je Jahr" value={form.stunden} onChange={v => setze({ stunden: v })} min={0} max={8784} />
                            <p className="mt-1 text-xs text-slate-500">Auf diese Stunden werden die Jahreskosten verteilt.</p>
                        </div>

                        <section aria-label="So rechnet sich der Satz" className="rounded-lg border border-slate-200 bg-slate-50 p-3" data-testid="rechner">
                            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-800"><Calculator className="w-4 h-4 text-rose-600" aria-hidden="true" /> So rechnet sich der Satz je Stunde</h3>
                            {vorschau && vorschau.summe != null ? (
                                <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
                                    <dt>Abschreibung</dt><dd className="text-right">{euro(vorschau.abschreibung)}</dd>
                                    <dt>Zins</dt><dd className="text-right">{euro(vorschau.zins)}</dd>
                                    <dt>Wartung</dt><dd className="text-right">{euro(vorschau.wartung)}</dd>
                                    <dt>Platz</dt><dd className="text-right">{euro(vorschau.platz)}</dd>
                                    <dt>Versicherung</dt><dd className="text-right">{euro(vorschau.versicherung)}</dd>
                                    <dt>Strom</dt><dd className="text-right">{euro(vorschau.energie)}</dd>
                                    <dt className="border-t border-slate-300 pt-1 font-semibold">Errechneter Satz je Stunde</dt><dd className="border-t border-slate-300 pt-1 text-right font-semibold" data-testid="rechner-summe">{euro(vorschau.summe)}</dd>
                                </dl>
                            ) : (
                                <p className="text-sm text-amber-800" data-testid="rechner-hinweis">{vorschau?.hinweis ?? 'Tragen Sie die Angaben ein, dann erscheint hier der Satz.'}</p>
                            )}
                        </section>

                        <fieldset className="space-y-2">
                            <legend className="text-sm font-semibold text-slate-800">Welcher Satz gilt?</legend>
                            <label className="flex items-center gap-2 text-sm text-slate-800"><input type="checkbox" checked={form.handAn} onChange={e => setze({ handAn: e.target.checked })} className="h-4 w-4 rounded border-slate-300 text-rose-600" /> Den Satz selbst festlegen (statt den errechneten zu nehmen)</label>
                            {form.handAn && (
                                <div className="max-w-xs"><DecimalInput label="Satz je Stunde (€)" value={form.hand} onChange={v => setze({ hand: v })} min={0} max={1000} required /></div>
                            )}
                            {!form.handAn && <p className="text-xs text-slate-500">Es gilt der errechnete Satz von oben.</p>}
                        </fieldset>

                        <fieldset className="space-y-1">
                            <legend className="text-sm font-semibold text-slate-800">Welche Arbeitsgänge nutzen diese Maschine?</legend>
                            {arbeitsgaenge.length === 0 && <p className="text-xs text-slate-500">Es gibt noch keine Arbeitsgänge (Stammdaten → Arbeitsgänge).</p>}
                            <ul className="max-h-40 space-y-1 overflow-y-auto">
                                {arbeitsgaenge.map(a => {
                                    const andere = a.maschineId != null && a.maschineId !== dialog?.id ? maschinenName.get(a.maschineId) ?? a.maschinenBezeichnung : null;
                                    return (
                                        <li key={a.id}>
                                            <label className="flex items-center gap-2 text-sm text-slate-800">
                                                <input type="checkbox" checked={form.arbeitsgangIds.includes(a.id)} onChange={e => umschalten(a.id, e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-rose-600" />
                                                {a.beschreibung}
                                                {andere && <span className="text-xs text-slate-500">nutzt bisher „{andere}“, wird umgehängt</span>}
                                            </label>
                                        </li>
                                    );
                                })}
                            </ul>
                        </fieldset>

                        <div>
                            <label htmlFor="m-bem" className="mb-1 block text-sm font-medium text-slate-700">Bemerkung (nur für Sie)</label>
                            <Input id="m-bem" value={form.bemerkung} onChange={e => setze({ bemerkung: e.target.value })} maxLength={500} />
                        </div>
                        <label className="flex items-center gap-2 text-sm text-slate-800"><input type="checkbox" checked={!form.aktiv} onChange={e => setze({ aktiv: !e.target.checked })} className="h-4 w-4 rounded border-slate-300 text-rose-600" /> Maschine ausblenden (sie rechnet weiter, wo sie genutzt wird)</label>

                        {dialog?.id != null && verlauf && verlauf.length > 0 && (
                            <details className="rounded-lg border border-slate-200 p-3 text-sm">
                                <summary className="cursor-pointer font-semibold text-slate-800">Verlauf des Satzes ({verlauf.length})</summary>
                                <ul className="mt-2 space-y-1 text-slate-700" data-testid="verlauf">
                                    {verlauf.map((v, i) => (
                                        <li key={i}>{euro(v.satz)} {v.vonHand ? 'von Hand' : 'errechnet'}, ab {new Date(v.gueltigAb).toLocaleDateString('de-DE')}{v.geaendertVon ? `, von ${v.geaendertVon}` : ''}</li>
                                    ))}
                                </ul>
                            </details>
                        )}

                        {meldung && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{meldung}</p>}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setDialog(null)} disabled={speichert}>Abbrechen</Button>
                        <Button className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => void speichere()} disabled={speichert}><Save className="w-4 h-4" /> {speichert ? 'Speichere …' : 'Speichern'}</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
}
