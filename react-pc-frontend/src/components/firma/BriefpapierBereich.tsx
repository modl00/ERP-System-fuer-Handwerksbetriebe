import { useState } from 'react';
import { FileText, Loader2, Plus, Trash2 } from 'lucide-react';

import { Button } from '../ui/button';
import { useConfirm } from '../ui/confirm-dialog';
import { useToast } from '../ui/toast';
import { cn } from '../../lib/utils';

const ERLAUBT = ['image/png', 'image/jpeg', 'application/pdf'];
const MAX_BYTES = 30 * 1024 * 1024;

export interface BriefpapierStand {
    briefpapierDateiname: string | null;
    briefpapierEnthaeltKopf: boolean;
}

interface Props extends BriefpapierStand {
    /** Nach Upload, Löschen oder Umschalten: der neue Stand für die Firmenseite */
    onGeaendert: (neu: BriefpapierStand) => void;
}

async function fehlertext(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json() as { message?: string };
        return body?.message || fallback;
    } catch {
        return fallback;
    }
}

/**
 * Briefpapier der Firma: ein Bild oder PDF, das in jedem Formular (Angebot, Rechnung, Mahnung ...) als Hintergrund der ganzen A4-Seite liegt.
 * Die Größe der Datei spielt keine Rolle, sie wird auf A4-Auflösung verkleinert und immer auf die ganze Seite gezogen.
 */
export function BriefpapierBereich({ briefpapierDateiname, briefpapierEnthaeltKopf, onGeaendert }: Props) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [laedt, setLaedt] = useState(false);
    const [version, setVersion] = useState(0);
    const istPdf = (briefpapierDateiname ?? '').toLowerCase().endsWith('.pdf');

    const hochladen = async (datei: File) => {
        if (!ERLAUBT.includes(datei.type)) {
            toast.error('Ungültiger Dateityp – erlaubt sind PNG, JPEG und PDF.');
            return;
        }
        if (datei.size > MAX_BYTES) {
            toast.error('Datei zu groß – maximal 30 MB.');
            return;
        }
        setLaedt(true);
        try {
            const form = new FormData();
            form.append('datei', datei);
            const res = await fetch('/api/firma/briefpapier', { method: 'POST', body: form });
            if (!res.ok) {
                toast.error(await fehlertext(res, 'Das Briefpapier konnte nicht hochgeladen werden.'));
                return;
            }
            const neu = await res.json() as BriefpapierStand;
            onGeaendert({ briefpapierDateiname: neu.briefpapierDateiname, briefpapierEnthaeltKopf: neu.briefpapierEnthaeltKopf });
            setVersion(v => v + 1);
            toast.success('Briefpapier gespeichert. Es liegt jetzt hinter allen Formularen.');
        } catch {
            toast.error('Netzwerkfehler beim Hochladen.');
        } finally {
            setLaedt(false);
        }
    };

    const entfernen = async () => {
        if (!await confirmDialog({ title: 'Briefpapier löschen', message: 'Das hinterlegte Briefpapier wirklich entfernen? Die Formulare erscheinen danach wieder auf weißem Papier.', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch('/api/firma/briefpapier', { method: 'DELETE' });
            if (!res.ok) {
                toast.error(await fehlertext(res, 'Das Briefpapier konnte nicht gelöscht werden.'));
                return;
            }
            onGeaendert({ briefpapierDateiname: null, briefpapierEnthaeltKopf: false });
            setVersion(v => v + 1);
            toast.success('Briefpapier entfernt.');
        } catch {
            toast.error('Netzwerkfehler beim Löschen.');
        }
    };

    const kopfUmschalten = async (wert: boolean) => {
        try {
            const res = await fetch('/api/firma/briefpapier/kopf', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enthaeltKopf: wert }) });
            if (!res.ok) {
                toast.error(await fehlertext(res, 'Die Einstellung konnte nicht gespeichert werden.'));
                return;
            }
            onGeaendert({ briefpapierDateiname, briefpapierEnthaeltKopf: wert });
            toast.success(wert ? 'Logo und Firmenangaben der Formulare entfallen, sie stehen im Briefpapier.' : 'Formulare zeigen wieder ihren eigenen Briefkopf und die Fußzeile.');
        } catch {
            toast.error('Netzwerkfehler beim Speichern.');
        }
    };

    return (
        <div className="mb-8 pb-6 border-b" data-testid="briefpapier-bereich">
            <h3 className="text-lg font-semibold text-slate-900 mb-4">Briefpapier</h3>
            <div className="flex items-start gap-6">
                <div className="w-32 shrink-0 rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 overflow-hidden flex items-center justify-center" style={{ aspectRatio: '210 / 297' }}>
                    {briefpapierDateiname ? (
                        istPdf ? (
                            <div className="flex flex-col items-center gap-1 text-slate-500 text-xs"><FileText className="w-8 h-8" aria-hidden="true" />PDF</div>
                        ) : (
                            <img src={`/api/firma/briefpapier?v=${version}`} alt="Briefpapier" className="w-full h-full object-fill" />
                        )
                    ) : (
                        <span className="text-xs text-slate-400 text-center px-3">Kein Briefpapier hinterlegt</span>
                    )}
                </div>
                <div className="flex-1 space-y-3">
                    <p className="text-sm text-slate-600">
                        Das Briefpapier liegt in allen Formularen (Angebot, Rechnung, Mahnung ...) als Hintergrund hinter dem Text. Egal wie groß die Datei ist:
                        Sie wird verkleinert und immer <strong>auf die ganze A4-Seite gezogen</strong>. Am besten ein Hochformat im Seitenverhältnis DIN A4 (210 × 297 mm).
                        Formate: PNG, JPEG oder PDF (erste Seite), maximal 30&nbsp;MB.
                    </p>
                    <div className="flex items-center gap-2">
                        <label className={cn('inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg cursor-pointer transition',
                            laedt ? 'bg-slate-200 text-slate-500 cursor-not-allowed' : 'bg-rose-600 text-white hover:bg-rose-700')}>
                            {laedt ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Plus className="w-4 h-4" />}
                            {briefpapierDateiname ? 'Briefpapier ersetzen' : 'Briefpapier hochladen'}
                            <input type="file" accept="image/png,image/jpeg,application/pdf" className="hidden" disabled={laedt}
                                aria-label="Briefpapier auswählen"
                                onChange={e => {
                                    const datei = e.target.files?.[0];
                                    if (datei) void hochladen(datei);
                                    e.target.value = '';
                                }} />
                        </label>
                        {briefpapierDateiname && (
                            <Button type="button" variant="outline" size="sm" onClick={entfernen} disabled={laedt}>
                                <Trash2 className="w-4 h-4 mr-1" /> Entfernen
                            </Button>
                        )}
                    </div>
                    {briefpapierDateiname && (
                        <label className="flex items-start gap-2 text-sm text-slate-700 cursor-pointer select-none max-w-xl">
                            <input type="checkbox" className="h-4 w-4 mt-0.5 accent-rose-600" checked={briefpapierEnthaeltKopf} onChange={e => void kopfUmschalten(e.target.checked)} />
                            <span>
                                Mein Briefpapier enthält schon Briefkopf und Fußzeile
                                <span className="block text-xs text-slate-500">
                                    Dann lassen die Formulare Logo, Firmenname, Adresse, Bank, Steuernummer und Absenderzeile weg, damit nichts doppelt steht.
                                </span>
                            </span>
                        </label>
                    )}
                </div>
            </div>
        </div>
    );
}
