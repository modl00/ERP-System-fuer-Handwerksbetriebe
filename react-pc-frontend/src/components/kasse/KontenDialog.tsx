import { useCallback, useEffect, useMemo, useState } from 'react';
import { Eye, EyeOff, Loader2, Lock, Pencil, Plus, Search } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogHeader, DialogTitle } from '../ui/dialog';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import type { Sachkonto, SachkontoTyp } from '../../types';
import { SACHKONTO_TYP_LABEL, modalInputCls } from './belegFormat';
import { FieldRow, ModalFooter } from './NeueBuchungDialog';

const ARTEN: SachkontoTyp[] = ['AUFWAND', 'ERTRAG', 'PRIVAT', 'NEUTRAL'];
const ART_HINWEIS: Record<SachkontoTyp, string> = {
    AUFWAND: 'Ausgaben: Material, Löhne, Fahrzeug, Büro',
    ERTRAG: 'Einnahmen aus Aufträgen und Vermietung',
    PRIVAT: 'Privatentnahmen und Privateinlagen des Inhabers',
    NEUTRAL: 'Geldbewegungen ohne Gewinn oder Verlust (Bank, Kasse)',
};

interface Formular {
    id: number | null;
    nummer: string;
    bezeichnung: string;
    kontoTyp: SachkontoTyp;
    beschreibung: string;
    systemkonto: boolean;
}

const leeresFormular = (): Formular => ({ id: null, nummer: '', bezeichnung: '', kontoTyp: 'AUFWAND', beschreibung: '', systemkonto: false });

/** Fehlertext des Servers (`{ message }`), sonst der Ersatztext. */
async function fehlertext(res: Response, ersatz: string): Promise<string> {
    try {
        const daten = await res.json();
        if (daten?.message) return String(daten.message);
    } catch {
        // keine lesbare Antwort
    }
    return res.status === 403 ? 'Dafür fehlt Ihnen das Recht (Belege prüfen und buchen).' : ersatz;
}

/**
 * Konten ansehen, ergänzen, umbenennen und ausblenden (#98). Die mitgelieferten Konten folgen dem SKR03; was fehlt, ergänzt der
 * Betrieb hier von Hand. Konten, die das Programm für die Kasse braucht, sind gesperrt (Schloss): Nummer, Art und Sichtbarkeit bleiben.
 * Ausgeblendete Konten stehen nicht mehr zur Auswahl, bereits gebuchte Belege behalten sie.
 */
