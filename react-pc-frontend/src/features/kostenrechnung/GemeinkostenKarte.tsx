import { useEffect, useState } from 'react';
import { Info, Loader2 } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { useToast } from '../../components/ui/toast';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { ladeGesamtbetrag, speichereGesamtbetrag, speichereSoll, type Gemeinkosten, type KostenstelleZeile } from './kostensaetzeApi';

const MAX_BETRAG = 100_000_000;
const euro = (n: number | null) => (n == null ? '–' : formatCurrency(n));
const alsText = (n: number | null) => (n == null ? '' : formatDecimalInput(n));

function SollZeile({ zeile, onGeaendert }: { zeile: KostenstelleZeile; onGeaendert: () => void }) {
    const toast = useToast();
    const [entwurf, setEntwurf] = useState(alsText(zeile.soll));
    const [speichert, setSpeichert] = useState(false);
    useEffect(() => { setEntwurf(alsText(zeile.soll)); }, [zeile.soll]);

    const pruefung = validateDecimalInput(entwurf, { label: 'Der geschätzte Betrag', required: false, min: 0, max: MAX_BETRAG });
    const geaendert = entwurf.trim() !== alsText(zeile.soll);

    const speichern = async () => {
        if (!pruefung.valid) return;
        setSpeichert(true);
        try {
            await speichereSoll(zeile.id, pruefung.value ?? null);
            toast.success(pruefung.value == null ? `Die Schätzung für ${zeile.name} wurde entfernt.` : `Die Schätzung für ${zeile.name} ist gespeichert.`);
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Schätzung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <tr className="border-b border-slate-100 align-top" data-testid={`gemeinkosten-${zeile.name}`}>
            <td className="py-2 pr-3 font-medium text-slate-900">{zeile.name}</td>
            <td className="px-2 py-2">
                <div className="flex items-start gap-2">
                    <div className="w-36">
                        <DecimalInput label={`Geschätzt im Jahr für ${zeile.name}`} value={entwurf} onChange={setEntwurf} min={0} max={MAX_BETRAG} placeholder="keine Schätzung" />
                    </div>
                    <Button variant="outline" size="sm" className="mt-6" disabled={!geaendert || !pruefung.valid || speichert} onClick={() => void speichern()} aria-label={`Schätzung für ${zeile.name} speichern`}>
                        {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : 'Speichern'}
                    </Button>
                </div>
            </td>
            <td className="px-2 py-2 text-right tabular-nums text-slate-600">{euro(zeile.ist)}</td>
            <td className="pl-2 py-2 text-right tabular-nums">
                <span className="font-semibold">{euro(zeile.verwendet)}</span>
                {zeile.sollGilt && <span className="ml-1 block text-xs text-amber-800">Schätzung gilt</span>}
            </td>
        </tr>
    );
}

function GesamtbetragFeld({ gesperrt, onGeaendert }: { gesperrt: boolean; onGeaendert: () => void }) {
    const toast = useToast();
    const [gespeichert, setGespeichert] = useState<number | null>(null);
    const [entwurf, setEntwurf] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        ladeGesamtbetrag().then(b => { if (aktuell) { setGespeichert(b); setEntwurf(alsText(b)); } }).catch(() => { /* Feld bleibt leer */ });
        return () => { aktuell = false; };
    }, []);

    const pruefung = validateDecimalInput(entwurf, { label: 'Der Gesamtbetrag', required: false, min: 0, max: MAX_BETRAG });
    const geaendert = entwurf.trim() !== alsText(gespeichert);

    const speichern = async () => {
        if (!pruefung.valid) return;
        setSpeichert(true);
        try {
            await speichereGesamtbetrag(pruefung.value ?? null);
            setGespeichert(pruefung.value ?? null);
            toast.success(pruefung.value == null ? 'Der Gesamtbetrag wurde entfernt.' : 'Der Gesamtbetrag ist gespeichert.');
            onGeaendert();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Gesamtbetrag konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <div className="mt-4 border-t border-slate-200 pt-3" data-testid="gesamtbetrag">
            <div className="flex flex-wrap items-start gap-2">
                <div className="w-56">
                    <DecimalInput label="Oder: alle Gemeinkosten zusammen (€ im Jahr)" value={entwurf} onChange={setEntwurf} min={0} max={MAX_BETRAG} placeholder="kein Gesamtbetrag" disabled={gesperrt} />
                </div>
                <Button variant="outline" size="sm" className="mt-6" disabled={gesperrt || !geaendert || !pruefung.valid || speichert} onClick={() => void speichern()}>
                    {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : 'Gesamtbetrag speichern'}
                </Button>
            </div>
            <p className="mt-1 text-xs text-slate-500">
                {gesperrt
                    ? 'Der Gesamtbetrag ruht, solange bei einer Kostenstelle eine Schätzung steht. Entfernen Sie dort die Schätzungen, wenn Sie lieber einen Gesamtbetrag verwenden wollen.'
                    : 'Wenn Sie nicht je Kostenstelle schätzen wollen: Dieser Betrag gilt, solange die echten Kosten niedriger sind.'}
            </p>
        </div>
    );
}

