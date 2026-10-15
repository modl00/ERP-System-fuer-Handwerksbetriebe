import { useState } from 'react';
import { ArrowRight, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useOptionalToast } from '../../components/ui/toast';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { ladeUebernahmeVorschau, uebernehmeSaetze, type UebernahmeParameter, type UebernahmeVorschau } from './kostensaetzeApi';

const euro = (n: number | null) => (n == null ? '–' : formatCurrency(n));
const gleich = (a: UebernahmeParameter, b: UebernahmeParameter) => a.jahr === b.jahr && a.wagnis === b.wagnis && a.gewinn === b.gewinn && a.interneQuote === b.interneQuote;

/**
 * Den Verrechnungssatz des ganzen Betriebs in die Arbeitsgänge übernehmen, <b>erst nach Vorschau</b> (Stufe 3b von #166): je Arbeitsgang bisheriger und neuer Satz des Jahres,
 * übernommen wird nur, was angekreuzt ist. Vorbelegt sind die Arbeitsgänge, deren Satz sich ändert oder fehlt.
 */
const GRUPPEN: Record<string, string> = { AUSZUBILDENDER: 'Auszubildender', FACHARBEITER: 'Facharbeiter', MEISTER: 'Meister' };

export function SatzUebernahmeKarte({ parameter, moeglich }: { parameter: UebernahmeParameter; moeglich: boolean }) {
    const toast = useOptionalToast();
    const [vorschau, setVorschau] = useState<{ daten: UebernahmeVorschau; parameter: UebernahmeParameter } | null>(null);
    const [auswahl, setAuswahl] = useState<Set<number>>(new Set());
    const [laedt, setLaedt] = useState(false);
    const [schreibt, setSchreibt] = useState(false);
    const [fehler, setFehler] = useState('');

    const veraltet = vorschau != null && !gleich(vorschau.parameter, parameter);

    const lade = async () => {
        setLaedt(true);
        setFehler('');
        try {
            const daten = await ladeUebernahmeVorschau(parameter);
            setVorschau({ daten, parameter });
            setAuswahl(new Set(daten.zeilen.filter(z => z.art !== 'GLEICH' && z.art !== 'KEIN_SATZ').map(z => z.arbeitsgangId)));
        } catch (e) {
            setVorschau(null);
            setFehler(e instanceof Error ? e.message : 'Die Vorschau konnte nicht berechnet werden.');
        } finally {
            setLaedt(false);
        }
    };

    const umschalten = (id: number) => setAuswahl(alt => {
        const neu = new Set(alt);
        if (neu.has(id)) neu.delete(id); else neu.add(id);
        return neu;
    });

    const uebernehmen = async () => {
        if (!vorschau || veraltet || auswahl.size === 0) return;
        setSchreibt(true);
        setFehler('');
        try {
            const n = await uebernehmeSaetze(vorschau.parameter, vorschau.daten.neuerSatz, [...auswahl]);
            toast?.success(n === 1 ? 'Für 1 Arbeitsgang wurde der Satz übernommen.' : `Für ${n} Arbeitsgänge wurde der Satz übernommen.`);
            await lade();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Sätze konnten nicht übernommen werden.');
        } finally {
            setSchreibt(false);
        }
    };

    const zeilen = vorschau?.daten.zeilen ?? [];
    const alleGewaehlt = zeilen.length > 0 && zeilen.every(z => auswahl.has(z.arbeitsgangId));

    return (
        <Card className="p-4" data-testid="satz-uebernahme">
            <h2 className="text-base font-semibold text-slate-900">In die Arbeitsgänge übernehmen</h2>
            <p className="mb-3 text-xs text-slate-500">
                Der Verrechnungssatz des ganzen Betriebs wird als Stundensatz der gewählten Arbeitsgänge für das Jahr eingetragen. Erst die Vorschau, dann Ihre Auswahl: Es ändert sich nichts, was Sie nicht ankreuzen.
                Bereits geschriebene Angebote und Rechnungen bleiben unverändert.
            </p>
            <Button variant="outline" onClick={() => void lade()} disabled={!moeglich || laedt || schreibt}>
                {laedt ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <ArrowRight className="h-4 w-4" aria-hidden="true" />} {vorschau ? 'Vorschau neu laden' : 'Vorschau anzeigen'}
            </Button>
            {!moeglich && <span className="ml-3 text-sm text-slate-500">Erst möglich, wenn oben ein Verrechnungssatz für den Betrieb berechnet ist.</span>}
            {fehler && <p role="alert" className="mt-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

            {vorschau && (
                <div className="mt-4" data-testid="uebernahme-vorschau">
                    {veraltet && <p role="status" className="mb-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Die Eingaben oben haben sich geändert. Bitte die Vorschau neu laden.</p>}
                    <p className="mb-2 text-sm text-slate-700">Neuer Satz für {vorschau.daten.jahr}: <strong className="tabular-nums text-rose-700">{euro(vorschau.daten.neuerSatz)}</strong> je Stunde</p>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                                    <th scope="col" className="w-8 py-2">
                                        <input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label="Alle auswählen" checked={alleGewaehlt}
                                            onChange={() => setAuswahl(alleGewaehlt ? new Set() : new Set(zeilen.map(z => z.arbeitsgangId)))} />
                                    </th>
                                    <th scope="col" className="py-2 pr-3 font-semibold">Arbeitsgang</th>
                                    <th scope="col" className="px-2 py-2 text-right font-semibold">Bisher</th>
                                    <th scope="col" className="px-2 py-2 text-right font-semibold">Neu</th>
                                    <th scope="col" className="pl-2 py-2 text-right font-semibold">Unterschied</th>
                                </tr>
                            </thead>
                            <tbody>
                                {zeilen.map(z => (
                                    <tr key={z.arbeitsgangId} className="border-b border-slate-100" data-testid={`uebernahme-${z.name}`}>
                                        <td className="py-2">
                                            <input type="checkbox" className="h-4 w-4 accent-rose-600" aria-label={`${z.name} übernehmen`} disabled={z.art === 'KEIN_SATZ'} checked={auswahl.has(z.arbeitsgangId)} onChange={() => umschalten(z.arbeitsgangId)} />
                                        </td>
                                        <td className="py-2 pr-3">
                                            <span className="font-medium text-slate-900">{z.name}</span>
                                            {z.abteilung && <span className="ml-1 text-xs text-slate-500">({z.abteilung})</span>}
                                            {z.quelle === 'VERRECHNUNG' && <span className="block text-xs text-rose-700">folgt dem Verrechnungssatz{z.gruppe ? ` (${GRUPPEN[z.gruppe] ?? z.gruppe})` : ' (ganzer Betrieb)'}</span>}
                                        </td>
                                        <td className="px-2 py-2 text-right tabular-nums">{z.alt == null ? <span className="text-slate-500">kein Satz</span> : euro(z.alt)}</td>
                                        <td className="px-2 py-2 text-right tabular-nums font-semibold">{z.neu == null ? <span className="text-amber-700 font-normal">kein Satz</span> : euro(z.neu)}</td>
                                        <td className={`pl-2 py-2 text-right tabular-nums ${z.art === 'GLEICH' ? 'text-slate-400' : (z.differenz ?? 0) > 0 ? 'text-emerald-700' : (z.differenz ?? 0) < 0 ? 'text-rose-700' : 'text-slate-600'}`}>
                                            {z.art === 'KEIN_SATZ' ? <span className="text-xs text-amber-700">Für diese Qualifikation fehlen verkäufliche Stunden</span> : z.art === 'GLEICH' ? 'gleich' : z.differenz == null ? 'neu' : `${z.differenz > 0 ? '+' : ''}${euro(z.differenz)}`}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    {zeilen.length === 0 && <p className="py-3 text-sm text-slate-500">Es gibt keine Arbeitsgänge.</p>}
                    <div className="mt-3 flex flex-wrap items-center gap-3">
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={veraltet || auswahl.size === 0 || schreibt}>
                            {schreibt && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Ausgewählte übernehmen ({auswahl.size})
                        </Button>
                        <span className="text-sm text-slate-500">{zeilen.filter(z => z.art === 'GLEICH').length} haben schon diesen Satz.</span>
                    </div>
                </div>
            )}
        </Card>
    );
}
