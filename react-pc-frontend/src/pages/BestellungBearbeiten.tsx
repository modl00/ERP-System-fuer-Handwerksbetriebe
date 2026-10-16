import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Boxes, Briefcase, CheckCheck, FileText, Loader2, Mail, PackagePlus, Phone, Save, Send, Trash2, X } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { ProjectSelectModal } from '../components/ProjectSelectModal';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useConfirm } from '../components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import { useDarf } from '../features/funktionsrechte/useFunktionsrechte';
import { LagerArtikelWahl, type GewaehlterArtikel } from '../features/lager/LagerArtikelWahl';
import { BestellMailDialog } from '../features/bestellwesen/BestellMailDialog';
import { LieferantWahl } from '../features/bestellwesen/LieferantWahl';
import { VorschlaegeDialog } from '../features/bestellwesen/VorschlaegeDialog';
import {
    BestellFehler, aendere, alleGeliefert, bestelle, datum, geld, legeAn, ladeBestellung, liefere, loesche, mengeText, pdfAdresse, storniere,
    vorschlaegeMindestbestand, vorschlaegeProjekt,
    type Bestellung, type Herkunft, type PositionEingabe, type Vorschlag,
} from '../features/bestellwesen/bestellwesenApi';
import { StandMarke } from './BestellListe';

/** Eine Zeile des Entwurfs, wie sie am Bildschirm bearbeitet wird (Menge als Text, damit "12," beim Tippen stehen bleibt). */
interface Zeile {
    key: number;
    artikelId: number;
    bezeichnung: string;
    artikelnummer: string | null;
    einheit: string;
    menge: string;
    einzelpreis: number | null;
    herkunft: Herkunft;
    projektId: number | null;
    projektName: string | null;
    artikelInProjektId: number | null;
    kommentar: string | null;
}

const HERKUNFT_TEXT: Record<Herkunft, string> = { LAGER: 'Lager auffüllen', PROJEKT: 'Projekt', FREI: 'Frei bestellt' };
const alsZahl = (t: string) => Number(t.trim().replace(',', '.'));

