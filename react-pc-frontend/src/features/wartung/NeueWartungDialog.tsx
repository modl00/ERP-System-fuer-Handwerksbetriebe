import { useCallback, useEffect, useMemo, useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DatePicker } from '../../components/ui/datepicker';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { kundeName as bekannterKundenname, sucheKunden } from './kundenSuche';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { heuteIso } from '../../lib/datum';
import { ladeModule, ladeObjekte, legeProtokollAn } from './api';
import { INTERVALL_OPTIONEN } from './format';
import { ModulWahl } from './ModulWahl';
import { bereinigeAuswahl, fehlendeAngaben } from './modulAuswahl';
import { ObjektDialog } from './ObjektDialog';
import { TuerDialog } from './TuerDialog';
import type { ModulAuswahl, WartungModul, WartungObjekt, WartungProtokoll } from './types';

interface Vorbelegung {
    kundeId: number;
    kundeName: string;
    objektId?: number;
    tuerId?: number;
}

interface NeueWartungDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    vorbelegung?: Vorbelegung;
    onAngelegt: (protokoll: WartungProtokoll) => void;
}

interface Techniker {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
}

/**
 * Legt eine neue Wartung an: Kunde, Objekt und Tür wählen (oder gleich neu anlegen), dann was geprüft wird.
 * Das fertige Protokoll öffnet sich danach direkt zum Ausfüllen.
 */
