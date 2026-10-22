import { useEffect, useRef, useState } from 'react';
import { File as FileIcon, FolderOpen, Loader2, Mail, Paperclip, Upload, X } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { InfoButton } from '../../components/ui/info-button';
import { Input } from '../../components/ui/input';
import { useToast } from '../../components/ui/toast';
import type { ProjektDokument } from '../../types';
import { pdfAdresse, type Bestellung } from './bestellwesenApi';

interface Props {
    bestellung: Bestellung;
    onGesendet: () => void;
    onSchliessen: () => void;
}

const FRONTEND_USER_KEY = 'frontendUserSelection';

function aktuellerBenutzer(): { id: number | null; name: string } {
    try {
        const roh = localStorage.getItem(FRONTEND_USER_KEY);
        const p = roh ? (JSON.parse(roh) as { id?: unknown; displayName?: unknown }) : null;
        return { id: typeof p?.id === 'number' ? p.id : null, name: typeof p?.displayName === 'string' ? p.displayName : '' };
    } catch {
        return { id: null, name: '' };
    }
}

const maskiere = (t: string) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const alsHtml = (t: string) => t.split(/\n{2,}/).map((a) => `<p>${maskiere(a).replace(/\n/g, '<br>')}</p>`).join('');

/** 20 MB Gesamtlimit für alle zusätzlichen Anhänge (manuell + Projektdateien). */
const MAX_BYTES = 20 * 1024 * 1024;

function gesamtgroesse(extra: File[], projektDateien: Map<number, File>): number {
    return extra.reduce((s, f) => s + f.size, 0)
        + Array.from(projektDateien.values()).reduce((s, f) => s + f.size, 0);
}

/**
 * E-Mail an den Lieferanten mit dem Bestellformular als PDF im Anhang.
 * Unterstützt zusätzliche Datei-Uploads und – wenn die Bestellung aus einem
 * Projekt stammt – das direkte Anhängen von Projektdateien (Zeichnungen etc.).
 */