/** Eine Bestellung ansehen oder (als Entwurf) zusammenstellen, bestellen, per E-Mail senden, Lieferung eintragen und stornieren. */
const BestellungBearbeiten = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const toast = useToast();
    const confirm = useConfirm();
    const darfAnlegen = useDarf('BESTELLUNGEN', 'ANLEGEN') !== false;
    const darfAendern = useDarf('BESTELLUNGEN', 'AENDERN') !== false;
    const darfLoeschen = useDarf('BESTELLUNGEN', 'LOESCHEN') !== false;

    const bestellungId = id && id !== 'neu' ? Number(id) : null;
    const [bestellung, setBestellung] = useState<Bestellung | null>(null);
    const [laedt, setLaedt] = useState(bestellungId !== null);
    const [ladeFehler, setLadeFehler] = useState('');
    const [lieferant, setLieferant] = useState<{ id: number; name: string } | null>(null);
    const [notiz, setNotiz] = useState('');
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    const [fehler, setFehler] = useState('');
    const [beschaeftigt, setBeschaeftigt] = useState(false);
    const [vorschlag, setVorschlag] = useState<{ titel: string; beschreibung: string; liste: Vorschlag[] } | null>(null);
    const [projektWahl, setProjektWahl] = useState(false);
    const [bestellenOffen, setBestellenOffen] = useState(false);
    const [mailOffen, setMailOffen] = useState(false);
    const [stornoOffen, setStornoOffen] = useState(false);
    const [stornoGrund, setStornoGrund] = useState('');
    const [artikelMenge, setArtikelMenge] = useState('1');
    const zaehler = useRef(0);

    const istEntwurf = bestellung === null || bestellung.status === 'ENTWURF';

    const uebernimm = useCallback((b: Bestellung) => {
        setBestellung(b);
        setLieferant({ id: b.lieferantId, name: b.lieferantName });
        setNotiz(b.notiz ?? '');
        setZeilen(b.positionen.map((p) => ({
            key: ++zaehler.current, artikelId: p.artikelId as number, bezeichnung: p.bezeichnung, artikelnummer: p.artikelnummer, einheit: p.einheit,
            menge: mengeText(p.menge), einzelpreis: p.einzelpreis, herkunft: p.herkunft, projektId: p.projektId, projektName: p.projektName,
            artikelInProjektId: p.artikelInProjektId, kommentar: p.kommentar,
        })));
    }, []);

    useEffect(() => {
        if (bestellungId === null) return;
        ladeBestellung(bestellungId)
            .then(uebernimm)
            .catch((e: unknown) => setLadeFehler(e instanceof BestellFehler ? e.message : 'Die Bestellung konnte nicht geladen werden.'))
            .finally(() => setLaedt(false));
    }, [bestellungId, uebernimm]);

    const summe = useMemo(() => zeilen.reduce((s, z) => {
        const m = alsZahl(z.menge);
        return z.einzelpreis !== null && Number.isFinite(m) ? s + m * z.einzelpreis : s;
    }, 0), [zeilen]);

    const fuegeVorschlaegeHinzu = (gewaehlt: Vorschlag[]) => {
        const schon = new Set(zeilen.map((z) => z.artikelInProjektId).filter((x): x is number => x !== null));
        const neu = gewaehlt.filter((v) => v.artikelInProjektId === null || !schon.has(v.artikelInProjektId));
        const doppelt = gewaehlt.length - neu.length;
        setZeilen((z) => [...z, ...neu.map((v): Zeile => ({
            key: ++zaehler.current, artikelId: v.artikelId, bezeichnung: v.bezeichnung, artikelnummer: v.artikelnummer, einheit: v.einheit, menge: mengeText(v.menge),
            einzelpreis: v.einzelpreis, herkunft: v.herkunft, projektId: v.projektId, projektName: v.projektName, artikelInProjektId: v.artikelInProjektId,
            kommentar: v.kommentar,
        }))]);
        if (lieferant === null) {
            const mit = gewaehlt.find((v) => v.lieferantId !== null);
            if (mit) setLieferant({ id: mit.lieferantId as number, name: mit.lieferantName ?? '' });
        }
        if (doppelt > 0) toast.info(`${doppelt} ${doppelt === 1 ? 'Position steht' : 'Positionen stehen'} schon in der Bestellung.`);
        setVorschlag(null);
    };

    const zeigeVorschlaege = async (art: 'MINDESTBESTAND' | 'PROJEKT', projekt?: { id: number; bauvorhaben: string }) => {
        setFehler('');
        try {
            const liste = art === 'MINDESTBESTAND' ? await vorschlaegeMindestbestand() : await vorschlaegeProjekt((projekt as { id: number }).id);
            setVorschlag(art === 'MINDESTBESTAND'
                ? { titel: 'Lager auffüllen', beschreibung: 'Diese Artikel liegen unter ihrem Mindestbestand. Die Menge reicht bis zum Mindestbestand; was schon bestellt und unterwegs ist, ist abgezogen.', liste }
                : { titel: `Material für ${projekt?.bauvorhaben ?? 'das Projekt'}`, beschreibung: 'Material aus der Projektliste, das noch nicht bestellt ist. Material aus dem Lager steht hier nicht.', liste });
        } catch (e) {
            setFehler(e instanceof BestellFehler ? e.message : 'Die Vorschläge konnten nicht geladen werden.');
        }
    };

    const artikelHinzufuegen = (a: GewaehlterArtikel) => {
        const menge = alsZahl(artikelMenge);
        if (!Number.isFinite(menge) || menge <= 0) { setFehler('Bitte zuerst eine Menge größer als 0 eintragen.'); return; }
        setFehler('');
        setZeilen((z) => [...z, {
            key: ++zaehler.current, artikelId: a.id, bezeichnung: a.titel, artikelnummer: a.nummer, einheit: a.einheit.kurz, menge: mengeText(menge), einzelpreis: null,
            herkunft: 'FREI', projektId: null, projektName: null, artikelInProjektId: null, kommentar: null,
        }]);
    };

    /** Prüft die Eingaben und speichert den Entwurf (neu oder als Änderung). Gibt die gespeicherte Bestellung zurück, bei Fehlern null. */
    const speichern = async (): Promise<Bestellung | null> => {
        setFehler('');
        if (lieferant === null) { setFehler('Bitte einen Lieferanten wählen.'); return null; }
        if (zeilen.length === 0) { setFehler('Bitte mindestens eine Position hinzufügen.'); return null; }
        const positionen: PositionEingabe[] = [];
        for (const z of zeilen) {
            const menge = alsZahl(z.menge);
            if (!Number.isFinite(menge) || menge <= 0) { setFehler(`Bitte bei "${z.bezeichnung}" eine Menge größer als 0 eintragen.`); return null; }
            positionen.push({ artikelId: z.artikelId, menge, einheit: z.einheit, einzelpreis: z.einzelpreis, herkunft: z.herkunft, projektId: z.projektId, artikelInProjektId: z.artikelInProjektId, kommentar: z.kommentar });
        }
        setBeschaeftigt(true);
        try {
            const eingabe = { lieferantId: lieferant.id, notiz: notiz.trim() || null, positionen };
            const gespeichert = bestellung ? await aendere(bestellung.id, eingabe) : await legeAn(eingabe);
            uebernimm(gespeichert);
            if (!bestellung) navigate(`/bestellungen/bestellung/${gespeichert.id}`, { replace: true });
            return gespeichert;
        } catch (e) {
            setFehler(e instanceof BestellFehler ? e.message : 'Das Speichern hat nicht geklappt.');
            return null;
        } finally {
            setBeschaeftigt(false);
        }
    };

    const nurSpeichern = async () => {
        if (await speichern()) toast.success('Der Entwurf ist gespeichert.');
    };

    const bestellen = async (per: 'EMAIL' | 'ANDERS') => {
        setBestellenOffen(false);
        const gespeichert = await speichern();
        if (!gespeichert) return;
        setBeschaeftigt(true);
        try {
            const b = await bestelle(gespeichert.id, per);
            uebernimm(b);
            toast.success(`Bestellt: ${b.bestellnummer}`);
            if (per === 'EMAIL') setMailOffen(true);
        } catch (e) {
            setFehler(e instanceof BestellFehler ? e.message : 'Das Bestellen hat nicht geklappt.');
        } finally {
            setBeschaeftigt(false);
        }
    };

    const aktion = async (lauf: () => Promise<Bestellung>, erfolg?: string) => {
        setFehler('');
        setBeschaeftigt(true);
        try {
            uebernimm(await lauf());
            if (erfolg) toast.success(erfolg);
        } catch (e) {
            setFehler(e instanceof BestellFehler ? e.message : 'Das hat nicht geklappt.');
        } finally {
            setBeschaeftigt(false);
        }
    };

    const entwurfLoeschen = async () => {
        if (!bestellung) return;
        if (!(await confirm({ title: 'Entwurf löschen?', message: 'Der Entwurf wird gelöscht. Das lässt sich nicht rückgängig machen.', confirmLabel: 'Löschen', variant: 'danger' }))) return;
        try {
            await loesche(bestellung.id);
            toast.success('Der Entwurf ist gelöscht.');
            navigate('/bestellungen/liste');
        } catch (e) {
            setFehler(e instanceof BestellFehler ? e.message : 'Das Löschen hat nicht geklappt.');
        }
    };

    const stornieren = async () => {
        if (!bestellung) return;
        if (stornoGrund.trim().length < 3) { setFehler('Bitte kurz sagen, warum die Bestellung storniert wird.'); return; }
        setStornoOffen(false);
        await aktion(() => storniere(bestellung.id, stornoGrund.trim()), 'Die Bestellung ist storniert.');
        setStornoGrund('');
    };

    const titel = bestellung?.bestellnummer ? `BESTELLUNG ${bestellung.bestellnummer}` : bestellung ? 'BESTELLUNG (ENTWURF)' : 'NEUE BESTELLUNG';

    if (laedt) {
        return <PageLayout ribbonCategory="Einkauf" title="BESTELLUNG"><p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Wird geladen …</p></PageLayout>;
    }
    if (ladeFehler) {
        return (
            <PageLayout ribbonCategory="Einkauf" title="BESTELLUNG">
                <p role="alert" className="rounded-lg bg-rose-50 p-3 text-rose-800">{ladeFehler}</p>
                <Link to="/bestellungen/liste" className="mt-3 inline-flex items-center gap-1 text-sm text-rose-700 hover:underline"><ArrowLeft className="h-4 w-4" /> Zur Übersicht</Link>
            </PageLayout>
        );
    }

    return (
        <PageLayout
            ribbonCategory="Einkauf"
            title={titel}
            subtitle={istEntwurf ? 'Positionen aus dem Lager, aus einem Projekt oder frei aus dem Artikelstamm. Erst beim Bestellen bekommt die Bestellung ihre Nummer.' : undefined}
            actions={bestellung && (
                <div className="flex flex-wrap items-center gap-2">
                    <StandMarke b={bestellung} />
                    <a href={pdfAdresse(bestellung.id)} target="_blank" rel="noreferrer"><Button variant="outline" size="sm"><FileText className="h-4 w-4" /> PDF ansehen</Button></a>
                </div>
            )}
        >
            <div className="space-y-4">
                <Link to="/bestellungen/liste" className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-rose-700"><ArrowLeft className="h-4 w-4" /> Alle Bestellungen</Link>

                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}

                {bestellung && bestellung.status === 'STORNIERT' && (
                    <p className="rounded-lg bg-slate-100 p-3 text-sm text-slate-700">Storniert am {datum(bestellung.storniertAm)}: {bestellung.stornoGrund}</p>
                )}
                {bestellung?.ueberfaellig && (
                    <p className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">Diese Bestellung ist seit {bestellung.tageSeitBestellung} Tagen bestellt und noch nicht vollständig geliefert. Beim Lieferanten nachfragen?</p>
                )}

                <Card className="space-y-4 p-4">
                    <div className="grid gap-4 md:grid-cols-2">
                        <div>
                            <p className="mb-1 text-sm font-medium text-slate-700">Lieferant</p>
                            <LieferantWahl lieferantId={lieferant?.id ?? null} lieferantName={lieferant?.name ?? ''} onWaehlen={setLieferant} disabled={!istEntwurf} />
                        </div>
                        <div>
                            <label htmlFor="bestell-notiz" className="mb-1 block text-sm font-medium text-slate-700">Hinweis für den Lieferanten (steht auf dem Formular)</label>
                            {istEntwurf ? (
                                <textarea id="bestell-notiz" rows={2} maxLength={1000} value={notiz} onChange={(e) => setNotiz(e.target.value)} placeholder="Zum Beispiel: Bitte Anfang der Woche liefern"
                                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                            ) : <p className="px-1 py-2 text-slate-800">{bestellung?.notiz || '–'}</p>}
                        </div>
                    </div>
                    {bestellung?.bestelltAm && (
                        <p className="text-sm text-slate-600">
                            Bestellt am {datum(bestellung.bestelltAm)}{bestellung.bestelltVon ? ` von ${bestellung.bestelltVon}` : ''}, {bestellung.bestelltPer === 'EMAIL' ? 'per E-Mail' : 'telefonisch oder auf anderem Weg'}.
                        </p>
                    )}
                </Card>

                {istEntwurf && (
                    <Card className="space-y-3 p-4">
                        <p className="text-sm font-medium text-slate-700">Positionen hinzufügen</p>
                        <div className="flex flex-wrap gap-2">
                            <Button variant="outline" size="sm" onClick={() => void zeigeVorschlaege('MINDESTBESTAND')}><Boxes className="h-4 w-4" /> Lager auffüllen (Mindestbestand)</Button>
                            <Button variant="outline" size="sm" onClick={() => setProjektWahl(true)}><Briefcase className="h-4 w-4" /> Material eines Projekts</Button>
                        </div>
                        <div className="rounded-lg border border-slate-200 p-3">
                            <p className="mb-2 flex items-center gap-2 text-sm text-slate-700"><PackagePlus className="h-4 w-4" /> Oder einen Artikel frei aus dem Artikelstamm suchen</p>
                            <div className="flex flex-wrap items-start gap-3">
                                <div className="min-w-[16rem] flex-1"><LagerArtikelWahl onWaehlen={artikelHinzufuegen} /></div>
                                <div>
                                    <label htmlFor="frei-menge" className="mb-1 block text-xs text-slate-500">Menge</label>
                                    <input id="frei-menge" inputMode="decimal" value={artikelMenge} onChange={(e) => setArtikelMenge(e.target.value)}
                                        className="w-24 rounded-lg border border-slate-300 px-2 py-2 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                            </div>
                        </div>
                    </Card>
                )}

                <Card className="overflow-hidden p-0">
                    {zeilen.length === 0 ? (
                        <p className="p-6 text-center text-slate-600">Noch keine Positionen. Oben Material aus dem Lager, aus einem Projekt oder einen Artikel hinzufügen.</p>
                    ) : (
                        <table className="w-full text-left text-sm">
                            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                                <tr>
                                    <th className="px-4 py-3">Artikel</th>
                                    <th className="hidden px-4 py-3 md:table-cell">Wofür</th>
                                    <th className="px-4 py-3 text-right">Menge</th>
                                    <th className="hidden px-4 py-3 text-right lg:table-cell">Preis</th>
                                    {!istEntwurf && bestellung?.status === 'BESTELLT' && <th className="px-4 py-3">Geliefert</th>}
                                    {istEntwurf && <th className="w-12 px-2 py-3"><span className="sr-only">Entfernen</span></th>}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {zeilen.map((z, i) => {
                                    const p = bestellung?.positionen[i];
                                    return (
                                        <tr key={z.key}>
                                            <td className="px-4 py-3">
                                                <p className="font-medium text-slate-900">{z.bezeichnung}</p>
                                                <p className="text-xs text-slate-500">{[z.artikelnummer, z.kommentar].filter(Boolean).join(' · ')}</p>
                                            </td>
                                            <td className="hidden px-4 py-3 text-slate-600 md:table-cell">{z.projektName ?? HERKUNFT_TEXT[z.herkunft]}</td>
                                            <td className="px-4 py-3 text-right">
                                                {istEntwurf ? (
                                                    <span className="inline-flex items-center gap-1">
                                                        <input inputMode="decimal" aria-label={`Menge ${z.bezeichnung}`} value={z.menge}
                                                            onChange={(e) => setZeilen((l) => l.map((x) => (x.key === z.key ? { ...x, menge: e.target.value } : x)))}
                                                            className="w-24 rounded-lg border border-slate-300 px-2 py-1.5 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                                        <span className="w-8 text-left text-slate-600">{z.einheit}</span>
                                                    </span>
                                                ) : <span className="text-slate-900">{z.menge} {z.einheit}</span>}
                                            </td>
                                            <td className="hidden px-4 py-3 text-right text-slate-600 lg:table-cell">{z.einzelpreis === null ? '–' : geld(z.einzelpreis)}</td>
                                            {!istEntwurf && bestellung?.status === 'BESTELLT' && p && (
                                                <td className="px-4 py-3">
                                                    <label className="inline-flex items-center gap-2 text-slate-700">
                                                        <input type="checkbox" className="h-5 w-5 accent-rose-600" checked={p.geliefertAm !== null} disabled={!darfAnlegen || beschaeftigt}
                                                            aria-label={`${z.bezeichnung} geliefert`}
                                                            onChange={(e) => void aktion(() => liefere(bestellung.id, p.id, e.target.checked))} />
                                                        {p.geliefertAm ? datum(p.geliefertAm) : 'offen'}
                                                    </label>
                                                </td>
                                            )}
                                            {istEntwurf && (
                                                <td className="px-2 py-3 text-right">
                                                    <button type="button" aria-label={`${z.bezeichnung} entfernen`} onClick={() => setZeilen((l) => l.filter((x) => x.key !== z.key))}
                                                        className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-700"><X className="h-4 w-4" /></button>
                                                </td>
                                            )}
                                        </tr>
                                    );
                                })}
                            </tbody>
                            {summe > 0 && (
                                <tfoot>
                                    <tr className="bg-slate-50">
                                        <td className="px-4 py-3 text-right text-sm text-slate-600" colSpan={2}>Summe nach Preisliste (netto)</td>
                                        <td className="px-4 py-3 text-right font-medium text-slate-900" colSpan={3}>{geld(summe)}</td>
                                    </tr>
                                </tfoot>
                            )}
                        </table>
                    )}
                </Card>

                <div className="flex flex-wrap items-center gap-2">
                    {istEntwurf && (
                        <>
                            <Button variant="outline" onClick={() => void nurSpeichern()} disabled={beschaeftigt || (bestellung ? !darfAendern : !darfAnlegen)}>
                                {beschaeftigt ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Entwurf speichern
                            </Button>
                            <Button onClick={() => setBestellenOffen(true)} disabled={beschaeftigt || !darfAnlegen || zeilen.length === 0}><Send className="h-4 w-4" /> Bestellen …</Button>
                            {bestellung && darfLoeschen && <Button variant="ghost" onClick={() => void entwurfLoeschen()} disabled={beschaeftigt}><Trash2 className="h-4 w-4" /> Entwurf löschen</Button>}
                        </>
                    )}
                    {bestellung?.status === 'BESTELLT' && (
                        <>
                            <Button variant="outline" onClick={() => setMailOffen(true)}><Mail className="h-4 w-4" /> {bestellung.bestelltPer === 'EMAIL' ? 'E-Mail erneut senden' : 'Per E-Mail senden'}</Button>
                            {bestellung.anzahlGeliefert < bestellung.anzahlPositionen && darfAnlegen && (
                                <Button variant="outline" onClick={() => void aktion(() => alleGeliefert(bestellung.id), 'Alles als geliefert eingetragen.')} disabled={beschaeftigt}><CheckCheck className="h-4 w-4" /> Alles geliefert</Button>
                            )}
                            {darfAnlegen && <Button variant="ghost" onClick={() => { setStornoGrund(''); setStornoOffen(true); }} disabled={beschaeftigt}>Stornieren …</Button>}
                        </>
                    )}
                </div>
            </div>

            {projektWahl && (
                <ProjectSelectModal onClose={() => setProjektWahl(false)} onSelect={(p) => { setProjektWahl(false); void zeigeVorschlaege('PROJEKT', p); }} />
            )}
            {vorschlag && (
                <VorschlaegeDialog titel={vorschlag.titel} beschreibung={vorschlag.beschreibung} vorschlaege={vorschlag.liste} lieferantId={lieferant?.id ?? null}
                    onUebernehmen={fuegeVorschlaegeHinzu} onSchliessen={() => setVorschlag(null)} />
            )}
            {mailOffen && bestellung && (
                <BestellMailDialog bestellung={bestellung} onSchliessen={() => setMailOffen(false)} onGesendet={() => { setMailOffen(false); toast.success('Die E-Mail an den Lieferanten ist gesendet.'); }} />
            )}
            <Dialog open={bestellenOffen} onOpenChange={setBestellenOffen} aria-label="Wie wird bestellt?">
                <DialogContent className="sm:max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Wie wird bestellt?</DialogTitle>
                        <DialogDescription>Die Bestellung bekommt jetzt ihre Bestellnummer und lässt sich danach nicht mehr ändern (nur Lieferung eintragen oder stornieren).</DialogDescription>
                    </DialogHeader>
                    <div className="grid gap-3">
                        <Button onClick={() => void bestellen('EMAIL')}><Mail className="h-4 w-4" /> Bestellen und per E-Mail senden</Button>
                        <Button variant="outline" onClick={() => void bestellen('ANDERS')}><Phone className="h-4 w-4" /> Schon bestellt (zum Beispiel telefonisch)</Button>
                    </div>
                    <DialogFooter><Button variant="ghost" onClick={() => setBestellenOffen(false)}>Abbrechen</Button></DialogFooter>
                </DialogContent>
            </Dialog>
            <Dialog open={stornoOffen} onOpenChange={setStornoOffen} aria-label="Bestellung stornieren?">
                <DialogContent className="sm:max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Bestellung stornieren?</DialogTitle>
                        <DialogDescription>Die Bestellnummer bleibt vergeben. Material aus Projekten, das noch nicht geliefert ist, steht danach wieder als „zu bestellen“ da.</DialogDescription>
                    </DialogHeader>
                    <label htmlFor="storno-grund" className="text-sm font-medium text-slate-700">Warum wird storniert?</label>
                    <input id="storno-grund" value={stornoGrund} maxLength={500} onChange={(e) => setStornoGrund(e.target.value)} placeholder="Zum Beispiel: Lieferant kann nicht liefern"
                        className={cn('w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500')} />
                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => setStornoOffen(false)}>Zurück</Button>
                        <Button onClick={() => void stornieren()}>Stornieren</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
};

export default BestellungBearbeiten;