/**
 * Gemeinkosten schätzen (#237): Bei einer frischen Installation sind noch keine Rechnungen im Programm, trotzdem soll ein Stundensatz entstehen. Je Kostenstelle
 * (Miete, Versicherung, Strom …) trägt man ein, was sie im Jahr ungefähr kostet. Im laufenden und in kommenden Jahren gilt je Kostenstelle der höhere Wert aus Schätzung und
 * echten Kosten, in vergangenen Jahren zählen nur die echten Kosten.
 */
export function GemeinkostenKarte({ gemeinkosten, rueckblick, onGeaendert }: { gemeinkosten: Gemeinkosten; rueckblick: boolean; onGeaendert: () => void }) {
    return (
        <Card className="p-4" data-testid="gemeinkosten-karte">
            <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-base font-semibold text-slate-900">Gemeinkosten schätzen</h2>
                {gemeinkosten.enthaeltSchaetzwerte && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900">Enthält Schätzwerte</span>}
            </div>
            <p className="mb-2 flex items-start gap-1.5 text-xs text-slate-500">
                <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>
                    Miete, Versicherung, Strom, Büro: Solange noch nicht alle Rechnungen im Programm sind, schätzen Sie hier, was jede Kostenstelle im Jahr kostet. Das Programm rechnet im
                    laufenden und in kommenden Jahren mit dem höheren Wert aus Ihrer Schätzung und den echten Kosten. {rueckblick && 'Für vergangene Jahre zählen nur die echten Kosten.'}
                </span>
            </p>
            {gemeinkosten.kostenstellen.length === 0 ? (
                <p className="text-sm text-slate-600">Es gibt noch keine Kostenstelle für Gemeinkosten. Legen Sie sie unter Stammdaten → Kostenstellen an, oder tragen Sie unten einen Gesamtbetrag ein.</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                                <th scope="col" className="py-2 pr-3 font-semibold">Kostenstelle</th>
                                <th scope="col" className="px-2 py-2 font-semibold">Geschätzt im Jahr</th>
                                <th scope="col" className="px-2 py-2 text-right font-semibold">Echt bisher</th>
                                <th scope="col" className="pl-2 py-2 text-right font-semibold">Gerechnet wird mit</th>
                            </tr>
                        </thead>
                        <tbody>
                            {gemeinkosten.kostenstellen.map(k => <SollZeile key={k.id} zeile={k} onGeaendert={onGeaendert} />)}
                        </tbody>
                    </table>
                </div>
            )}
            <GesamtbetragFeld gesperrt={gemeinkosten.sollVorhanden} onGeaendert={onGeaendert} />
        </Card>
    );
}
