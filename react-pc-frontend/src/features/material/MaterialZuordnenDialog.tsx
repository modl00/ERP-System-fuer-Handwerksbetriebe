import { useMemo, useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { cn } from '../../lib/utils';
import { abweichung, euro, mengeText, type MaterialPosition, type MaterialRechnung, type ZeileEingabe } from './materialAbrechnungApi';

interface Props {
    rechnung: MaterialRechnung;
    positionen: MaterialPosition[];
    schwelleProzent: number;
    onSpeichern: (zeilen: ZeileEingabe[]) => Promise<void>;
    onSchliessen: () => void;
}

const alsZahl = (t: string) => Number(t.trim().replace(',', '.'));
const betragText = (b: number) => b.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Ordnet den Betrag einer Rechnung dem geplanten Material des Projekts zu. Positionen desselben Lieferanten sind vorgewählt und mit dem geplanten Preis vorbelegt;
 * die Beträge lassen sich ändern. Was übrig bleibt, kann als "Material ohne Plan" (spontaner Kauf) mit Beschreibung gebucht werden. Weicht ein Betrag um mehr als
 * die Schwelle vom Plan ab, steht gleich daneben ein Hinweis (er blockiert nichts). Die Projektkosten ändern sich durch die Zuordnung nicht.
 */
export function MaterialZuordnenDialog({ rechnung, positionen, schwelleProzent, onSpeichern, onSchliessen }: Props) {
    const vorhanden = useMemo(() => new Map(rechnung.zeilen.filter((z) => z.artikelInProjektId !== null).map((z) => [z.artikelInProjektId as number, z])), [rechnung.zeilen]);
    // Offene Positionen und die, die schon durch diese Rechnung bezahlt sind (zum Ändern)
    const angebot = useMemo(() => positionen.filter((p) => p.ist === null || vorhanden.has(p.artikelInProjektId)), [positionen, vorhanden]);
    const ohnePlanVorhanden = rechnung.zeilen.find((z) => z.artikelInProjektId === null);

    const [an, setAn] = useState<Record<number, boolean>>(() => Object.fromEntries(angebot.map((p) => [
        p.artikelInProjektId, vorhanden.has(p.artikelInProjektId) || (vorhanden.size === 0 && p.ist === null && p.lieferantId !== null && p.lieferantId === rechnung.lieferantId),
    ])));
    const [betraege, setBetraege] = useState<Record<number, string>>(() => Object.fromEntries(angebot.map((p) => [
        p.artikelInProjektId, betragText(vorhanden.get(p.artikelInProjektId)?.betrag ?? p.geplant ?? 0),
    ])));
    const [ohnePlan, setOhnePlan] = useState(() => ohnePlanVorhanden ? betragText(ohnePlanVorhanden.betrag) : '');
    const [beschreibung, setBeschreibung] = useState(ohnePlanVorhanden?.beschreibung ?? '');
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const summePositionen = angebot.reduce((s, p) => s + (an[p.artikelInProjektId] ? (Number.isFinite(alsZahl(betraege[p.artikelInProjektId] ?? '')) ? alsZahl(betraege[p.artikelInProjektId]) : 0) : 0), 0);
    const ohnePlanBetrag = ohnePlan.trim() === '' ? 0 : alsZahl(ohnePlan);
    const summe = summePositionen + (Number.isFinite(ohnePlanBetrag) ? ohnePlanBetrag : 0);
    const rest = Math.round((rechnung.anteil - summe) * 100) / 100;
    const zuViel = rest < -0.01;

    const restAlsOhnePlan = () => setOhnePlan(betragText(Math.max(0, Math.round((rechnung.anteil - summePositionen) * 100) / 100)));

    const speichern = async () => {
        setFehler('');
        const zeilen: ZeileEingabe[] = [];
        for (const p of angebot) {
            if (!an[p.artikelInProjektId]) continue;
            const b = alsZahl(betraege[p.artikelInProjektId] ?? '');
            if (!Number.isFinite(b) || b <= 0) { setFehler(`Bitte bei "${p.bezeichnung}" einen Betrag größer als 0 eintragen.`); return; }
            zeilen.push({ artikelInProjektId: p.artikelInProjektId, betrag: b, beschreibung: null });
        }
        if (ohnePlan.trim() !== '') {
            const b = alsZahl(ohnePlan);
            if (!Number.isFinite(b) || b <= 0) { setFehler('Bitte beim Material ohne Plan einen Betrag größer als 0 eintragen.'); return; }
            if (beschreibung.trim().length < 3) { setFehler('Bitte beim Material ohne Plan kurz beschreiben, was gekauft wurde.'); return; }
            zeilen.push({ artikelInProjektId: null, betrag: b, beschreibung: beschreibung.trim() });
        }
        if (zuViel) { setFehler(`Die Zeilen ergeben ${euro(summe)}, die Rechnung hat für dieses Projekt nur ${euro(rechnung.anteil)}.`); return; }
        setSpeichert(true);
        try {
            await onSpeichern(zeilen);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Das Speichern hat nicht geklappt.');
            setSpeichert(false);
        }
    };

    return (
        <Dialog open onOpenChange={(o) => { if (!o && !speichert) onSchliessen(); }} aria-label="Material zur Rechnung zuordnen">
            <DialogContent className="sm:max-w-[780px]">
                <DialogHeader>
                    <DialogTitle>Material zur Rechnung {rechnung.dokumentNummer ?? ''} zuordnen</DialogTitle>
                    <DialogDescription>
                        {rechnung.lieferantName ?? 'Lieferant'}: Das Projekt trägt {euro(rechnung.anteil)} netto. Welches geplante Material bezahlt diese Rechnung? Die Projektkosten ändern sich dadurch nicht, es wird nur sichtbar,
                        was womit bezahlt ist.
                    </DialogDescription>
                </DialogHeader>

                {angebot.length === 0 ? (
                    <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">Im Projekt gibt es kein offenes Material, das per Rechnung kommt. Unten lässt sich ein Kauf ohne Plan buchen.</p>
                ) : (
                    <ul className="max-h-[38vh] divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
                        {angebot.map((p) => {
                            const k = p.artikelInProjektId;
                            const b = alsZahl(betraege[k] ?? '');
                            const abw = an[k] && Number.isFinite(b) ? abweichung(p.geplant, b, schwelleProzent) : null;
                            return (
                                <li key={k} className="flex flex-wrap items-start gap-3 p-3">
                                    <input type="checkbox" className="mt-1 h-5 w-5 accent-rose-600" checked={!!an[k]} onChange={(e) => setAn((a) => ({ ...a, [k]: e.target.checked }))} aria-label={`${p.bezeichnung} gehört zu dieser Rechnung`} />
                                    <div className="min-w-0 flex-1">
                                        <p className="font-medium text-slate-900">{p.bezeichnung}</p>
                                        <p className="text-xs text-slate-500">
                                            {[p.artikelnummer, `${mengeText(p.menge)} ${p.einheit}`, p.lieferantName, p.bestellt ? 'bestellt' : 'noch nicht bestellt'].filter(Boolean).join(' · ')}
                                            {p.geplant !== null && ` · Plan ${euro(p.geplant)}`}
                                        </p>
                                        {abw && (
                                            <p className={cn('mt-1 flex items-center gap-1 text-xs font-medium', abw.art === 'TEURER' ? 'text-rose-700' : 'text-emerald-700')}>
                                                <AlertTriangle className="h-3.5 w-3.5" /> {abw.art === 'TEURER' ? 'teurer' : 'günstiger'} als geplant: {Math.abs(abw.prozent).toLocaleString('de-DE')} %
                                            </p>
                                        )}
                                    </div>
                                    <div className="flex shrink-0 items-center gap-1">
                                        <input inputMode="decimal" aria-label={`Betrag ${p.bezeichnung}`} disabled={!an[k]} value={betraege[k] ?? ''}
                                            onChange={(e) => setBetraege((m) => ({ ...m, [k]: e.target.value }))}
                                            className="w-28 rounded-lg border border-slate-300 px-2 py-1.5 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50 disabled:text-slate-400" />
                                        <span className="text-sm text-slate-600">€</span>
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                )}

                <div className="rounded-lg border border-slate-200 p-3">
                    <p className="text-sm font-medium text-slate-800">Material ohne Plan (zum Beispiel Kauf im Handel)</p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                        <input aria-label="Beschreibung Material ohne Plan" value={beschreibung} maxLength={255} onChange={(e) => setBeschreibung(e.target.value)} placeholder="Was wurde gekauft?"
                            className="min-w-[12rem] flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                        <input inputMode="decimal" aria-label="Betrag Material ohne Plan" value={ohnePlan} onChange={(e) => setOhnePlan(e.target.value)} placeholder="0,00"
                            className="w-28 rounded-lg border border-slate-300 px-2 py-1.5 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                        <span className="text-sm text-slate-600">€</span>
                        <Button variant="outline" size="sm" onClick={restAlsOhnePlan}>Rest hier eintragen</Button>
                    </div>
                </div>

                <p className={cn('text-sm', zuViel ? 'font-medium text-rose-700' : 'text-slate-700')} aria-live="polite">
                    Zugeordnet {euro(summe)} von {euro(rechnung.anteil)} · {zuViel ? `${euro(-rest)} zu viel` : rest > 0.01 ? `noch offen ${euro(rest)}` : 'alles zugeordnet'}
                </p>
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                    <Button onClick={() => void speichern()} disabled={speichert || zuViel}>{speichert && <Loader2 className="h-4 w-4 animate-spin" />} Speichern</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