export function NeueWartungDialog({ open, onOpenChange, vorbelegung, onAngelegt }: NeueWartungDialogProps) {
    const toast = useToast();
    const [module, setModule] = useState<WartungModul[]>([]);
    const [techniker, setTechniker] = useState<Techniker[]>([]);
    const [kundeId, setKundeId] = useState('');
    const [kundeName, setKundeName] = useState('');
    const [objekte, setObjekte] = useState<WartungObjekt[]>([]);
    const [objektId, setObjektId] = useState('');
    const [tuerId, setTuerId] = useState('');
    const [auswahl, setAuswahl] = useState<ModulAuswahl[]>([]);
    const [titel, setTitel] = useState('');
    const [datum, setDatum] = useState(heuteIso());
    const [intervall, setIntervall] = useState('');
    const [technikerId, setTechnikerId] = useState('');
    const [bemerkungen, setBemerkungen] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [versucht, setVersucht] = useState(false);
    const [objektDialog, setObjektDialog] = useState(false);
    const [tuerDialog, setTuerDialog] = useState(false);

    // Katalog und Techniker einmal laden, sobald der Dialog aufgeht.
    useEffect(() => {
        if (!open) return;
        let aktuell = true;
        ladeModule().then((m) => { if (aktuell) setModule(m); })
            .catch((e: Error) => toast.error(e.message));
        fetch('/api/mitarbeiter')
            .then((res) => (res.ok ? res.json() : []))
            .then((liste: Techniker[]) => { if (aktuell) setTechniker(Array.isArray(liste) ? liste.filter((m) => m.aktiv !== false) : []); })
            .catch(() => { if (aktuell) setTechniker([]); });
        return () => { aktuell = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open]);

    // Beim Öffnen auf den Ausgangszustand zurück, mit Vorbelegung von Kunde, Objekt und Tür.
    useEffect(() => {
        if (!open) return;
        setKundeId(vorbelegung ? String(vorbelegung.kundeId) : '');
        setKundeName(vorbelegung?.kundeName ?? '');
        setObjektId(vorbelegung?.objektId ? String(vorbelegung.objektId) : '');
        setTuerId(vorbelegung?.tuerId ? String(vorbelegung.tuerId) : '');
        setAuswahl([]);
        setTitel('');
        setDatum(heuteIso());
        setIntervall('');
        setTechnikerId('');
        setBemerkungen('');
        setVersucht(false);
    }, [open, vorbelegung]);

    const ladeObjekteFuerKunde = useCallback(async (id: string) => {
        if (id === '') { setObjekte([]); return; }
        try {
            setObjekte(await ladeObjekte(Number(id)));
        } catch (e) {
            setObjekte([]);
            toast.error(e instanceof Error ? e.message : 'Die Objekte konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => { if (open) void ladeObjekteFuerKunde(kundeId); }, [open, kundeId, ladeObjekteFuerKunde]);

    const objekt = objekte.find((o) => String(o.id) === objektId);
    const tuer = objekt?.tueren.find((t) => String(t.id) === tuerId);

    // Wird eine Tür gewählt, übernehmen wir deren Module und Intervall als Vorschlag.
    useEffect(() => {
        if (tuer && module.length > 0) {
            setAuswahl(bereinigeAuswahl(module, tuer.module));
            setIntervall(tuer.intervallMonate ? String(tuer.intervallMonate) : '');
        }
        // Nur bei Wechsel der Tür, nicht bei jeder Änderung der Auswahl.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tuer?.id, module.length]);

    const fehlt = useMemo(() => fehlendeAngaben(module, auswahl), [module, auswahl]);
    const kundeFehlt = kundeId === '';

    const speichern = async () => {
        setVersucht(true);
        if (kundeFehlt || fehlt.length > 0) return;
        setSpeichert(true);
        try {
            const protokoll = await legeProtokollAn({
                kundeId: Number(kundeId),
                objektId: objektId === '' ? null : Number(objektId),
                tuerId: tuerId === '' ? null : Number(tuerId),
                technikerId: technikerId === '' ? null : Number(technikerId),
                titel,
                datum,
                intervallMonate: intervall === '' ? null : Number(intervall),
                module: auswahl,
                bemerkungen,
            });
            toast.success(`Wartung ${protokoll.nummer} angelegt.`);
            onAngelegt(protokoll);
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Wartung konnte nicht angelegt werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <>
            <Dialog open={open} onOpenChange={onOpenChange}>
                <DialogContent className="sm:max-w-[760px]">
                    <DialogHeader>
                        <DialogTitle>Neue Wartung</DialogTitle>
                        <DialogDescription>
                            Wählen Sie, wo geprüft wird und was geprüft wird. Die Checkliste entsteht daraus automatisch.
                        </DialogDescription>
                    </DialogHeader>

                    {/* Die Kundenauswahl steht bewusst ausserhalb des scrollenden Bereichs: Ihre Trefferliste klappt
                        nach unten auf und wuerde sonst am Rand des Scrollbereichs abgeschnitten. */}
                    <div className="py-2">
                        <Label id="wartung-kunde-label">Kunde</Label>
                        <div role="group" aria-labelledby="wartung-kunde-label">
                            <SearchableSelect
                                value={kundeId}
                                selectedLabel={kundeName}
                                onChange={(wert) => { setKundeId(wert); setKundeName(bekannterKundenname(wert)); setObjektId(''); setTuerId(''); setAuswahl([]); }}
                                onSearch={sucheKunden}
                                placeholder="Kunde suchen und wählen"
                                searchPlaceholder="Name, Kundennummer oder Ort"
                            />
                        </div>
                        {versucht && kundeFehlt && <p role="alert" className="mt-1 text-sm text-rose-700">Bitte einen Kunden wählen.</p>}
                    </div>

                    <div className="flex flex-col gap-5 py-2 min-h-0 overflow-y-auto pr-1">
                        {kundeId !== '' && (
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div>
                                    <div className="flex items-center justify-between mb-1.5">
                                        <Label htmlFor="wartung-objekt" className="mb-0">Objekt</Label>
                                        <Button type="button" variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={() => setObjektDialog(true)}>
                                            <Plus className="w-3.5 h-3.5" aria-hidden="true" /> Neues Objekt
                                        </Button>
                                    </div>
                                    <Select
                                        id="wartung-objekt"
                                        value={objektId}
                                        onChange={(wert) => { setObjektId(wert); setTuerId(''); }}
                                        options={[{ value: '', label: 'Ohne Objekt' }, ...objekte.map((o) => ({ value: String(o.id), label: o.bezeichnung }))]}
                                    />
                                </div>
                                <div>
                                    <div className="flex items-center justify-between mb-1.5">
                                        <Label htmlFor="wartung-tuer" className="mb-0">Tür oder Tor</Label>
                                        <Button type="button" variant="ghost" size="sm" className="h-7 px-2 text-xs" disabled={!objekt} onClick={() => setTuerDialog(true)}
                                            title={objekt ? undefined : 'Bitte zuerst ein Objekt wählen'}>
                                            <Plus className="w-3.5 h-3.5" aria-hidden="true" /> Neue Tür
                                        </Button>
                                    </div>
                                    <Select
                                        id="wartung-tuer"
                                        value={tuerId}
                                        onChange={setTuerId}
                                        disabled={!objekt}
                                        options={[{ value: '', label: objekt ? 'Ohne feste Tür' : 'Erst ein Objekt wählen' }, ...(objekt?.tueren ?? []).map((t) => ({ value: String(t.id), label: t.bezeichnung }))]}
                                    />
                                </div>
                            </div>
                        )}

                        {kundeId !== '' && (
                            <>
                                <ModulWahl module={module} auswahl={auswahl} onChange={setAuswahl} />
                                {versucht && fehlt.length > 0 && (
                                    <ul role="alert" className="text-sm text-rose-700 list-disc pl-5">
                                        {fehlt.map((f) => <li key={f}>{f}</li>)}
                                    </ul>
                                )}

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div>
                                        <Label htmlFor="wartung-titel">Titel (optional)</Label>
                                        <Input id="wartung-titel" value={titel} maxLength={200} placeholder="z. B. Jahreswartung 2026" onChange={(e) => setTitel(e.target.value)} />
                                    </div>
                                    <div>
                                        <Label id="wartung-datum-label">Datum</Label>
                                        <div role="group" aria-labelledby="wartung-datum-label">
                                            <DatePicker value={datum} onChange={setDatum} placeholder="Datum wählen" />
                                        </div>
                                    </div>
                                    <div>
                                        <Label htmlFor="wartung-techniker">Techniker</Label>
                                        <Select
                                            id="wartung-techniker"
                                            value={technikerId}
                                            onChange={setTechnikerId}
                                            options={[{ value: '', label: 'Noch offen' }, ...techniker.map((m) => ({ value: String(m.id), label: `${m.vorname} ${m.nachname}`.trim() }))]}
                                        />
                                    </div>
                                    <div>
                                        <Label htmlFor="wartung-intervall">Nächste Prüfung in</Label>
                                        <Select
                                            id="wartung-intervall"
                                            value={intervall}
                                            onChange={setIntervall}
                                            options={[
                                                { value: '', label: 'Wie an der Tür bzw. im Modul' },
                                                ...INTERVALL_OPTIONEN.map((m) => ({ value: String(m), label: m === 1 ? '1 Monat' : `${m} Monaten` })),
                                            ]}
                                        />
                                    </div>
                                </div>

                                <div>
                                    <Label htmlFor="wartung-bemerkungen">Bemerkung (optional)</Label>
                                    <textarea
                                        id="wartung-bemerkungen"
                                        className="w-full min-h-[70px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        value={bemerkungen}
                                        maxLength={10000}
                                        onChange={(e) => setBemerkungen(e.target.value)}
                                    />
                                </div>
                            </>
                        )}
                    </div>

                    <DialogFooter>
                        <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} disabled={speichert}>Abbrechen</Button>
                        <Button size="sm" onClick={speichern} disabled={speichert || kundeFehlt}
                            title={kundeFehlt ? 'Bitte zuerst einen Kunden wählen' : undefined}>
                            {speichert && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                            {speichert ? 'Legt an...' : 'Wartung anlegen'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {kundeId !== '' && (
                <ObjektDialog
                    open={objektDialog}
                    onOpenChange={setObjektDialog}
                    kundeId={Number(kundeId)}
                    onGespeichert={(neu) => { setObjekte((alt) => [...alt, { ...neu, tueren: [] }]); setObjektId(String(neu.id)); setTuerId(''); }}
                />
            )}
            {objekt && (
                <TuerDialog
                    open={tuerDialog}
                    onOpenChange={setTuerDialog}
                    objektId={objekt.id}
                    module={module}
                    onGespeichert={(neu) => {
                        setObjekte((alt) => alt.map((o) => (o.id === objekt.id ? { ...o, tueren: [...o.tueren, neu] } : o)));
                        setTuerId(String(neu.id));
                    }}
                />
            )}
        </>
    );
}