export function BestellMailDialog({ bestellung, onGesendet, onSchliessen }: Props) {
    const nummer = bestellung.bestellnummer ?? '';
    const [empfaenger, setEmpfaenger] = useState(bestellung.lieferantEmails[0] ?? '');
    const [betreff, setBetreff] = useState(`Bestellung ${nummer}`);
    const [text, setText] = useState(
        `Guten Tag,\n\nanbei unsere Bestellung ${nummer}.\n\nBitte bestätigen Sie uns die Bestellung mit dem Liefertermin und nennen Sie die Bestellnummer auf Auftragsbestätigung, Lieferschein und Rechnung.\n\nVielen Dank und freundliche Grüße`,
    );
    const [sendet, setSendet] = useState(false);

    const toast = useToast();
    const confirm = useConfirm();

    // Extra-Dateien (manuell hochgeladen)
    const [extraDateien, setExtraDateien] = useState<File[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Projektdateien
    const [zeigeOrdner, setZeigeOrdner] = useState(false);
    const [projektDokumente, setProjektDokumente] = useState<ProjektDokument[]>([]);
    const [ladeOrdner, setLadeOrdner] = useState(false);
    const [ausgewaehlteProjektIds, setAusgewaehlteProjektIds] = useState<Set<number>>(new Set());
    const [ladeProjektDateiIds, setLadeProjektDateiIds] = useState<Set<number>>(new Set());
    const [ausgewaehlteProjektDateien, setAusgewaehlteProjektDateien] = useState<Map<number, File>>(new Map());

    const adresseOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(empfaenger.trim());

    // Positions-Anhänge (DXF/STEP/PDF direkt an Positionen angehängt)
    const allePositionsAnhaenge = bestellung.positionen.flatMap(p => p.anhaenge ?? []);

    // Projektdateien laden wenn Ordner geöffnet wird
    useEffect(() => {
        if (!zeigeOrdner || !bestellung.projektId || projektDokumente.length > 0) return;
        setLadeOrdner(true);
        fetch(`/api/projekte/${bestellung.projektId}`)
            .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
            .then((data: { dokumente?: ProjektDokument[] }) => {
                setProjektDokumente(data.dokumente ?? []);
            })
            .catch(() => {
                toast.error('Projektdateien konnten nicht geladen werden.');
            })
            .finally(() => setLadeOrdner(false));
    // toast ist stabil, kein echter Dep
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [zeigeOrdner, bestellung.projektId, projektDokumente.length]);

    const handleFileUpload = (files: FileList | null) => {
        if (!files) return;
        const neu = Array.from(files);
        const neueDateien: File[] = [];
        for (const f of neu) {
            if (gesamtgroesse([...extraDateien, ...neueDateien], ausgewaehlteProjektDateien) + f.size > MAX_BYTES) {
                toast.error(`"${f.name}" überschreitet das Anhangslimit von 20 MB.`);
                break;
            }
            neueDateien.push(f);
        }
        setExtraDateien(prev => [...prev, ...neueDateien]);
    };

    const handleProjektDateiToggle = async (dok: ProjektDokument) => {
        if (ausgewaehlteProjektIds.has(dok.id)) {
            setAusgewaehlteProjektIds(prev => { const s = new Set(prev); s.delete(dok.id); return s; });
            setAusgewaehlteProjektDateien(prev => { const m = new Map(prev); m.delete(dok.id); return m; });
            return;
        }
        setLadeProjektDateiIds(prev => new Set(prev).add(dok.id));
        try {
            const url = dok.url + (dok.url.includes('?') ? '&' : '?') + 'download=true';
            const r = await fetch(url);
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const blob = await r.blob();
            const neueDateien = new Map(ausgewaehlteProjektDateien);
            const file = new File([blob], dok.originalDateiname, { type: blob.type || dok.dateityp || 'application/octet-stream' });
            if (gesamtgroesse(extraDateien, neueDateien) + file.size > MAX_BYTES) {
                toast.error(`"${dok.originalDateiname}" überschreitet das Anhangslimit von 20 MB.`);
                return;
            }
            neueDateien.set(dok.id, file);
            setAusgewaehlteProjektDateien(neueDateien);
            setAusgewaehlteProjektIds(prev => new Set(prev).add(dok.id));
        } catch {
            toast.error(`"${dok.originalDateiname}" konnte nicht geladen werden.`);
        } finally {
            setLadeProjektDateiIds(prev => { const s = new Set(prev); s.delete(dok.id); return s; });
        }
    };

    const senden = async () => {
        const ok = await confirm({
            title: 'E-Mail wirklich senden?',
            message: `Die Bestellung ${nummer} wird per E-Mail an ${bestellung.lieferantName} gesendet. Diese Aktion kann nicht rückgängig gemacht werden.`,
            variant: 'info',
        });
        if (!ok) return;

        setSendet(true);
        try {
            const pdf = await fetch(pdfAdresse(bestellung.id));
            if (!pdf.ok) throw new Error('Das Bestellformular konnte nicht erstellt werden.');
            const pdfBlob = await pdf.blob();

            // Positions-Anhänge laden
            const posAnhangBlobs = await Promise.all(
                allePositionsAnhaenge.map(async (a) => {
                    const r = await fetch(a.url);
                    if (!r.ok) return null;
                    return { blob: await r.blob(), name: a.originalDateiname };
                })
            );

            const benutzer = aktuellerBenutzer();
            const dto = {
                sender: null,
                recipients: [empfaenger.trim()],
                cc: [],
                subject: betreff.trim(),
                body: alsHtml(text),
                direction: 'OUT',
                benutzer: benutzer.name,
                frontendUserId: benutzer.id,
                lieferantId: bestellung.lieferantId,
            };
            const form = new FormData();
            form.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
            form.append('attachments', pdfBlob, `Bestellung_${nummer.replace(/[^A-Za-z0-9._-]/g, '_')}.pdf`);
            posAnhangBlobs.forEach(a => { if (a) form.append('attachments', a.blob, a.name); });
            extraDateien.forEach(f => form.append('attachments', f, f.name));
            ausgewaehlteProjektDateien.forEach(f => form.append('attachments', f, f.name));

            const res = await fetch('/api/emails/send', { method: 'POST', body: form });
            if (!res.ok) throw new Error('Die E-Mail konnte nicht gesendet werden. Bitte die E-Mail-Einstellungen prüfen.');
            onGesendet();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die E-Mail konnte nicht gesendet werden.');
        } finally {
            setSendet(false);
        }
    };

    const hatProjekt = !!bestellung.projektId;

    return (
        <Dialog open onOpenChange={(o) => { if (!o && !sendet) onSchliessen(); }} aria-label={`Bestellung ${nummer} per E-Mail senden`}>
            <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Bestellung {nummer} per E-Mail senden</DialogTitle>
                    <DialogDescription>An {bestellung.lieferantName}. Das Bestellformular hängt als PDF an.</DialogDescription>
                </DialogHeader>
                <div className="space-y-3">
                    <div>
                        <label htmlFor="mail-an" className="mb-1 block text-sm font-medium text-slate-700">An</label>
                        <Input id="mail-an" type="email" list="mail-vorschlaege" value={empfaenger} onChange={(e) => setEmpfaenger(e.target.value)} placeholder="E-Mail-Adresse des Lieferanten" />
                        <datalist id="mail-vorschlaege">{bestellung.lieferantEmails.map((a) => <option key={a} value={a} />)}</datalist>
                        {bestellung.lieferantEmails.length === 0 && <p className="mt-1 text-xs text-amber-700">Beim Lieferanten ist noch keine E-Mail-Adresse hinterlegt.</p>}
                    </div>
                    <div>
                        <label htmlFor="mail-betreff" className="mb-1 block text-sm font-medium text-slate-700">Betreff</label>
                        <Input id="mail-betreff" value={betreff} onChange={(e) => setBetreff(e.target.value)} />
                    </div>
                    <div>
                        <label htmlFor="mail-text" className="mb-1 block text-sm font-medium text-slate-700">Nachricht</label>
                        <textarea id="mail-text" rows={8} value={text} onChange={(e) => setText(e.target.value)}
                            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                    </div>

                    {/* Anhang-Liste */}
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-1.5">
                        <div className="flex items-center gap-2 mb-2">
                            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Anhänge</p>
                            <InfoButton titel="Was wird angehängt?">
                                <p>Das Bestellformular wird immer als PDF mitgeschickt. Dateien, die direkt an den Positionen hängen (z. B. DXF-Zeichnungen), werden automatisch mit angehängt.</p>
                                <p className="mt-1">Mit „Datei anhängen" oder „Aus Projekt" können Sie weitere Unterlagen ergänzen – diese werden direkt im Anhang der E-Mail mitgeschickt.</p>
                            </InfoButton>
                        </div>
                        <p className="flex items-center gap-2 text-sm text-slate-600">
                            <Paperclip className="h-4 w-4 shrink-0 text-slate-400" />
                            Bestellung_{nummer}.pdf
                        </p>
                        {allePositionsAnhaenge.map(a => (
                            <p key={a.id} className="flex items-center gap-2 pl-5 text-sm text-slate-500">
                                <Paperclip className="h-3.5 w-3.5 shrink-0 text-slate-400" /> {a.originalDateiname}
                            </p>
                        ))}
                        {extraDateien.map((f, i) => (
                            <div key={i} className="flex items-center gap-2 pl-5 text-sm text-slate-600">
                                <FileIcon className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                                <span className="flex-1 truncate">{f.name}</span>
                                <button
                                    type="button"
                                    aria-label={`${f.name} entfernen`}
                                    onClick={() => setExtraDateien(prev => prev.filter((_, j) => j !== i))}
                                    className="text-slate-400 hover:text-rose-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded"
                                >
                                    <X className="h-3.5 w-3.5" />
                                </button>
                            </div>
                        ))}
                        {ausgewaehlteProjektIds.size > 0 && Array.from(ausgewaehlteProjektIds).map(id => {
                            const dok = projektDokumente.find(d => d.id === id);
                            if (!dok) return null;
                            return (
                                <div key={id} className="flex items-center gap-2 pl-5 text-sm text-slate-600">
                                    <FolderOpen className="h-3.5 w-3.5 shrink-0 text-rose-400" />
                                    <span className="flex-1 truncate">{dok.originalDateiname}</span>
                                    <button
                                        type="button"
                                        aria-label={`${dok.originalDateiname} entfernen`}
                                        onClick={() => void handleProjektDateiToggle(dok)}
                                        className="text-slate-400 hover:text-rose-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded"
                                    >
                                        <X className="h-3.5 w-3.5" />
                                    </button>
                                </div>
                            );
                        })}
                    </div>

                    {/* Buttons: Datei hochladen + Projektordner */}
                    <div className="flex flex-wrap gap-2">
                        <input
                            ref={fileInputRef}
                            type="file"
                            multiple
                            accept="application/pdf,image/*,.dxf,.stp,.step,.dwg"
                            className="hidden"
                            onChange={(e) => handleFileUpload(e.target.files)}
                            onClick={(e) => { (e.target as HTMLInputElement).value = ''; }}
                        />
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => fileInputRef.current?.click()}
                            disabled={sendet}
                        >
                            <Upload className="mr-1.5 h-3.5 w-3.5" />
                            Datei anhängen
                        </Button>
                        {hatProjekt && (
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setZeigeOrdner(v => !v)}
                                disabled={sendet}
                            >
                                <FolderOpen className="mr-1.5 h-3.5 w-3.5" />
                                Aus Projekt
                                {ausgewaehlteProjektIds.size > 0 && (
                                    <span className="ml-1.5 rounded-full bg-rose-100 px-1.5 py-0.5 text-xs font-medium text-rose-700">
                                        {ausgewaehlteProjektIds.size}
                                    </span>
                                )}
                            </Button>
                        )}
                    </div>

                    {/* Projektordner-Picker */}
                    {zeigeOrdner && hatProjekt && (
                        <div className="rounded-lg border border-slate-200 bg-white p-3">
                            <p className="mb-2 text-xs font-medium text-slate-500 uppercase tracking-wide">Projektdateien</p>
                            {ladeOrdner && (
                                <div className="flex items-center gap-2 text-sm text-slate-500">
                                    <Loader2 className="h-4 w-4 animate-spin" /> Dateien werden geladen…
                                </div>
                            )}
                            {!ladeOrdner && projektDokumente.length === 0 && (
                                <p className="text-sm text-slate-400">Keine Dateien im Projekt vorhanden.</p>
                            )}
                            {!ladeOrdner && projektDokumente.length > 0 && (
                                <div className="max-h-48 overflow-y-auto space-y-1 -mx-1 px-1">
                                    {projektDokumente.map(dok => {
                                        const ausgewaehlt = ausgewaehlteProjektIds.has(dok.id);
                                        const ladet = ladeProjektDateiIds.has(dok.id);
                                        return (
                                            <button
                                                key={dok.id}
                                                type="button"
                                                onClick={() => void handleProjektDateiToggle(dok)}
                                                disabled={ladet}
                                                className={`w-full flex items-center gap-2 rounded px-2 py-1.5 text-sm text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 ${
                                                    ausgewaehlt
                                                        ? 'bg-rose-50 text-rose-700 font-medium'
                                                        : 'text-slate-700 hover:bg-slate-50'
                                                }`}
                                            >
                                                {ladet
                                                    ? <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin text-slate-400" />
                                                    : ausgewaehlt
                                                        ? <Paperclip className="h-3.5 w-3.5 shrink-0 text-rose-500" />
                                                        : <FileIcon className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                                                }
                                                <span className="flex-1 truncate">{dok.originalDateiname}</span>
                                                {ausgewaehlt && <span className="text-xs text-rose-500">✓</span>}
                                            </button>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onSchliessen} disabled={sendet}>Später senden</Button>
                    <Button onClick={() => void senden()} disabled={sendet || !adresseOk || betreff.trim().length === 0}>
                        {sendet ? <Loader2 className="h-4 w-4 animate-spin" /> : <Mail className="h-4 w-4" />} E-Mail senden
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
