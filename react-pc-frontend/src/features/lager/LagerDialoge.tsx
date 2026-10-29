import { useEffect, useState } from 'react';
import { ExternalLink, Loader2, Package, Save } from 'lucide-react';
import { Link } from 'react-router-dom';

import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { SearchableSelect } from '../../components/ui/searchable-select';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { HandelseinheitEingabe } from '../../components/HandelseinheitEingabe';
import { LagerArtikelWahl, type GewaehlterArtikel } from './LagerArtikelWahl';
import {
    aendereArtikel, buche, EINHEIT_AUSWAHL, einheitAusKuerzel, einheitSchluesselAusKuerzel, ladeVerlauf, mengeText, setzeMindestbestand, umbuche,
    type Buchungsart, type LagerBestand, type LagerBuchung, type Lagerort,
} from './lagerApi';

const MAX_MENGE = 10_000_000;

const ART_TEXT: Record<Buchungsart, { name: string; mengeLabel: string; hilfe: string }> = {
    ZUGANG: { name: 'Zugang', mengeLabel: 'Menge (eingegangen)', hilfe: 'Ware ist angekommen, zum Beispiel laut Lieferschein.' },
    ABGANG: { name: 'Entnahme', mengeLabel: 'Menge (entnommen)', hilfe: 'Ware wurde aus dem Lager genommen.' },
    KORREKTUR: { name: 'Inventur', mengeLabel: 'Gezählter Bestand', hilfe: 'Tragen Sie ein, was Sie tatsächlich gezählt haben. Die Differenz wird gebucht.' },
};

interface ProjektKurz { id: number; bauvorhaben?: string; auftragsnummer?: string }

export interface BuchungsDialogProps {
    offen: boolean;
    /** Voreingestellte Buchungsart beim Öffnen */
    art: Buchungsart;
    /** Voreingestellter Artikel (aus der Zeile), sonst wird einer gewählt */
    artikel: LagerBestand | null;
    orte: Lagerort[];
    onSchliessen: () => void;
    onGebucht: (zeile: LagerBestand, art: Buchungsart) => void;
}

