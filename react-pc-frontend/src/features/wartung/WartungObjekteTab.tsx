import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, ClipboardCheck, Copy, DoorClosed, Loader2, MapPin, Move, Pencil, Phone, Plus, Power } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { kundeName as bekannterKundenname, sucheKunden } from './kundenSuche';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { heuteIso } from '../../lib/datum';
import { cn } from '../../lib/utils';
import { kopiereTuer, ladeModule, ladeObjekte, setzeObjektAktiv, setzeTuerAktiv, verschiebeTuer } from './api';
import { formatDatum, istUeberfaellig, tageBis } from './format';
import { ObjektDialog } from './ObjektDialog';
import { TuerDialog } from './TuerDialog';
import type { WartungModul, WartungObjekt, WartungTuer } from './types';

/** Zeigt, wann die nächste Prüfung einer Tür fällig ist. Rose nur, wenn Handlungsbedarf besteht. */
function FaelligkeitsChip({ tuer }: { tuer: WartungTuer }) {
    const heute = heuteIso();
    if (!tuer.naechsteFaelligkeit) {
        return <span className="inline-flex rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-600">Noch keine Prüfung abgeschlossen</span>;
    }
    const tage = tageBis(tuer.naechsteFaelligkeit, heute);
    if (istUeberfaellig(tuer.naechsteFaelligkeit, heute)) {
        return <span className="inline-flex rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700">Überfällig seit {formatDatum(tuer.naechsteFaelligkeit)}</span>;
    }
    if (tage !== null && tage <= 30) {
        return <span className="inline-flex rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700">Fällig am {formatDatum(tuer.naechsteFaelligkeit)}</span>;
    }
    return <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">Nächste Prüfung {formatDatum(tuer.naechsteFaelligkeit)}</span>;
}