export function KontenDialog({ onClose, onChanged }: { onClose: () => void; onChanged: () => void }) {
    const toast = useToast();
    const [konten, setKonten] = useState<Sachkonto[] | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [suche, setSuche] = useState('');
    const [ausgeblendetZeigen, setAusgeblendetZeigen] = useState(false);
    const [formular, setFormular] = useState<Formular | null>(null);
    const [formFehler, setFormFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        try {
            const res = await fetch('/api/buchhaltung/sachkonten?nurAktive=false');
            if (!res.ok) throw new Error(await fehlertext(res, 'Die Konten konnten nicht geladen werden.'));
            setKonten(await res.json() as Sachkonto[]);
            setLadefehler('');
        } catch (e) {
            setLadefehler(e instanceof Error ? e.message : 'Die Konten konnten nicht geladen werden.');
        }
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const gruppen = useMemo(() => {
        const q = suche.trim().toLowerCase();
        const sichtbar = (konten ?? []).filter(k => (ausgeblendetZeigen || k.aktiv)
            && (!q || `${k.nummer ?? ''} ${k.bezeichnung} ${k.beschreibung ?? ''}`.toLowerCase().includes(q)));
        return ARTEN.map(art => ({
            art,
            zeilen: sichtbar.filter(k => k.kontoTyp === art)
                .sort((a, b) => (a.nummer ?? '99999999').localeCompare(b.nummer ?? '99999999') || a.bezeichnung.localeCompare(b.bezeichnung, 'de')),
        })).filter(g => g.zeilen.length > 0);
    }, [konten, suche, ausgeblendetZeigen]);

    const anzahlAusgeblendet = (konten ?? []).filter(k => !k.aktiv).length;

    const oeffneBearbeiten = (k: Sachkonto) => {
        setFormFehler('');
        setFormular({ id: k.id, nummer: k.nummer ?? '', bezeichnung: k.bezeichnung, kontoTyp: k.kontoTyp, beschreibung: k.beschreibung ?? '', systemkonto: !!k.systemkonto });
    };

    const sende = async (id: number | null, body: Record<string, unknown>, erfolg: string): Promise<boolean> => {
        const res = await fetch(id == null ? '/api/buchhaltung/sachkonten' : `/api/buchhaltung/sachkonten/${id}`, {
            method: id == null ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
        });
        if (!res.ok) throw new Error(await fehlertext(res, 'Das Konto konnte nicht gespeichert werden.'));
        toast.success(erfolg);
        await laden();
        onChanged();
        return true;
    };

    const speichern = async () => {
        if (!formular) return;
        setSpeichert(true);
        setFormFehler('');
        try {
            await sende(formular.id, {
                nummer: formular.nummer.trim(), bezeichnung: formular.bezeichnung.trim(), kontoTyp: formular.kontoTyp, beschreibung: formular.beschreibung.trim(),
            }, formular.id == null ? 'Das Konto wurde angelegt.' : 'Das Konto wurde gespeichert.');
            setFormular(null);
        } catch (e) {
            setFormFehler(e instanceof Error ? e.message : 'Das Konto konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const umschalten = async (k: Sachkonto) => {
        try {
            await sende(k.id, { aktiv: !k.aktiv }, k.aktiv ? 'Das Konto ist ausgeblendet.' : 'Das Konto steht wieder zur Auswahl.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Konto konnte nicht geändert werden.');
        }
    };

    return (
        <Dialog open onOpenChange={open => { if (!open) onClose(); }} aria-label="Konten" className="w-full max-w-3xl h-[calc(100vh-2rem)] max-h-[calc(100vh-2rem)] overflow-hidden flex flex-col">
            <DialogHeader><DialogTitle>Konten</DialogTitle></DialogHeader>
            <p className="text-sm text-slate-600">
                Die Nummern folgen dem Kontenrahmen SKR03. Was Ihnen fehlt, ergänzen Sie hier. Mit dem Schloss markierte Konten braucht das Programm für die Kasse.
                Ausgeblendete Konten stehen nicht mehr zur Auswahl, gebuchte Belege behalten sie.
            </p>

            {!formular && (
                <div className="mt-3 flex flex-wrap items-center gap-2">
                    <div className="relative flex-1 min-w-48">
                        <Search aria-hidden="true" className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" />
                        <input aria-label="Konten suchen" value={suche} onChange={e => setSuche(e.target.value)} placeholder="Nummer, Name oder Stichwort"
                            className={`${modalInputCls} pl-8`} />
                    </div>
                    {anzahlAusgeblendet > 0 && (
                        <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer select-none">
                            <input type="checkbox" checked={ausgeblendetZeigen} onChange={e => setAusgeblendetZeigen(e.target.checked)}
                                className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                            Ausgeblendete zeigen ({anzahlAusgeblendet})
                        </label>
                    )}
                    <Button size="sm" onClick={() => { setFormFehler(''); setFormular(leeresFormular()); }} className="bg-rose-600 text-white hover:bg-rose-700">
                        <Plus className="mr-1 h-4 w-4" /> Neues Konto
                    </Button>
                </div>
            )}

            {formular && (
                <div role="form" aria-label={formular.id == null ? 'Neues Konto' : 'Konto bearbeiten'} className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <h3 className="mb-3 text-sm font-semibold text-slate-900">{formular.id == null ? 'Neues Konto' : 'Konto bearbeiten'}</h3>
                    {formular.systemkonto && (
                        <p className="mb-3 flex items-start gap-2 rounded-md bg-amber-50 p-2 text-sm text-amber-800">
                            <Lock aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                            Dieses Konto braucht das Programm für die Kasse. Nummer und Art bleiben, Name und Beschreibung können Sie ändern.
                        </p>
                    )}
                    <div className="grid gap-x-4 sm:grid-cols-[8rem_1fr]">
                        <FieldRow label="Nummer">
                            <input aria-label="Nummer" value={formular.nummer} disabled={formular.systemkonto} inputMode="numeric" maxLength={8}
                                onChange={e => setFormular(f => f && ({ ...f, nummer: e.target.value }))} className={`${modalInputCls} font-mono`} placeholder="4920" />
                        </FieldRow>
                        <FieldRow label="Name">
                            <input aria-label="Name" value={formular.bezeichnung} maxLength={120}
                                onChange={e => setFormular(f => f && ({ ...f, bezeichnung: e.target.value }))} className={modalInputCls} placeholder="Telefon" />
                        </FieldRow>
                    </div>
                    <FieldRow label="Art">
                        <Select aria-label="Art" value={formular.kontoTyp} disabled={formular.systemkonto}
                            onChange={v => setFormular(f => f && ({ ...f, kontoTyp: v as SachkontoTyp }))}
                            options={ARTEN.map(a => ({ value: a, label: `${SACHKONTO_TYP_LABEL[a]}: ${ART_HINWEIS[a]}` }))} />
                    </FieldRow>
                    <FieldRow label="Wofür ist das Konto? (erscheint bei der Auswahl)">
                        <textarea aria-label="Beschreibung" value={formular.beschreibung} maxLength={500} rows={2}
                            onChange={e => setFormular(f => f && ({ ...f, beschreibung: e.target.value }))} className={modalInputCls}
                            placeholder="Zum Beispiel: Mobilfunk und Festnetz" />
                    </FieldRow>
                    {formFehler && <p role="alert" className="text-sm text-rose-700">{formFehler}</p>}
                    <ModalFooter onClose={() => setFormular(null)} onSubmit={speichern} saving={speichert} label="Speichern" />
                </div>
            )}

            <div className="mt-3 min-h-0 flex-1 overflow-y-auto">
                {ladefehler && <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{ladefehler}</div>}
                {!konten && !ladefehler && <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-rose-500" /></div>}
                {konten && gruppen.length === 0 && (
                    <p className="py-8 text-center text-sm text-slate-500">
                        {konten.length === 0 ? 'Es sind noch keine Konten angelegt. Mit „Neues Konto" legen Sie das erste an.' : 'Kein Konto passt zur Suche.'}
                    </p>
                )}
                {gruppen.map(({ art, zeilen }) => (
                    <section key={art} className="mb-4">
                        <h3 className="sticky top-0 bg-white py-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            {SACHKONTO_TYP_LABEL[art]} <span className="font-normal normal-case text-slate-400">· {ART_HINWEIS[art]}</span>
                        </h3>
                        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                            {zeilen.map(k => (
                                <li key={k.id} data-testid={`konto-${k.id}`} className={`flex items-start gap-3 px-3 py-2 ${k.aktiv ? '' : 'bg-slate-50 text-slate-400'}`}>
                                    <span className="w-14 shrink-0 pt-0.5 font-mono text-sm">{k.nummer ?? '–'}</span>
                                    <div className="min-w-0 flex-1">
                                        <p className="flex items-center gap-2 text-sm font-medium text-slate-900">
                                            <span className={k.aktiv ? '' : 'text-slate-500'}>{k.bezeichnung}</span>
                                            {k.systemkonto && <Lock aria-label="Das Programm braucht dieses Konto" className="h-3.5 w-3.5 text-amber-600" />}
                                            {!k.aktiv && <span className="rounded border border-slate-200 px-1.5 text-xs font-normal">ausgeblendet</span>}
                                        </p>
                                        {k.beschreibung && <p className="text-xs text-slate-500">{k.beschreibung}</p>}
                                    </div>
                                    <button type="button" aria-label={`${k.bezeichnung} bearbeiten`} onClick={() => oeffneBearbeiten(k)}
                                        className="rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800"><Pencil className="h-4 w-4" /></button>
                                    <button type="button" aria-label={k.aktiv ? `${k.bezeichnung} ausblenden` : `${k.bezeichnung} wieder einblenden`}
                                        disabled={!!k.systemkonto} title={k.systemkonto ? 'Das Programm braucht dieses Konto' : undefined} onClick={() => void umschalten(k)}
                                        className="rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-40">
                                        {k.aktiv ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </section>
                ))}
            </div>
        </Dialog>
    );
}