/** Zugang, Entnahme oder Inventur buchen. */
export function BuchungsDialog({ offen, art: startArt, artikel: startArtikel, orte, onSchliessen, onGebucht }: BuchungsDialogProps) {
    const toast = useToast();
    const aktiveOrte = orte.filter(o => o.aktiv);
    const [art, setArt] = useState<Buchungsart>(startArt);
    const [artikel, setArtikel] = useState<{ id: number; titel: string; nummer: string | null; einheit: string; einheitLang: string; orte: LagerBestand['orte']; kgProMeter?: number | null; handelseinheitBezeichnung?: string | null; handelseinheitMeter?: number | null; handelseinheitVarianten?: string | null } | null>(null);
    const [waehlt, setWaehlt] = useState(false);
    const [ortId, setOrtId] = useState('');
    const [menge, setMenge] = useState('');
    const [notiz, setNotiz] = useState('');
    const [projektId, setProjektId] = useState('');
    const [projekte, setProjekte] = useState<ProjektKurz[] | null>(null);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    // Beim Öffnen frisch beginnen
    useEffect(() => {
        if (!offen) return;
        setArt(startArt);
        setArtikel(startArtikel ? { id: startArtikel.artikelId, titel: startArtikel.bezeichnung, nummer: startArtikel.artikelnummer, einheit: startArtikel.einheit, einheitLang: einheitAusKuerzel(startArtikel.einheit).lang, orte: startArtikel.orte, kgProMeter: startArtikel.kgProMeter ?? null, handelseinheitBezeichnung: startArtikel.handelseinheitBezeichnung ?? null, handelseinheitMeter: startArtikel.handelseinheitMeter ?? null, handelseinheitVarianten: startArtikel.handelseinheitVarianten ?? null } : null);
        setWaehlt(!startArtikel);
        // Der Lagerort beginnt dort, wo der Artikel liegt (sonst der erste aktive)
        const vorort = startArtikel?.orte.find(o => aktiveOrte.some(a => a.id === o.lagerortId))?.lagerortId;
        setOrtId(String(vorort ?? aktiveOrte[0]?.id ?? ''));
        setMenge('');
        setNotiz('');
        setProjektId('');
        setFehler('');
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [offen]);

    // Die Projektliste nur laden, wenn sie gebraucht wird (Entnahme)
    useEffect(() => {
        if (!offen || art !== 'ABGANG' || projekte) return;
        let abgebrochen = false;
        fetch('/api/projekte/simple?size=500')
            .then(res => (res.ok ? res.json() : []))
            .then((daten: unknown) => {
                const liste = Array.isArray(daten) ? daten : ((daten as { content?: unknown[] })?.content ?? []);
                if (!abgebrochen) setProjekte((liste as ProjektKurz[]).filter(p => p && typeof p.id === 'number'));
            })
            .catch(() => { if (!abgebrochen) setProjekte([]); });
        return () => { abgebrochen = true; };
    }, [offen, art, projekte]);

    const artikelGewaehlt = (a: GewaehlterArtikel) => {
        setArtikel({ id: a.id, titel: a.titel, nummer: a.nummer, einheit: a.einheit.kurz, einheitLang: a.einheit.lang, orte: [], kgProMeter: a.kgProMeter ?? null, handelseinheitBezeichnung: a.handelseinheitBezeichnung ?? null, handelseinheitMeter: a.handelseinheitMeter ?? null, handelseinheitVarianten: a.handelseinheitVarianten ?? null });
        setWaehlt(false);
        setFehler('');
    };

    const gebucht = artikel?.orte.find(o => String(o.lagerortId) === ortId)?.bestand ?? 0;

    const speichern = async () => {
        setFehler('');
        if (!artikel || waehlt) { setFehler('Bitte einen Artikel wählen.'); return; }
        if (!ortId) { setFehler('Bitte einen Lagerort wählen.'); return; }
        const pruefung = validateDecimalInput(menge, {
            label: ART_TEXT[art].mengeLabel, required: true, min: art === 'KORREKTUR' ? 0 : 0.001, max: MAX_MENGE,
        });
        if (!pruefung.valid) { setFehler(pruefung.message); return; }
        setSpeichert(true);
        try {
            const zeile = await buche({
                art, artikelId: artikel.id, lagerortId: Number(ortId), menge: pruefung.value as number,
                projektId: art === 'ABGANG' && projektId ? Number(projektId) : null, notiz: notiz.trim() || null,
            });
            onGebucht(zeile, art);
            onSchliessen();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Die Buchung konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <>
            <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
                <DialogContent className="sm:max-w-[520px]">
                    <DialogHeader>
                        <DialogTitle>Lagerbuchung</DialogTitle>
                        <DialogDescription>{ART_TEXT[art].hilfe}</DialogDescription>
                    </DialogHeader>

                    <div role="tablist" aria-label="Art der Buchung" className="grid grid-cols-3 gap-1 rounded-lg bg-slate-100 p-1">
                        {(['ZUGANG', 'ABGANG', 'KORREKTUR'] as Buchungsart[]).map(a => (
                            <button key={a} type="button" role="tab" aria-selected={art === a}
                                className={cn('rounded-md px-3 py-1.5 text-sm font-medium cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300',
                                    art === a ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-900')}
                                onClick={() => { setArt(a); setFehler(''); }}>
                                {ART_TEXT[a].name}
                            </button>
                        ))}
                    </div>

                    <div className="space-y-3">
                        <div>
                            <Label>Artikel</Label>
                            {artikel && !waehlt ? (
                                <div className="flex items-center gap-2">
                                    <div className="flex-1 min-h-9 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-900" data-testid="lager-artikel">
                                        {artikel.titel}{artikel.nummer ? <span className="text-slate-500"> · {artikel.nummer}</span> : null}
                                        <span className="ml-2 rounded-full bg-white border border-slate-200 px-2 py-0.5 text-xs text-slate-700">{artikel.einheitLang} ({artikel.einheit})</span>
                                    </div>
                                    <Button type="button" variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50 shrink-0" onClick={() => setWaehlt(true)}>
                                        <Package className="w-4 h-4" /> Ändern
                                    </Button>
                                </div>
                            ) : (
                                <LagerArtikelWahl onWaehlen={artikelGewaehlt} />
                            )}
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div>
                                <Label>Lagerort</Label>
                                <Select value={ortId} onChange={setOrtId} placeholder="Lagerort wählen"
                                    options={aktiveOrte.map(o => ({ value: String(o.id), label: o.name }))} />
                            </div>
                            <div>
                                {artikel && artikel.kgProMeter && artikel.handelseinheitBezeichnung && artikel.handelseinheitMeter ? (
                                    <HandelseinheitEingabe
                                        artikel={{ einheit: artikel.einheit, kgProMeter: artikel.kgProMeter, handelseinheitBezeichnung: artikel.handelseinheitBezeichnung, handelseinheitMeter: artikel.handelseinheitMeter, handelseinheitVarianten: artikel.handelseinheitVarianten }}
                                        value={menge}
                                        onChange={setMenge}
                                        label={ART_TEXT[art].mengeLabel}
                                        min={art === 'KORREKTUR' ? 0 : 0.001}
                                        max={MAX_MENGE}
                                    />
                                ) : (
                                    <DecimalInput label={`${ART_TEXT[art].mengeLabel}${artikel ? ` in ${artikel.einheitLang} (${artikel.einheit})` : ''}`} value={menge} min={art === 'KORREKTUR' ? 0 : 0.001} max={MAX_MENGE}
                                        onChange={setMenge} placeholder={art === 'KORREKTUR' ? 'z. B. 42' : 'z. B. 5'} />
                                )}
                                {art === 'KORREKTUR' && artikel && artikel.orte.length > 0 && (
                                    <p className="mt-1 text-xs text-slate-500">Aktuell gebucht: {mengeText(gebucht, artikel.einheit)}</p>
                                )}
                            </div>
                        </div>

                        {art === 'ABGANG' && (
                            <div>
                                <Label>Für Projekt (optional)</Label>
                                <SearchableSelect value={projektId} onChange={setProjektId} placeholder="Kein Projekt" searchPlaceholder="Projekt suchen…"
                                    options={(projekte ?? []).map(p => ({
                                        value: String(p.id),
                                        label: [p.auftragsnummer, p.bauvorhaben].filter(Boolean).join(' – ') || `Projekt ${p.id}`,
                                    }))} />
                            </div>
                        )}

                        <div>
                            <Label>Notiz (optional)</Label>
                            <Input value={notiz} maxLength={255} onChange={e => setNotiz(e.target.value)}
                                placeholder={art === 'ZUGANG' ? 'z. B. Lieferschein-Nr.' : art === 'KORREKTUR' ? 'z. B. Inventur 2026' : 'z. B. für Montage Halle 2'} />
                        </div>
                    </div>

                    {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

                    <DialogFooter className="gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                        <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                            {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} {ART_TEXT[art].name} buchen
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}

export interface MindestbestandDialogProps {
    artikel: LagerBestand | null;
    onSchliessen: () => void;
    onGespeichert: (zeile: LagerBestand) => void;
}

/** Mindestbestand eines Artikels setzen oder entfernen. */
export function MindestbestandDialog({ artikel, onSchliessen, onGespeichert }: MindestbestandDialogProps) {
    const toast = useToast();
    const [wert, setWert] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        setWert(artikel?.mindestbestand != null ? formatDecimalInput(artikel.mindestbestand) : '');
        setFehler('');
    }, [artikel]);

    const speichern = async () => {
        if (!artikel) return;
        const pruefung = validateDecimalInput(wert, { label: 'Mindestbestand', required: false, min: 0, max: MAX_MENGE });
        if (!pruefung.valid) { setFehler(pruefung.message); return; }
        setSpeichert(true);
        try {
            const zeile = await setzeMindestbestand(artikel.artikelId, pruefung.value);
            toast.success(pruefung.value == null ? 'Mindestbestand entfernt.' : 'Mindestbestand gespeichert.');
            onGespeichert(zeile);
            onSchliessen();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Der Mindestbestand konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={artikel != null} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[420px]">
                <DialogHeader>
                    <DialogTitle>Mindestbestand</DialogTitle>
                    <DialogDescription>{artikel?.bezeichnung}</DialogDescription>
                </DialogHeader>
                <DecimalInput label={`Mindestbestand in ${artikel?.einheit ?? ''}`} value={wert} min={0} max={MAX_MENGE} onChange={setWert} placeholder="leer = kein Mindestbestand" />
                <p className="text-xs text-slate-500">Fällt der Bestand darunter, markiert die Liste den Artikel, damit rechtzeitig nachbestellt wird. Leer lassen entfernt den Mindestbestand.</p>
                {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                    <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                        {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Speichern
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export interface VerlaufDialogProps {
    artikel: LagerBestand | null;
    onSchliessen: () => void;
}

const datum = (iso: string) => new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

/** Alle Buchungen eines Artikels, neueste zuerst. */
export function VerlaufDialog({ artikel, onSchliessen }: VerlaufDialogProps) {
    return artikel ? <VerlaufInhalt key={artikel.artikelId} artikel={artikel} onSchliessen={onSchliessen} /> : null;
}

function VerlaufInhalt({ artikel, onSchliessen }: { artikel: LagerBestand; onSchliessen: () => void }) {
    const [buchungen, setBuchungen] = useState<LagerBuchung[] | null>(null);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let abgebrochen = false;
        ladeVerlauf(artikel.artikelId)
            .then(b => { if (!abgebrochen) setBuchungen(b); })
            .catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Der Verlauf konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [artikel]);

    return (
        <Dialog open onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[760px]">
                <DialogHeader>
                    <DialogTitle>Verlauf</DialogTitle>
                    <DialogDescription>{artikel.bezeichnung} — aktueller Bestand {mengeText(artikel.bestand, artikel.einheit)}</DialogDescription>
                </DialogHeader>
                {fehler ? (
                    <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>
                ) : buchungen == null ? (
                    <div className="flex items-center gap-2 text-sm text-slate-500 py-6"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Wird geladen…</div>
                ) : buchungen.length === 0 ? (
                    <p className="text-sm text-slate-500 py-6">Noch keine Buchungen.</p>
                ) : (
                    <div className="max-h-[50vh] overflow-auto -m-1 p-1">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
                                    <th scope="col" className="py-2 pr-3 font-medium">Datum</th>
                                    <th scope="col" className="py-2 pr-3 font-medium">Art</th>
                                    <th scope="col" className="py-2 pr-3 font-medium text-right">Menge</th>
                                    <th scope="col" className="py-2 pr-3 font-medium">Lagerort</th>
                                    <th scope="col" className="py-2 pr-3 font-medium">Bemerkung</th>
                                </tr>
                            </thead>
                            <tbody>
                                {buchungen.map(b => (
                                    <tr key={b.id} className="border-b border-slate-100 last:border-0 align-top">
                                        <td className="py-2 pr-3 whitespace-nowrap text-slate-600">{datum(b.gebuchtAm)}</td>
                                        <td className="py-2 pr-3">{b.art === 'ZUGANG' ? 'Zugang' : b.art === 'ABGANG' ? 'Entnahme' : 'Inventur'}</td>
                                        <td className={cn('py-2 pr-3 text-right tabular-nums font-medium', b.menge < 0 ? 'text-rose-700' : 'text-emerald-700')}>
                                            {b.menge > 0 ? '+' : ''}{mengeText(b.menge, artikel.einheit)}
                                        </td>
                                        <td className="py-2 pr-3">{b.lagerortName}</td>
                                        <td className="py-2 pr-3 text-slate-600">
                                            {[b.projekt ? `Projekt ${b.projekt}` : null, b.notiz, b.benutzer ? `(${b.benutzer})` : null].filter(Boolean).join(' · ')}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                <DialogFooter>
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Schließen</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export interface UmbuchungsDialogProps {
    artikel: LagerBestand | null;
    orte: Lagerort[];
    onSchliessen: () => void;
    onGebucht: (zeile: LagerBestand) => void;
}

/** Ware von einem Lagerort an einen anderen legen (zum Beispiel ins Montagefahrzeug). */
export function UmbuchungsDialog({ artikel, orte, onSchliessen, onGebucht }: UmbuchungsDialogProps) {
    const toast = useToast();
    const [von, setVon] = useState('');
    const [nach, setNach] = useState('');
    const [menge, setMenge] = useState('');
    const [notiz, setNotiz] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        const mitBestand = artikel?.orte.filter(o => o.bestand > 0) ?? [];
        setVon(mitBestand.length > 0 ? String(mitBestand[0].lagerortId) : '');
        setNach('');
        setMenge('');
        setNotiz('');
        setFehler('');
    }, [artikel]);

    const aktiveOrte = orte.filter(o => o.aktiv);
    const vorhanden = artikel?.orte.find(o => String(o.lagerortId) === von)?.bestand ?? 0;

    const buchen = async () => {
        if (!artikel) return;
        if (!von) { setFehler('Bitte wählen, woher die Ware kommt.'); return; }
        if (!nach) { setFehler('Bitte wählen, wohin die Ware soll.'); return; }
        if (von === nach) { setFehler('Bitte zwei verschiedene Lagerorte wählen.'); return; }
        const pruefung = validateDecimalInput(menge, { label: 'Menge', required: true, min: 0.001, max: MAX_MENGE });
        if (!pruefung.valid) { setFehler(pruefung.message); return; }
        if ((pruefung.value as number) > vorhanden) { setFehler(`Am Lagerort sind nur ${mengeText(vorhanden, artikel.einheit)} vorhanden.`); return; }
        setSpeichert(true);
        try {
            const zeile = await umbuche({ artikelId: artikel.artikelId, vonLagerortId: Number(von), nachLagerortId: Number(nach), menge: pruefung.value as number, notiz: notiz.trim() || null });
            toast.success('Umgebucht.');
            onGebucht(zeile);
            onSchliessen();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Die Umbuchung konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={artikel != null} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[460px]">
                <DialogHeader>
                    <DialogTitle>Umbuchen</DialogTitle>
                    <DialogDescription>{artikel?.bezeichnung}: Ware an einen anderen Lagerort legen. Der Gesamtbestand bleibt gleich.</DialogDescription>
                </DialogHeader>
                <div className="space-y-4">
                    <div>
                        <Label>Von Lagerort</Label>
                        <Select value={von} onChange={setVon} placeholder="Lagerort wählen"
                            options={(artikel?.orte ?? []).filter(o => o.bestand > 0).map(o => ({ value: String(o.lagerortId), label: `${o.lagerortName} (${mengeText(o.bestand, artikel?.einheit ?? '')})` }))} />
                    </div>
                    <div>
                        <Label>Nach Lagerort</Label>
                        <Select value={nach} onChange={setNach} placeholder="Lagerort wählen"
                            options={aktiveOrte.filter(o => String(o.id) !== von).map(o => ({ value: String(o.id), label: o.name }))} />
                    </div>
                    <DecimalInput label={`Menge in ${artikel?.einheit ?? ''}`} value={menge} min={0.001} max={MAX_MENGE} onChange={setMenge} placeholder="z. B. 5" />
                    <div>
                        <Label htmlFor="umbuchung-notiz">Notiz (optional)</Label>
                        <Input id="umbuchung-notiz" value={notiz} maxLength={150} onChange={e => setNotiz(e.target.value)} placeholder="z. B. für Montage Halle 3" />
                    </div>
                </div>
                {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                    <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={buchen} disabled={speichert}>
                        {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Umbuchen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export interface ArtikelDialogProps {
    artikel: LagerBestand | null;
    onSchliessen: () => void;
    onGespeichert: (zeile: LagerBestand) => void;
}

/** Bezeichnung und Einheit eines Artikels im Nachgang ändern. */
export function ArtikelDialog({ artikel, onSchliessen, onGespeichert }: ArtikelDialogProps) {
    const toast = useToast();
    const [name, setName] = useState('');
    const [einheit, setEinheit] = useState('STUECK');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        setName(artikel?.bezeichnung ?? '');
        setEinheit(artikel ? einheitSchluesselAusKuerzel(artikel.einheit) : 'STUECK');
        setFehler('');
    }, [artikel]);

    const geaendert = artikel != null && einheit !== einheitSchluesselAusKuerzel(artikel.einheit);

    const speichern = async () => {
        if (!artikel) return;
        if (!name.trim()) { setFehler('Bitte eine Bezeichnung angeben.'); return; }
        setSpeichert(true);
        try {
            const zeile = await aendereArtikel(artikel.artikelId, name.trim(), einheit);
            toast.success('Artikel gespeichert.');
            onGespeichert(zeile);
            onSchliessen();
        } catch (e) {
            const text = e instanceof Error ? e.message : 'Der Artikel konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={artikel != null} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[460px]">
                <DialogHeader>
                    <DialogTitle>Artikel bearbeiten</DialogTitle>
                    <DialogDescription>{artikel?.artikelnummer ? `Artikelnummer ${artikel.artikelnummer}` : 'Bezeichnung und Einheit ändern.'}</DialogDescription>
                </DialogHeader>
                <div className="space-y-4">
                    <div>
                        <Label htmlFor="artikel-bezeichnung">Bezeichnung</Label>
                        <Input id="artikel-bezeichnung" value={name} maxLength={255} onChange={e => setName(e.target.value)} />
                    </div>
                    <div>
                        <Label>Einheit</Label>
                        <Select value={einheit} onChange={setEinheit} placeholder="Einheit wählen"
                            options={EINHEIT_AUSWAHL.map(e => ({ value: e.schluessel, label: `${e.einheit.lang} (${e.einheit.kurz})` }))} />
                        {geaendert && (
                            <p className="mt-1 text-xs text-amber-700">Die vorhandenen Mengen werden nicht umgerechnet, sie gelten ab jetzt in der neuen Einheit.</p>
                        )}
                    </div>
                    {artikel && (
                        <Link to={`/artikel/${artikel.artikelId}`} className="inline-flex items-center gap-1.5 text-sm text-rose-700 hover:underline">
                            <ExternalLink className="w-3.5 h-3.5" aria-hidden="true" /> Preise, Lieferanten und Texte im Artikel öffnen
                        </Link>
                    )}
                    <p className="text-xs text-slate-500">Den Lagerort ändern Sie über „Umbuchen" in der Zeile des Artikels.</p>
                </div>
                {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                    <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                        {speichert ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Save className="w-4 h-4" />} Speichern
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
