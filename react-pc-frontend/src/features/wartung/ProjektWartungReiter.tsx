import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ExternalLink, FileText, FolderOpen, Receipt } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN, type AusgangsGeschaeftsDokument } from '../../types';
import { formatDatum } from './format';
import { ProjektSammelbericht } from './ProjektSammelbericht';
import { ProjektWartungen } from './ProjektWartungen';
import { WartungVertragKarte } from './WartungVertragKarte';
import type { ProjektWartung } from './types';

interface Props {
    projektId: number;
    kundeId: number | null;
    /** Angebote, Rechnungen usw. dieses Projekts (aus dem Reiter Geschäftsdokumente) */
    dokumente: AusgangsGeschaeftsDokument[];
    /** Anzahl der abgelegten Dateien im Reiter Dokumente */
    dateienAnzahl: number;
    onOeffneReiter: (reiter: 'geschaeftsdokumente' | 'dokumente') => void;
}

const MAX_ZEILEN = 8;
const geld = (wert: number) => wert.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
const typLabel = (typ: string) => AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === typ)?.label ?? typ;

/**
 * Reiter "Wartung" im Projekt: der Wartungsvertrag, die Objekte und Wartungen des Projekts und die Projektmappe
 * (Prüfberichte, Angebote und Rechnungen, Dokumente) an einem Ort.
 */
export function ProjektWartungReiter({ projektId, kundeId, dokumente, dateienAnzahl, onOeffneReiter }: Props) {
    const [wartungen, setWartungen] = useState<ProjektWartung[]>([]);
    const berichte = wartungen.flatMap(w => w.berichte.map(b => ({ ...b, anlage: w.bezeichnung })))
        .sort((a, b) => (a.datum < b.datum ? 1 : a.datum > b.datum ? -1 : 0));
    const geschaeft = [...dokumente].sort((a, b) => (a.datum < b.datum ? 1 : a.datum > b.datum ? -1 : 0));

    return (
        <div className="space-y-4" data-testid="projekt-wartung-reiter">
            <p className="text-sm text-slate-600">
                Hier steht alles zur Wartung dieses Projekts: der Vertrag, die gewarteten Anlagen und die Projektmappe mit Berichten, Angeboten und Dokumenten.
            </p>

            <WartungVertragKarte projektId={projektId} />

            <ProjektWartungen projektId={projektId} kundeId={kundeId} eingebettet onGeladen={setWartungen} />
            {kundeId !== null && (
                <p className="-mt-2 text-sm text-slate-500">
                    Neue Objekte und Anlagen legen Sie beim Kunden an.{' '}
                    <Link to={`/kunden?kundeId=${kundeId}`} className="inline-flex items-center gap-1 text-rose-700 hover:underline">
                        Zum Kunden <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                    </Link>
                </p>
            )}

            <ProjektSammelbericht berichte={berichte.map(b => ({ protokollId: b.protokollId, nummer: b.nummer, datum: b.datum, anlage: b.anlage }))} />

            <section className="rounded-xl border border-slate-200 bg-white p-4" aria-labelledby="projektmappe-titel" data-testid="projektmappe">
                <h3 id="projektmappe-titel" className="mb-3 flex items-center gap-2 text-sm font-medium text-slate-900">
                    <FolderOpen className="h-4 w-4 text-rose-500" aria-hidden="true" /> Projektmappe
                </h3>
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                    <div>
                        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Prüfberichte ({berichte.length})</h4>
                        {berichte.length === 0 ? <p className="text-sm text-slate-500">Noch kein Bericht abgelegt.</p> : (
                            <ul className="space-y-1.5 text-sm">
                                {berichte.slice(0, MAX_ZEILEN).map(b => (
                                    <li key={b.protokollId}>
                                        <a href={b.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-start gap-1.5 text-rose-700 hover:underline">
                                            <FileText className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                            <span className="break-words">{formatDatum(b.datum)} · {b.anlage} ({b.nummer})</span>
                                        </a>
                                    </li>
                                ))}
                                {berichte.length > MAX_ZEILEN && <li className="text-slate-500">… und {berichte.length - MAX_ZEILEN} weitere</li>}
                            </ul>
                        )}
                    </div>
                    <div>
                        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Angebote und Rechnungen ({geschaeft.length})</h4>
                        {geschaeft.length === 0 ? <p className="text-sm text-slate-500">Noch keine Geschäftsdokumente.</p> : (
                            <ul className="space-y-1.5 text-sm">
                                {geschaeft.slice(0, MAX_ZEILEN).map(d => (
                                    <li key={d.id} className="flex items-start gap-1.5 text-slate-700">
                                        <Receipt className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                                        <span className="break-words">
                                            {typLabel(d.typ)} {d.dokumentNummer} · {formatDatum(d.datum)}{d.betragNetto != null && ` · ${geld(d.betragNetto)} netto`}{d.storniert && ' (storniert)'}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        )}
                        <Button variant="ghost" size="sm" className="mt-2 px-0 text-rose-700" onClick={() => onOeffneReiter('geschaeftsdokumente')}>Alle Geschäftsdokumente öffnen</Button>
                    </div>
                    <div>
                        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Dokumente ({dateienAnzahl})</h4>
                        <p className="text-sm text-slate-500">{dateienAnzahl === 0 ? 'Noch keine Dateien abgelegt.' : `${dateienAnzahl} ${dateienAnzahl === 1 ? 'Datei' : 'Dateien'} im Projekt.`}</p>
                        <Button variant="ghost" size="sm" className="mt-2 px-0 text-rose-700" onClick={() => onOeffneReiter('dokumente')}>Dokumente öffnen</Button>
                    </div>
                </div>
            </section>
        </div>
    );
}
