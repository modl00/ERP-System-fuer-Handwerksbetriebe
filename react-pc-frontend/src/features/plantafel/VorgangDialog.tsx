import { useEffect, useMemo, useState } from 'react';
import { Loader2, Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { DatePicker } from '../../components/ui/datepicker';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { aendereVorgang, ladeLieferanten, ladePersonen, ladeRessourcen, legeVorgangAn, loescheVorgaenger, loescheVorgang, setzeFruehestens, setzeLieferung, setzeRang, setzeVorgaenger, weiseZu, type Auftrag, type Person, type Prioritaet, type Ressource, type Vorgang, type VorgangStatus, type Vorgangstyp } from './plantafelApi';
import { anzeigeDatum } from './zeitachse';
import { VerknuepfungsLinks } from './VerknuepfungsLinks';

interface Props {
    offen: boolean;
    auftrag: Auftrag;
    /** Leer = neuen Vorgang anlegen. */
    vorgang: Vorgang | null;
    typen: Vorgangstyp[];
    onSchliessen: () => void;
    onAuftrag: (a: Auftrag) => void;
    /** Aktionen für den ganzen Auftrag (Vorgang hinzufügen, Termine, Dauern, Termin erklären); ohne sie gibt es die Zeile nicht. */
    onAuftragAktion?: (aktion: AuftragAktion) => void;
    /** Nur ansehen (Bearbeiten-Modus der Plantafel ist aus): Die Felder sind gesperrt, „Bearbeiten“ schaltet den Modus ein. */
    nurAnsehen?: boolean;
    onBearbeiten?: () => void;
}

export type AuftragAktion = 'vorgang-neu' | 'termine' | 'dauern' | 'erklaeren';

const AUFTRAG_AKTIONEN: { aktion: AuftragAktion; text: string; hilfe: string }[] = [
    { aktion: 'vorgang-neu', text: 'Vorgang hinzufügen', hilfe: 'Einen weiteren Vorgang zu diesem Auftrag anlegen' },
    { aktion: 'termine', text: 'Termine ändern', hilfe: 'Start und Zieltermin des Auftrags ändern' },
    { aktion: 'dauern', text: 'Dauern aus dem Angebot', hilfe: 'Dauern aus der Arbeitszeit des Angebots vorschlagen' },
    { aktion: 'erklaeren', text: 'Termin erklären', hilfe: 'Die KI erklärt, wie die Lieferzeit zustande kommt' },
];

const HERKUNFT: Record<string, string> = { KALKULATION: 'aus der Kalkulation', GESCHAETZT: 'geschätzt', VON_HAND: 'von Hand eingetragen' };
const PRIORITAETEN: { wert: Prioritaet; text: string }[] = [{ wert: 'DRINGEND', text: 'Dringend' }, { wert: 'NORMAL', text: 'Normal (laufender Auftrag)' }, { wert: 'NICHT_DRINGEND', text: 'Nicht dringend' }];
const STATUS: { wert: VorgangStatus; text: string }[] = [{ wert: 'GEPLANT', text: 'Geplant' }, { wert: 'LAEUFT', text: 'Läuft' }, { wert: 'FERTIG', text: 'Fertig' }];

/** Der Stand des Vorgangs im (neu geladenen) Auftrag: Nach jedem Speichern ändert er sich, die nächste Anfrage muss ihn mitschicken. */
const standVon = (a: Auftrag, id: number) => a.vorgaenge.find(v => v.id === id)?.stand;
const stundenText = (h: number | null | undefined) => (h == null ? '' : String(h).replace('.', ','));
const alsStunden = (text: string) => Number(text.trim().replace(',', '.'));

/** Vorgang anlegen oder bearbeiten: Art, Aufwand in Stunden (Wartevorgang: Dauer in Arbeitstagen), frühester Beginn, fester Start, Status, Notiz und die Vorgänger, nach denen er beginnt. */
export function VorgangDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-2xl max-h-[90vh]" aria-labelledby="vorgang-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ auftrag, vorgang, typen, onSchliessen, onAuftrag, onAuftragAktion, nurAnsehen = false, onBearbeiten }: Props) {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const neu = vorgang == null;
    const aktiveTypen = useMemo(() => typen.filter(t => t.aktiv || t.id === vorgang?.typId), [typen, vorgang]);
    const [typId, setTypId] = useState<number | ''>(vorgang?.typId ?? aktiveTypen[0]?.id ?? '');
    const [bezeichnung, setBezeichnung] = useState(vorgang?.bezeichnung ?? '');
    const [dauer, setDauer] = useState(vorgang ? String(vorgang.dauerTage) : '');
    const [aufwand, setAufwand] = useState(stundenText(vorgang?.aufwandStunden));
    const [fruehestens, setFruehestens] = useState(vorgang?.fruehestensAb ?? '');
    const [rang, setRang] = useState(vorgang?.rang == null ? '' : String(vorgang.rang));
    const [festerStart, setFesterStart] = useState(vorgang?.festerStart ?? '');
    const [status, setStatus] = useState<VorgangStatus>(vorgang?.status ?? 'GEPLANT');
    const [notiz, setNotiz] = useState(vorgang?.notiz ?? '');
    const [vorgaenger, setVorgaenger] = useState<Record<number, string>>(() => Object.fromEntries((vorgang?.vorgaenger ?? []).map(d => [d.vorgaengerId, String(d.versatzTage)])));
    const [mitarbeiterIds, setMitarbeiterIds] = useState<number[]>(vorgang?.mitarbeiterIds ?? []);
    const [ressourceId, setRessourceId] = useState<number | ''>(vorgang?.ressourceId ?? '');
    const [prioritaet, setPrioritaet] = useState<Prioritaet>(vorgang?.prioritaet ?? 'NORMAL');
    const [lieferantId, setLieferantId] = useState<number | ''>(vorgang?.lieferantId ?? '');
    const [bestelltAm, setBestelltAm] = useState(vorgang?.bestelltAm ?? '');
    const [lieferTermin, setLieferTermin] = useState(vorgang?.lieferterminLieferant ?? '');
    const [lieferanten, setLieferanten] = useState<{ id: number; name: string; standardLieferzeitTage: number | null }[]>([]);
    const [personen, setPersonen] = useState<Person[]>([]);
    const [ressourcen, setRessourcen] = useState<Ressource[]>([]);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let abgebrochen = false;
        Promise.all([ladePersonen(), ladeRessourcen()]).then(([p, r]) => {
            if (abgebrochen) return;
            setPersonen(p);
            setRessourcen(r);
        }).catch(() => { /* ohne die Listen lässt sich der Vorgang trotzdem bearbeiten, nur nicht zuweisen */ });
        ladeLieferanten().then(l => { if (!abgebrochen) setLieferanten(l); }).catch(() => { /* ohne Liste lässt sich der Vorgang trotzdem bearbeiten */ });
        return () => { abgebrochen = true; };
    }, []);
    const sichtbareRessourcen = ressourcen.filter(x => x.aktiv || x.id === vorgang?.ressourceId);
    const mitarbeiterUmschalten = (id: number) => setMitarbeiterIds(alt => (alt.includes(id) ? alt.filter(x => x !== id) : [...alt, id]));

    const gewaehlteArt = typen.find(t => t.id === typId);
    const andere = auftrag.vorgaenge.filter(v => v.id !== vorgang?.id);

    const umschalten = (id: number) => setVorgaenger(alt => {
        const neuerStand = { ...alt };
        if (id in neuerStand) delete neuerStand[id]; else neuerStand[id] = '0';
        return neuerStand;
    });

    const wartet = gewaehlteArt?.wartet ?? false;

    const speichern = async () => {
        setFehler('');
        const tage = dauer.trim() === '' ? null : Number(dauer);
        if (wartet && tage != null && (!Number.isInteger(tage) || tage < 0)) {
            setFehler('Die Dauer muss eine ganze Zahl von Arbeitstagen sein (0 oder mehr).');
            return;
        }
        const stunden = aufwand.trim() === '' ? null : alsStunden(aufwand);
        if (!wartet && stunden != null && (!Number.isFinite(stunden) || stunden < 0)) {
            setFehler('Der Aufwand muss eine Zahl von Stunden sein (0 oder mehr), zum Beispiel 16 oder 7,5.');
            return;
        }
        const reihenfolge = rang.trim() === '' ? null : Number(rang);
        if (reihenfolge != null && (!Number.isInteger(reihenfolge) || reihenfolge < 0)) {
            setFehler('Die Reihenfolge muss eine ganze Zahl sein (1 = zuerst), oder leer bleiben.');
            return;
        }
        for (const wert of Object.values(vorgaenger)) {
            const p = Number(wert);
            if (!Number.isInteger(p) || p < 0) {
                setFehler('Der Puffer muss eine ganze Zahl von Arbeitstagen sein (0 oder mehr).');
                return;
            }
        }
        if (typId === '') {
            setFehler('Bitte eine Vorgangsart wählen.');
            return;
        }
        setSpeichert(true);
        try {
            const aufwandGeaendert = !wartet && stunden != null && stunden !== (vorgang?.aufwandStunden ?? null);
            const eingabe = {
                typId, bezeichnung: bezeichnung.trim() || null, dauerTage: wartet ? tage : null, aufwandStunden: aufwandGeaendert ? stunden : null,
                festerStart: festerStart || null, status, notiz, stand: vorgang?.stand,
            };
            let stand: Auftrag;
            let id: number;
            if (neu) {
                const vorher = new Set(auftrag.vorgaenge.map(v => v.id));
                stand = await legeVorgangAn(auftrag.projektId, eingabe);
                id = stand.vorgaenge.find(v => !vorher.has(v.id))?.id ?? -1;
            } else {
                // Änderung der Dauer nur senden, wenn sie wirklich anders ist, sonst bleibt "geschätzt" bestehen
                stand = await aendereVorgang(vorgang.id, { ...eingabe, dauerTage: wartet && tage != null && tage !== vorgang.dauerTage ? tage : null });
                id = vorgang.id;
            }
            if (fruehestens !== (vorgang?.fruehestensAb ?? '')) stand = await setzeFruehestens(id, fruehestens || null, standVon(stand, id));
            if (reihenfolge !== (vorgang?.rang ?? null)) stand = await setzeRang(id, reihenfolge, standVon(stand, id));
            const neuerLieferant = lieferantId === '' ? null : lieferantId;
            if (wartet && (neuerLieferant !== (vorgang?.lieferantId ?? null) || (bestelltAm || null) !== (vorgang?.bestelltAm ?? null) || (lieferTermin || null) !== (vorgang?.lieferterminLieferant ?? null))) {
                stand = await setzeLieferung(id, { lieferantId: neuerLieferant, bestelltAm: bestelltAm || null, lieferterminLieferant: lieferTermin || null }, standVon(stand, id));
            }
            const alteZuweisung = vorgang ? [...vorgang.mitarbeiterIds].sort().join(',') : '';
            const neueRessource = ressourceId === '' ? null : ressourceId;
            if (alteZuweisung !== [...mitarbeiterIds].sort().join(',') || neueRessource !== (vorgang?.ressourceId ?? null) || prioritaet !== (vorgang?.prioritaet ?? 'NORMAL')) {
                stand = await weiseZu(id, { mitarbeiterIds, ressourceId: neueRessource, prioritaet }, standVon(stand, id));
            }
            const vorher = Object.fromEntries((vorgang?.vorgaenger ?? []).map(d => [d.vorgaengerId, String(d.versatzTage)]));
            for (const [vorId, puffer] of Object.entries(vorgaenger)) {
                if (vorher[Number(vorId)] !== puffer) stand = await setzeVorgaenger(id, Number(vorId), Number(puffer));
            }
            for (const vorId of Object.keys(vorher)) {
                if (!(vorId in vorgaenger)) stand = await loescheVorgaenger(id, Number(vorId));
            }
            onAuftrag(stand);
            toast.success(neu ? 'Der Vorgang wurde angelegt.' : 'Der Vorgang wurde gespeichert.');
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Vorgang konnte nicht gespeichert werden.');
            setSpeichert(false);
        }
    };

    const loeschen = async () => {
        if (!vorgang || !await bestaetigen({ title: 'Vorgang löschen', message: `Soll „${vorgang.bezeichnung}“ aus der Plantafel entfernt werden? Die Termine der anderen Vorgänge werden neu gerechnet.`, variant: 'danger', confirmLabel: 'Löschen' })) return;
        setSpeichert(true);
        try {
            onAuftrag(await loescheVorgang(vorgang.id));
            toast.success('Der Vorgang wurde gelöscht.');
            onSchliessen();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Der Vorgang konnte nicht gelöscht werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="vorgang-titel">{neu ? 'Vorgang hinzufügen' : nurAnsehen ? 'Vorgang' : 'Vorgang bearbeiten'}</DialogTitle>
                <DialogDescription>{auftrag.bezeichnung}{auftrag.auftragsnummer ? ` (${auftrag.auftragsnummer})` : ''}</DialogDescription>
                <VerknuepfungsLinks projektId={auftrag.projektId} projektName={auftrag.bezeichnung} kundeId={auftrag.kundeId} kundeName={auftrag.kundeName} />
            </DialogHeader>
            {onAuftragAktion && !neu && (
                <div className="flex flex-wrap items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600" data-testid="auftrag-aktionen">
                    <span className="mr-1 font-medium text-slate-700">Lieferzeit {anzeigeDatum(auftrag.liefertermin)}</span>
                    {AUFTRAG_AKTIONEN.filter(a => !nurAnsehen || a.aktion === 'erklaeren').map(a => (
                        <Button key={a.aktion} type="button" variant="outline" size="sm" title={a.hilfe} onClick={() => onAuftragAktion(a.aktion)}>{a.text}</Button>
                    ))}
                </div>
            )}
            {/* Der Rahmen scrollt (ein fieldset kann das nicht zuverlässig), sonst käme man mit der Maus nicht bis ganz unten */}
            <div className="min-h-0 flex-1 overflow-y-auto py-1" data-testid="vorgang-scroll">
            <fieldset disabled={nurAnsehen} className="m-0 min-w-0 space-y-4 border-0 p-0" data-testid={nurAnsehen ? 'vorgang-ansehen' : undefined}>
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-1.5">
                        <Label htmlFor="vorgang-art">Art</Label>
                        <select id="vorgang-art" value={typId} onChange={e => setTypId(e.target.value === '' ? '' : Number(e.target.value))} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                            {aktiveTypen.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                        </select>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="vorgang-name">Bezeichnung</Label>
                        <input id="vorgang-name" value={bezeichnung} onChange={e => setBezeichnung(e.target.value)} maxLength={200} placeholder={gewaehlteArt?.name ?? ''} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                    </div>
                    {wartet ? (
                        <div className="space-y-1.5">
                            <Label htmlFor="vorgang-dauer">Dauer in Arbeitstagen</Label>
                            <input id="vorgang-dauer" inputMode="numeric" value={dauer} onChange={e => setDauer(e.target.value)} placeholder={gewaehlteArt ? `${gewaehlteArt.standardDauerTage} (Schätzwert der Art)` : ''} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                            <p className="text-xs text-slate-500">Ein Wartevorgang braucht keine Mitarbeiter, nur Zeit (zum Beispiel Lieferzeit). Ohne Wochenenden und Feiertage. 0 = ein Termin ohne Dauer.{vorgang ? ` Bisher: ${HERKUNFT[vorgang.dauerHerkunft] ?? vorgang.dauerHerkunft}.` : ''}</p>
                        </div>
                    ) : (
                        <div className="space-y-1.5">
                            <Label htmlFor="vorgang-aufwand">Aufwand in Stunden</Label>
                            <input id="vorgang-aufwand" inputMode="decimal" value={aufwand} onChange={e => setAufwand(e.target.value)} placeholder={gewaehlteArt ? `${gewaehlteArt.standardDauerTage} Tage mal Arbeitstag (Schätzwert der Art)` : ''} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                            {vorgang?.istStunden != null && vorgang.aufwandStunden != null && (
                                <p className="text-xs font-medium text-slate-700" data-testid="gestempelt">
                                    Schon gestempelt: {vorgang.istStunden.toLocaleString('de-DE', { maximumFractionDigits: 2 })} von {vorgang.aufwandStunden.toLocaleString('de-DE', { maximumFractionDigits: 2 })} Stunden
                                    {' '}(noch {Math.max(0, vorgang.aufwandStunden - vorgang.istStunden).toLocaleString('de-DE', { maximumFractionDigits: 2 })} offen). Nur der Rest wird verplant.
                                </p>
                            )}
                            <p className="text-xs text-slate-500">
                                Die Dauer ergibt sich aus Aufwand, Mitarbeitern und freier Zeit{vorgang ? ` (jetzt ${vorgang.dauerTage} Arbeitstage)` : ''}. Zwei Mitarbeiter schaffen doppelt so viel pro Tag.
                                {vorgang ? ` Bisher: ${HERKUNFT[vorgang.aufwandHerkunft] ?? vorgang.aufwandHerkunft}.` : ''}
                            </p>
                        </div>
                    )}
                    <div className="space-y-1.5">
                        <Label htmlFor="vorgang-status">Stand</Label>
                        <select id="vorgang-status" value={status} onChange={e => setStatus(e.target.value as VorgangStatus)} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                            {STATUS.map(s => <option key={s.wert} value={s.wert}>{s.text}</option>)}
                        </select>
                    </div>
                </div>

                <div className="space-y-1.5">
                    <Label htmlFor="vorgang-fest">Fester Start</Label>
                    <div className="flex items-center gap-2">
                        <div className="w-56"><DatePicker id="vorgang-fest" value={festerStart} onChange={setFesterStart} placeholder="Nicht festgelegt" aria-label="Fester Start" /></div>
                        {festerStart && <Button variant="outline" size="sm" onClick={() => setFesterStart('')}>Nicht festlegen</Button>}
                    </div>
                    <p className="text-xs text-slate-500">Leer: Das Programm rechnet den Start aus den Vorgängern. Mit festem Start verschiebt es den Vorgang nie, sondern meldet einen Konflikt, wenn die Vorgänger später fertig werden.</p>
                </div>

                {wartet && (
                    <fieldset className="space-y-2 rounded-lg border border-slate-200 p-3" data-testid="lieferung">
                        <legend className="px-1 text-sm font-medium text-slate-900">Lieferung</legend>
                        <div className="grid gap-4 sm:grid-cols-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="vorgang-lieferant">Lieferant</Label>
                                <select id="vorgang-lieferant" value={lieferantId} onChange={e => setLieferantId(e.target.value === '' ? '' : Number(e.target.value))} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                                    <option value="">Keiner</option>
                                    {lieferanten.map(l => <option key={l.id} value={l.id}>{l.name}{l.standardLieferzeitTage != null ? ` (üblich ${l.standardLieferzeitTage} Arbeitstage)` : ''}</option>)}
                                    {lieferantId !== '' && !lieferanten.some(l => l.id === lieferantId) && <option value={lieferantId}>{vorgang?.lieferantName ?? `Lieferant ${lieferantId}`}</option>}
                                </select>
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="vorgang-bestellt">Bestellt am</Label>
                                <DatePicker id="vorgang-bestellt" value={bestelltAm} onChange={setBestelltAm} placeholder="Noch nicht bestellt" aria-label="Bestellt am" />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="vorgang-liefertermin">Liefertermin laut Lieferant</Label>
                                <DatePicker id="vorgang-liefertermin" value={lieferTermin} onChange={setLieferTermin} placeholder="Nicht bestätigt" aria-label="Liefertermin laut Lieferant" />
                            </div>
                        </div>
                        <p className="text-xs text-slate-500">Die Lieferzeit zählt ab der Bestellung. Ohne eigene Dauer gilt die übliche Lieferzeit des Lieferanten; ein Termin aus seiner Auftragsbestätigung (hier von Hand eingetragen oder aus dem Dokument gelesen) ist das Ende der Lieferung.</p>
                        {vorgang?.hinweis && <p className="text-xs text-slate-700" data-testid="lieferhinweis">{vorgang.hinweis}</p>}
                    </fieldset>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-1.5">
                        <Label htmlFor="vorgang-fruehestens">Frühestens ab</Label>
                        <div className="flex items-center gap-2">
                            <div className="w-56"><DatePicker id="vorgang-fruehestens" value={fruehestens} onChange={setFruehestens} placeholder="Kein Wunschbeginn" aria-label="Frühestens ab" /></div>
                            {fruehestens && <Button variant="outline" size="sm" onClick={() => setFruehestens('')}>Aufheben</Button>}
                        </div>
                        <p className="text-xs text-slate-500">Der Vorgang beginnt nicht früher, rückt aber nach hinten, wenn die Vorgänger später fertig werden. So setzt auch das Ziehen mit der Maus den Beginn.</p>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="vorgang-rang">Reihenfolge bei gleicher Wichtigkeit</Label>
                        <input id="vorgang-rang" inputMode="numeric" value={rang} onChange={e => setRang(e.target.value)} placeholder="nicht entschieden" className="w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                        <p className="text-xs text-slate-500">1 = zuerst. Leer: Das Programm nimmt den früheren Zieltermin zuerst. Eine Person arbeitet nie an zwei Vorgängen zugleich.</p>
                    </div>
                </div>

                <fieldset className="space-y-1.5">
                    <legend className="text-sm font-medium text-slate-900">Beginnt nach dem Ende von</legend>
                    {andere.length === 0 ? <p className="text-sm text-slate-500">Es gibt noch keine anderen Vorgänge in diesem Auftrag.</p> : (
                        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                            {andere.map(v => (
                                <li key={v.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                                    <label className="flex min-w-0 flex-1 items-center gap-2">
                                        <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={v.id in vorgaenger} onChange={() => umschalten(v.id)} />
                                        <span className="truncate">{v.bezeichnung}</span>
                                        <span className="shrink-0 text-xs text-slate-500">endet {anzeigeDatum(v.ende)}</span>
                                    </label>
                                    {v.id in vorgaenger && (
                                        <label className="flex shrink-0 items-center gap-1.5 text-xs text-slate-600">
                                            Puffer
                                            <input inputMode="numeric" aria-label={`Puffer nach ${v.bezeichnung} in Arbeitstagen`} value={vorgaenger[v.id]} onChange={e => setVorgaenger(alt => ({ ...alt, [v.id]: e.target.value }))} className="w-14 rounded border border-slate-300 px-2 py-1 text-right text-sm" />
                                            Tg.
                                        </label>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                    <p className="text-xs text-slate-500">Der Puffer sind zusätzliche Arbeitstage zwischen Ende und Beginn, zum Beispiel für Trocknung oder Transport.</p>
                </fieldset>

                <fieldset className="space-y-2">
                    <legend className="text-sm font-medium text-slate-900">Wer macht es?</legend>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="vorgang-ressource">Ressource</Label>
                            <select id="vorgang-ressource" value={ressourceId} onChange={e => setRessourceId(e.target.value === '' ? '' : Number(e.target.value))} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                                <option value="">Keine</option>
                                {sichtbareRessourcen.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}
                            </select>
                        </div>
                        <div className="space-y-1.5">
                            <Label htmlFor="vorgang-prio">Wie wichtig</Label>
                            <select id="vorgang-prio" value={prioritaet} onChange={e => setPrioritaet(e.target.value as Prioritaet)} className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm">
                                {PRIORITAETEN.map(x => <option key={x.wert} value={x.wert}>{x.text}</option>)}
                            </select>
                        </div>
                    </div>
                    {personen.length > 0 && (
                        <ul className="grid gap-1 sm:grid-cols-2" aria-label="Mitarbeiter">
                            {personen.map(x => (
                                <li key={x.id}>
                                    <label className="flex items-center gap-2 text-sm">
                                        <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={mitarbeiterIds.includes(x.id)} onChange={() => mitarbeiterUmschalten(x.id)} />
                                        {x.name}
                                    </label>
                                </li>
                            ))}
                        </ul>
                    )}
                    {vorgang?.ohneZuweisungGeschaetzt && !wartet && (
                        <p className="rounded-lg bg-amber-50 p-3 text-xs text-amber-900" data-testid="geschaetzt">Noch niemand zugewiesen: Das Programm rechnet mit einer Person und vollem Arbeitstag. Der Termin ist geschätzt.</p>
                    )}
                    {vorgang?.hinweis && !wartet && (
                        <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-700" data-testid="vorgang-hinweis"><span className="font-medium">Warum später:</span> {vorgang.hinweis}</p>
                    )}
                    <p className="text-xs text-slate-500">Wer den Vorgang macht, bestimmt, wann er fertig wird: Das Programm zieht die freie Zeit der gewählten Mitarbeiter ab (Urlaub, Termine, Bürotage und andere Aufträge). Das Programm weist nie von selbst zu.</p>
                </fieldset>

                <div className="space-y-1.5">
                    <Label htmlFor="vorgang-notiz">Notiz</Label>
                    <textarea id="vorgang-notiz" value={notiz} onChange={e => setNotiz(e.target.value)} maxLength={1000} rows={2} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                </div>
            </fieldset>
            </div>
            <DialogFooter>
                {nurAnsehen && onBearbeiten && (
                    <>
                        <Button variant="outline" onClick={onSchliessen}>Schließen</Button>
                        <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={onBearbeiten}>Bearbeiten</Button>
                    </>
                )}
                {!nurAnsehen && !neu && <Button variant="outline" className="mr-auto border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => void loeschen()} disabled={speichert}><Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen</Button>}
                {!nurAnsehen && <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>}
                {!nurAnsehen && (
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={speichert}>
                        {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Speichern
                    </Button>
                )}
            </DialogFooter>
        </>
    );
}
