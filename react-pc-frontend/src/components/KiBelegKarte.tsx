import { useEffect, useRef, useState } from 'react';
import { Check, FileUp, Loader2, X } from 'lucide-react';

interface Beleg {
    dokumentTyp?: string | null;
    dokumentNummer?: string | null;
    dokumentDatum?: string | null;
    betragNetto?: number | null;
    betragBrutto?: number | null;
    lieferantName?: string | null;
    [feld: string]: unknown;
}

interface Kandidat { id: number; name: string; aehnlichkeit: number }
interface Vorschlag { beleg: Beleg; lieferantId: number | null; lieferantName: string | null; kandidaten: Kandidat[]; hinweise: string[] }

const TYPEN: Record<string, string> = {
    ANGEBOT: 'Angebot', AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung', LIEFERSCHEIN: 'Lieferschein', RECHNUNG: 'Eingangsrechnung', GUTSCHRIFT: 'Gutschrift', SONSTIG: 'Sonstiges Dokument',
};

const euro = (n: number | null | undefined) => (n == null ? '–' : n.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' }));

async function fehlerText(res: Response, fallback: string): Promise<string> {
    try {
        const d = await res.json() as { message?: string };
        if (d?.message) return d.message;
    } catch { /* kein JSON */ }
    return fallback;
}

/**
 * Beleg im KI-Chat: Datei wählen, auswerten lassen, Vorschlag prüfen, erst nach „Ja, ablegen“ wird gespeichert (über den gewohnten Import des Lieferanten).
 * Es wird nichts angelegt: Ist der Lieferant unbekannt, gibt es nur den Hinweis, ihn zuerst anzulegen.
 */
export function KiBelegKarte({ datei: vorgegeben = null }: { datei?: File | null } = {}) {
    const feld = useRef<HTMLInputElement>(null);
    const [datei, setDatei] = useState<File | null>(null);
    const [laedt, setLaedt] = useState(false);
    const [vorschlag, setVorschlag] = useState<Vorschlag | null>(null);
    const [lieferantId, setLieferantId] = useState<number | null>(null);
    const [typ, setTyp] = useState('RECHNUNG');
    const [nummer, setNummer] = useState('');
    const [meldung, setMeldung] = useState<{ art: 'ok' | 'fehler'; text: string } | null>(null);

    const zuruecksetzen = () => { setDatei(null); setVorschlag(null); setLieferantId(null); setMeldung(null); if (feld.current) feld.current.value = ''; };

    const waehle = async (f: File) => {
        setDatei(f); setVorschlag(null); setMeldung(null); setLaedt(true);
        try {
            const form = new FormData();
            form.append('datei', f);
            const res = await fetch('/api/ki-assistent/beleg/pruefen', { method: 'POST', body: form });
            if (!res.ok) throw new Error(await fehlerText(res, 'Der Beleg konnte nicht ausgewertet werden.'));
            const v = await res.json() as Vorschlag;
            setVorschlag(v);
            setLieferantId(v.lieferantId);
            setTyp(v.beleg.dokumentTyp && TYPEN[v.beleg.dokumentTyp] ? v.beleg.dokumentTyp : 'RECHNUNG');
            setNummer(v.beleg.dokumentNummer ?? '');
        } catch (e) {
            setMeldung({ art: 'fehler', text: e instanceof Error ? e.message : 'Der Beleg konnte nicht ausgewertet werden.' });
        } finally {
            setLaedt(false);
        }
    };

    // Kommt die Datei schon aus dem Chat (hineingezogen), wird sie sofort ausgewertet
    useEffect(() => {
        if (!vorgegeben) return;
        const t = setTimeout(() => { void waehle(vorgegeben); }, 0);
        return () => clearTimeout(t);
    }, [vorgegeben]);

    const ablegen = async () => {
        if (!datei || !vorschlag || lieferantId == null) return;
        setLaedt(true);
        try {
            const { lieferantName: _n, ...rest } = vorschlag.beleg;
            void _n;
            const metadaten = { ...rest, dokumentTyp: typ, dokumentNummer: nummer.trim() || null, lieferantId };
            const form = new FormData();
            form.append('datei', datei);
            form.append('metadata', new Blob([JSON.stringify(metadaten)], { type: 'application/json' }));
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/import`, { method: 'POST', body: form });
            if (!res.ok) throw new Error(await fehlerText(res, 'Der Beleg konnte nicht abgelegt werden.'));
            const name = vorschlag.kandidaten.find(k => k.id === lieferantId)?.name ?? vorschlag.lieferantName ?? 'dem Lieferanten';
            setVorschlag(null);
            setMeldung({ art: 'ok', text: `Abgelegt bei ${name}: ${TYPEN[typ] ?? typ}${nummer.trim() ? ` ${nummer.trim()}` : ''}.` });
        } catch (e) {
            setMeldung({ art: 'fehler', text: e instanceof Error ? e.message : 'Der Beleg konnte nicht abgelegt werden.' });
        } finally {
            setLaedt(false);
        }
    };

    return (
        <div className="rounded-lg border border-slate-200 bg-white p-2 text-slate-800" data-testid="ki-beleg">
            <input ref={feld} type="file" accept=".pdf,.xml,image/*" className="sr-only" aria-label="Beleg auswählen" onChange={e => { const f = e.target.files?.[0]; if (f) void waehle(f); }} />
            {!vorschlag && (
                <button type="button" onClick={() => feld.current?.click()} disabled={laedt}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                    {laedt ? <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <FileUp className="h-3.5 w-3.5" aria-hidden="true" />}
                    {laedt ? 'Beleg wird ausgewertet …' : 'Beleg auswerten und ablegen'}
                </button>
            )}
            {vorschlag && (
                <div className="space-y-2 text-sm" data-testid="ki-beleg-vorschlag">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-800">Vorschlag zum Ablegen</p>
                    <p>
                        Das sieht aus wie ein(e) <strong>{TYPEN[typ] ?? typ}</strong>
                        {vorschlag.beleg.lieferantName ? <> von <strong>{vorschlag.beleg.lieferantName}</strong></> : null}.
                        Brutto {euro(vorschlag.beleg.betragBrutto)}{vorschlag.beleg.dokumentDatum ? `, vom ${new Date(vorschlag.beleg.dokumentDatum).toLocaleDateString('de-DE')}` : ''}.
                    </p>
                    <div className="grid grid-cols-2 gap-2 text-xs">
                        <label className="block">Art
                            <select value={typ} onChange={e => setTyp(e.target.value)} className="mt-0.5 w-full rounded border border-slate-300 px-1.5 py-1">
                                {Object.entries(TYPEN).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                            </select>
                        </label>
                        <label className="block">Nummer
                            <input value={nummer} onChange={e => setNummer(e.target.value)} className="mt-0.5 w-full rounded border border-slate-300 px-1.5 py-1" />
                        </label>
                    </div>
                    <fieldset className="text-xs">
                        <legend className="mb-0.5 font-medium">Ablegen bei</legend>
                        {vorschlag.kandidaten.length === 0 && <p className="text-slate-600">Kein passender Lieferant gefunden. Bitte den Lieferanten zuerst anlegen.</p>}
                        {vorschlag.kandidaten.map(k => (
                            <label key={k.id} className="flex items-center gap-1.5">
                                <input type="radio" name="ki-beleg-lieferant" checked={lieferantId === k.id} onChange={() => setLieferantId(k.id)} className="accent-rose-600" /> {k.name}
                            </label>
                        ))}
                    </fieldset>
                    {vorschlag.hinweise.map(h => <p key={h} className="rounded bg-amber-50 px-2 py-1 text-xs text-amber-900">{h}</p>)}
                    <p className="text-xs text-slate-600">Es wird nichts gespeichert, bevor Sie bestätigen.</p>
                    <div className="flex gap-2">
                        <button type="button" onClick={() => void ablegen()} disabled={laedt || lieferantId == null}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-700 disabled:opacity-50">
                            {laedt ? <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Check className="h-3.5 w-3.5" aria-hidden="true" />} Ja, ablegen
                        </button>
                        <button type="button" onClick={zuruecksetzen} disabled={laedt}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                            <X className="h-3.5 w-3.5" aria-hidden="true" /> Verwerfen
                        </button>
                    </div>
                </div>
            )}
            {meldung && (
                <p role={meldung.art === 'fehler' ? 'alert' : 'status'} className={`mt-1 text-xs font-medium ${meldung.art === 'ok' ? 'text-emerald-700' : 'text-rose-700'}`}>{meldung.text}</p>
            )}
        </div>
    );
}