/** Fragt, an welches andere Objekt desselben Kunden die Tür verschoben werden soll. */
function VerschiebenDialog({ tuer, objekte, open, onOpenChange, onVerschoben }: {
    tuer: WartungTuer | null;
    objekte: WartungObjekt[];
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onVerschoben: () => void;
}) {
    const toast = useToast();
    const [ziel, setZiel] = useState('');
    const [laeuft, setLaeuft] = useState(false);
    useEffect(() => { if (open) setZiel(''); }, [open]);

    const optionen = objekte.filter((o) => o.aktiv && tuer && o.id !== tuer.objektId).map((o) => ({ value: String(o.id), label: o.bezeichnung }));

    const verschieben = async () => {
        if (!tuer || ziel === '') return;
        setLaeuft(true);
        try {
            await verschiebeTuer(tuer.id, Number(ziel));
            toast.success('Tür verschoben.');
            onVerschoben();
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Tür konnte nicht verschoben werden.');
        } finally {
            setLaeuft(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[440px]">
                <DialogHeader>
                    <DialogTitle>Tür verschieben</DialogTitle>
                    <DialogDescription>
                        „{tuer?.bezeichnung}“ wird mit allen bisherigen Protokollen an ein anderes Objekt desselben Kunden gehängt.
                    </DialogDescription>
                </DialogHeader>
                <div className="py-2">
                    <Label htmlFor="verschieben-ziel">Neues Objekt</Label>
                    <Select
                        id="verschieben-ziel"
                        value={ziel}
                        onChange={setZiel}
                        options={optionen}
                        placeholder={optionen.length === 0 ? 'Kein anderes Objekt vorhanden' : 'Objekt wählen'}
                        disabled={optionen.length === 0}
                    />
                </div>
                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} disabled={laeuft}>Abbrechen</Button>
                    <Button size="sm" onClick={verschieben} disabled={laeuft || ziel === ''} title={ziel === '' ? 'Bitte ein Objekt wählen' : undefined}>
                        {laeuft && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                        Verschieben
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

/** Reiter "Objekte und Türen": die Objekte eines Kunden mit ihren Türen, Wartungsplan und Schnellstart. */
export function WartungObjekteTab() {
    const toast = useToast();
    const confirm = useConfirm();
    const [kundeId, setKundeId] = useState('');
    const [kundeName, setKundeName] = useState('');
    const [mitInaktiven, setMitInaktiven] = useState(false);
    const [objekte, setObjekte] = useState<WartungObjekt[]>([]);
    const [module, setModule] = useState<WartungModul[]>([]);
    const [ladeStatus, setLadeStatus] = useState<'leer' | 'laedt' | 'ok' | 'fehler'>('leer');
    const [objektDialog, setObjektDialog] = useState<{ offen: boolean; objekt?: WartungObjekt }>({ offen: false });
    const [tuerDialog, setTuerDialog] = useState<{ offen: boolean; objektId?: number; tuer?: WartungTuer }>({ offen: false });
    const [verschieben, setVerschieben] = useState<WartungTuer | null>(null);
    const [aktionLaeuft, setAktionLaeuft] = useState<string | null>(null);

    useEffect(() => {
        ladeModule().then(setModule).catch((e: Error) => toast.error(e.message));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const laden = useCallback(async () => {
        if (kundeId === '') { setObjekte([]); setLadeStatus('leer'); return; }
        setLadeStatus('laedt');
        try {
            setObjekte(await ladeObjekte(Number(kundeId), mitInaktiven));
            setLadeStatus('ok');
        } catch (e) {
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Die Objekte konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kundeId, mitInaktiven]);

    useEffect(() => { void laden(); }, [laden]);

    const modulName = (modulId: number) => module.find((m) => m.id === modulId)?.name ?? 'Unbekanntes Modul';

    const fuehreAus = async (schluessel: string, aktion: () => Promise<unknown>, erfolg: string, fehler: string) => {
        setAktionLaeuft(schluessel);
        try {
            await aktion();
            toast.success(erfolg);
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : fehler);
        } finally {
            setAktionLaeuft(null);
        }
    };

    const tuerAktivieren = async (tuer: WartungTuer) => {
        if (tuer.aktiv) {
            const ja = await confirm({
                title: 'Tür deaktivieren?',
                message: `„${tuer.bezeichnung}“ erscheint dann nicht mehr bei neuen Wartungen. Die bisherigen Protokolle bleiben erhalten.`,
                confirmLabel: 'Deaktivieren',
                variant: 'warning',
            });
            if (!ja) return;
        }
        await fuehreAus(`tuer-${tuer.id}`, () => setzeTuerAktiv(tuer.id, !tuer.aktiv),
            tuer.aktiv ? 'Tür deaktiviert.' : 'Tür wieder aktiviert.', 'Die Tür konnte nicht geändert werden.');
    };

    const objektAktivieren = async (objekt: WartungObjekt) => {
        if (objekt.aktiv) {
            const ja = await confirm({
                title: 'Objekt deaktivieren?',
                message: `„${objekt.bezeichnung}“ erscheint dann nicht mehr bei neuen Wartungen. Die bisherigen Protokolle bleiben erhalten.`,
                confirmLabel: 'Deaktivieren',
                variant: 'warning',
            });
            if (!ja) return;
        }
        await fuehreAus(`objekt-${objekt.id}`, () => setzeObjektAktiv(objekt.id, !objekt.aktiv),
            objekt.aktiv ? 'Objekt deaktiviert.' : 'Objekt wieder aktiviert.', 'Das Objekt konnte nicht geändert werden.');
    };

    return (
        <div className="space-y-6">
            <Card className="p-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
                    <div>
                        <Label id="objekte-kunde-label">Kunde</Label>
                        <div role="group" aria-labelledby="objekte-kunde-label">
                            <SearchableSelect
                                value={kundeId}
                                selectedLabel={kundeName}
                                onChange={(wert) => { setKundeId(wert); setKundeName(bekannterKundenname(wert)); }}
                                onSearch={sucheKunden}
                                placeholder="Kunde suchen und wählen"
                                searchPlaceholder="Name, Kundennummer oder Ort"
                            />
                        </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <Button variant={mitInaktiven ? 'default' : 'outline'} size="sm" aria-pressed={mitInaktiven} onClick={() => setMitInaktiven((v) => !v)} disabled={kundeId === ''}>
                            Inaktive anzeigen
                        </Button>
                        <Button size="sm" onClick={() => setObjektDialog({ offen: true })} disabled={kundeId === ''}
                            title={kundeId === '' ? 'Bitte zuerst einen Kunden wählen' : undefined}>
                            <Plus className="w-4 h-4" aria-hidden="true" /> Objekt anlegen
                        </Button>
                    </div>
                </div>
            </Card>

            {ladeStatus === 'leer' && (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <Building2 className="mx-auto mb-2 h-6 w-6 text-slate-400" aria-hidden="true" />
                    <p>Wählen Sie einen Kunden, um seine Objekte und Türen zu sehen.</p>
                </div>
            )}

            {ladeStatus === 'laedt' && (
                <div className="flex flex-col gap-4" aria-busy="true" aria-label="Objekte werden geladen">
                    {[0, 1].map((i) => <div key={i} className="motion-safe:animate-pulse bg-white border border-slate-200 rounded-lg shadow-sm h-28" />)}
                </div>
            )}

            {ladeStatus === 'fehler' && (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <p>Die Objekte konnten nicht geladen werden.</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={() => void laden()}>Erneut versuchen</Button>
                </div>
            )}

            {ladeStatus === 'ok' && objekte.length === 0 && (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <p>Dieser Kunde hat noch keine Objekte. Legen Sie das erste Objekt an.</p>
                </div>
            )}

            {ladeStatus === 'ok' && objekte.map((objekt) => (
                <Card key={objekt.id} className={cn('p-4 sm:p-5', !objekt.aktiv && 'bg-slate-50')}>
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="flex min-w-0 items-start gap-3">
                            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600" aria-hidden="true">
                                <Building2 className="h-5 w-5" />
                            </span>
                            <div className="min-w-0">
                                <h3 className="text-lg font-bold text-slate-900 break-words">
                                    {objekt.bezeichnung}
                                    {!objekt.aktiv && <span className="ml-2 align-middle rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-medium text-slate-500">Inaktiv</span>}
                                </h3>
                                {(objekt.strasse || objekt.ort) && (
                                    <p className="mt-0.5 flex items-center gap-1 text-sm text-slate-500 break-words">
                                        <MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                        {[objekt.strasse, [objekt.plz, objekt.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ')}
                                    </p>
                                )}
                                {(objekt.ansprechpartnerName || objekt.ansprechpartnerTelefon) && (
                                    <p className="mt-0.5 flex items-center gap-1 text-sm text-slate-500 break-words">
                                        <Phone className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                        {[objekt.ansprechpartnerName, objekt.ansprechpartnerTelefon].filter(Boolean).join(' · ')}
                                    </p>
                                )}
                            </div>
                        </div>
                        <div className="flex flex-wrap gap-2 shrink-0">
                            <Button variant="outline" size="sm" onClick={() => setTuerDialog({ offen: true, objektId: objekt.id })} disabled={!objekt.aktiv}
                                title={objekt.aktiv ? undefined : 'Das Objekt ist inaktiv. Bitte zuerst wieder aktivieren.'}>
                                <Plus className="w-4 h-4" aria-hidden="true" /> Tür anlegen
                            </Button>
                            <Button variant="ghost" size="sm" aria-label={`Objekt ${objekt.bezeichnung} bearbeiten`} onClick={() => setObjektDialog({ offen: true, objekt })}>
                                <Pencil className="w-4 h-4" aria-hidden="true" /> Bearbeiten
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => objektAktivieren(objekt)} disabled={aktionLaeuft === `objekt-${objekt.id}`}>
                                <Power className="w-4 h-4" aria-hidden="true" /> {objekt.aktiv ? 'Deaktivieren' : 'Aktivieren'}
                            </Button>
                        </div>
                    </div>

                    <div className="mt-4 border-t border-slate-100 pt-3">
                        {objekt.tueren.length === 0 ? (
                            <p className="text-sm text-slate-500">Noch keine Türen oder Tore in diesem Objekt.</p>
                        ) : (
                            <ul className="flex flex-col gap-2">
                                {objekt.tueren.map((tuer) => (
                                    <li key={tuer.id} className={cn('rounded-lg border border-slate-200 p-3', !tuer.aktiv && 'bg-slate-50 opacity-80')}>
                                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                                            <div className="min-w-0">
                                                <p className="flex items-center gap-2 text-sm font-semibold text-slate-900 break-words">
                                                    <DoorClosed className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                                                    <Link to={`/wartung/anlagen/${tuer.id}`} className="rounded hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500">{tuer.bezeichnung}</Link>
                                                    {!tuer.aktiv && <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-medium text-slate-500">Inaktiv</span>}
                                                </p>
                                                <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                                                    {tuer.module.map((m) => (
                                                        <span key={m.modulId} className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-xs text-slate-600">
                                                            {modulName(m.modulId)}
                                                        </span>
                                                    ))}
                                                    {tuer.aktiv && <FaelligkeitsChip tuer={tuer} />}
                                                    {tuer.projektName && (
                                                        <span className="rounded border border-rose-100 bg-rose-50 px-1.5 py-0.5 text-xs text-rose-700">Projekt: {tuer.projektName}</span>
                                                    )}
                                                </div>
                                            </div>
                                            <div className="flex flex-wrap gap-1.5 shrink-0">
                                                <Link to={`/wartung/anlagen/${tuer.id}`} aria-label={`Wartung ${tuer.bezeichnung} öffnen`}
                                                    className="inline-flex items-center justify-center gap-2 rounded border border-rose-600 bg-rose-600 px-3 py-1.5 text-sm text-white transition-colors hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-1">
                                                    <ClipboardCheck className="w-4 h-4" aria-hidden="true" /> Wartung öffnen
                                                </Link>
                                                <Button variant="ghost" size="sm" aria-label={`Tür ${tuer.bezeichnung} bearbeiten`} onClick={() => setTuerDialog({ offen: true, objektId: objekt.id, tuer })}>
                                                    <Pencil className="w-4 h-4" aria-hidden="true" />
                                                </Button>
                                                <Button variant="ghost" size="sm" aria-label={`Tür ${tuer.bezeichnung} kopieren`} disabled={aktionLaeuft === `tuer-${tuer.id}`}
                                                    onClick={() => fuehreAus(`tuer-${tuer.id}`, () => kopiereTuer(tuer.id), 'Tür kopiert.', 'Die Tür konnte nicht kopiert werden.')}>
                                                    <Copy className="w-4 h-4" aria-hidden="true" />
                                                </Button>
                                                <Button variant="ghost" size="sm" aria-label={`Tür ${tuer.bezeichnung} verschieben`} onClick={() => setVerschieben(tuer)}>
                                                    <Move className="w-4 h-4" aria-hidden="true" />
                                                </Button>
                                                <Button variant="ghost" size="sm" aria-label={`Tür ${tuer.bezeichnung} ${tuer.aktiv ? 'deaktivieren' : 'aktivieren'}`}
                                                    disabled={aktionLaeuft === `tuer-${tuer.id}`} onClick={() => tuerAktivieren(tuer)}>
                                                    <Power className="w-4 h-4" aria-hidden="true" />
                                                </Button>
                                            </div>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </Card>
            ))}

            {kundeId !== '' && (
                <ObjektDialog
                    open={objektDialog.offen}
                    onOpenChange={(offen) => setObjektDialog((alt) => ({ ...alt, offen }))}
                    kundeId={Number(kundeId)}
                    objekt={objektDialog.objekt}
                    onGespeichert={() => void laden()}
                />
            )}
            {tuerDialog.objektId !== undefined && (
                <TuerDialog
                    open={tuerDialog.offen}
                    onOpenChange={(offen) => setTuerDialog((alt) => ({ ...alt, offen }))}
                    objektId={tuerDialog.objektId}
                    module={module}
                    tuer={tuerDialog.tuer}
                    onGespeichert={() => void laden()}
                />
            )}
            <VerschiebenDialog
                tuer={verschieben}
                objekte={objekte}
                open={verschieben !== null}
                onOpenChange={(offen) => { if (!offen) setVerschieben(null); }}
                onVerschoben={() => void laden()}
            />
        </div>
    );
}
