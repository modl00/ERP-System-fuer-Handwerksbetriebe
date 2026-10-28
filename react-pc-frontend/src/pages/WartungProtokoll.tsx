import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Archive, ArchiveRestore, ArrowLeft, Check, CircleAlert, ExternalLink, FileText, Loader2, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { DatePicker } from '../components/ui/datepicker';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import {
    aendereKopf,
    aenderePruefpunktText,
    aendereDatenfeldDefinition,
    bewertePruefpunkt,
    erstelleBericht,
    fuegePruefpunktHinzu,
    ladeMeineUnterschrift,
    ladeProjekteDesKunden,
    ladeProtokoll,
    loescheMeineUnterschrift,
    loescheDatenfeld,
    loeschePruefpunkt,
    nutzeGespeicherteUnterschrift,
    setzeDatenfeld,
    speichereMeineUnterschrift,
    unterschreibe,
    wechsleStatus,
} from '../features/wartung/api';
import { ladeKundeObjekte } from '../features/kundenstamm/api';
import type { KundeObjekt } from '../features/kundenstamm/types';
import { DatenfeldDialog } from '../features/wartung/DatenfeldDialog';
import { DatenfeldEingabe } from '../features/wartung/DatenfeldEingabe';
import { ERGEBNIS_ANZEIGE, formatDatum, INTERVALL_OPTIONEN, PROTOKOLLARTEN, STATUS_ANZEIGE } from '../features/wartung/format';
import { berechneStatistik, fortschrittProzent, gruppiereNachAbschnitt } from '../features/wartung/gruppierung';
import { ObjektDialog } from '../features/wartung/ObjektDialog';
import { PruefpunktZeile } from '../features/wartung/PruefpunktZeile';
import { Unterschriftenfeld } from '../features/wartung/Unterschriftenfeld';
import { VerlaufKarte } from '../features/wartung/VerlaufKarte';
import { WiederOeffnenDialog } from '../features/wartung/WiederOeffnenDialog';
import type {
    Bezug,
    GespeicherteUnterschrift,
    ProtokollDatenfeld,
    ProtokollPruefpunkt,
    WartungErgebnis,
    WartungProtokoll as Protokoll,
    WartungStatus,
} from '../features/wartung/types';

interface Techniker {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
}

interface KopfEntwurf {
    projektId: string;
    objektId: string;
    titel: string;
    protokollTyp: string;
    datum: string;
    intervall: string;
    technikerId: string;
    ansprechpartnerName: string;
    bemerkungen: string;
}

function kopfAus(p: Protokoll): KopfEntwurf {
    return {
        projektId: p.projekt ? String(p.projekt.id) : '',
        objektId: p.objekt ? String(p.objekt.id) : '',
        titel: p.titel ?? '',
        protokollTyp: p.protokollTyp,
        datum: p.datum,
        intervall: String(p.intervallMonate),
        technikerId: p.techniker ? String(p.techniker.id) : '',
        ansprechpartnerName: p.ansprechpartnerName ?? '',
        bemerkungen: p.bemerkungen ?? '',
    };
}

interface DatenGruppe { schluessel: string | null; titel: string; felder: ProtokollDatenfeld[] }

/**
 * Gruppiert die Datenfelder nach Modul. Felder ohne Modul (global oder von Hand) stehen zuerst als "Allgemein".
 * Solange das Protokoll bearbeitbar ist, gibt es "Allgemein" und jedes Modul auch ohne Felder, damit man dort eines ergänzen kann.
 */
function gruppiereDatenfelder(p: Protokoll): DatenGruppe[] {
    const gruppen: DatenGruppe[] = [];
    const modulTitel = (schluessel: string | null) => (schluessel === null ? 'Allgemein' : (p.module.find((m) => m.modulKey === schluessel)?.name ?? 'Weitere Angaben'));
    const sortiert = [...p.datenfelder].sort((a, b) => a.sortierung - b.sortierung);
    for (const feld of sortiert) {
        let gruppe = gruppen.find((g) => g.schluessel === feld.modulKey);
        if (!gruppe) {
            gruppe = { schluessel: feld.modulKey, titel: modulTitel(feld.modulKey), felder: [] };
            gruppen.push(gruppe);
        }
        gruppe.felder.push(feld);
    }
    if (p.bearbeitbar) {
        const leer: DatenGruppe[] = [];
        if (!gruppen.some((g) => g.schluessel === null)) leer.push({ schluessel: null, titel: 'Allgemein', felder: [] });
        for (const m of p.module) {
            if (!gruppen.some((g) => g.schluessel === m.modulKey)) leer.push({ schluessel: m.modulKey, titel: m.name, felder: [] });
        }
        // "Allgemein" immer zuerst, danach die Gruppen mit Feldern, leere Modul-Gruppen am Ende
        const allgemeinLeer = leer.filter((g) => g.schluessel === null);
        const modulLeer = leer.filter((g) => g.schluessel !== null);
        return [...allgemeinLeer, ...gruppen, ...modulLeer];
    }
    return gruppen;
}

export default function WartungProtokoll() {
    const toast = useToast();
    const confirm = useConfirm();
    const { id } = useParams();
    const protokollId = Number(id);

    const [protokoll, setProtokoll] = useState<Protokoll | null>(null);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    const [fehlerText, setFehlerText] = useState('');
    const [nurOffene, setNurOffene] = useState(false);
    const [kopf, setKopf] = useState<KopfEntwurf | null>(null);
    const [kopfSpeichert, setKopfSpeichert] = useState(false);
    const [kopfVersucht, setKopfVersucht] = useState(false);
    const [statusLaeuft, setStatusLaeuft] = useState(false);
    const [techniker, setTechniker] = useState<Techniker[]>([]);
    const [projekte, setProjekte] = useState<Bezug[]>([]);
    const [objekte, setObjekte] = useState<KundeObjekt[]>([]);
    const [objektDialog, setObjektDialog] = useState(false);
    const [weitereAngaben, setWeitereAngaben] = useState(false);
    const [neuerPunkt, setNeuerPunkt] = useState('');
    const [neuerAbschnitt, setNeuerAbschnitt] = useState('');
    const [punktHinzufuegen, setPunktHinzufuegen] = useState(false);
    const [berichtLaeuft, setBerichtLaeuft] = useState(false);
    const [gespeicherteUnterschrift, setGespeicherteUnterschrift] = useState<GespeicherteUnterschrift | null>(null);

    const laden = useCallback(async () => {
        if (!Number.isInteger(protokollId) || protokollId <= 0) {
            setFehlerText('Diese Wartung gibt es nicht.');
            setLadeStatus('fehler');
            return;
        }
        setLadeStatus('laedt');
        try {
            const geladen = await ladeProtokoll(protokollId);
            setProtokoll(geladen);
            setKopf(kopfAus(geladen));
            setLadeStatus('ok');
        } catch (e) {
            setFehlerText(e instanceof Error ? e.message : 'Die Wartung konnte nicht geladen werden.');
            setLadeStatus('fehler');
            toast.error(e instanceof Error ? e.message : 'Die Wartung konnte nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [protokollId]);

    useEffect(() => { void laden(); }, [laden]);

    // Die Projekte des Kunden zur Auswahl in den Angaben: Projekte anderer Kunden gibt es dort nicht
    const kundeId = protokoll?.kunde.id;
    useEffect(() => {
        if (kundeId === undefined) return;
        ladeProjekteDesKunden(kundeId).then(setProjekte).catch(() => setProjekte([]));
    }, [kundeId]);

    // Die Objekte des Kunden zur Auswahl in den Angaben (nur die aktiven; das gewählte bleibt über die Optionen sichtbar)
    useEffect(() => {
        if (kundeId === undefined) return;
        ladeKundeObjekte(kundeId).then(setObjekte).catch(() => setObjekte([]));
    }, [kundeId]);

    // Die gespeicherte Unterschrift des angemeldeten Mitarbeiters (falls es eine gibt)
    useEffect(() => {
        ladeMeineUnterschrift().then(setGespeicherteUnterschrift).catch(() => setGespeicherteUnterschrift(null));
    }, []);

    useEffect(() => {
        fetch('/api/mitarbeiter')
            .then((res) => (res.ok ? res.json() : []))
            .then((liste: Techniker[]) => setTechniker(Array.isArray(liste) ? liste.filter((m) => m.aktiv !== false) : []))
            .catch(() => setTechniker([]));
    }, []);

    // Neu übernehmen, wenn der Server einen neuen Stand liefert (zum Beispiel nach dem Speichern der Angaben).
    const uebernehme = (neu: Protokoll) => { setProtokoll(neu); setKopf(kopfAus(neu)); };
    // Unterschrift und Status ändern die Angaben nicht: Ein noch nicht gespeicherter Entwurf bleibt stehen.
    const uebernehmeOhneKopf = (neu: Protokoll) => setProtokoll(neu);

    // ── Datenfelder ergänzen, umbenennen, löschen ───────────────────────────

    const [datenfeldDialog, setDatenfeldDialog] = useState<{ offen: boolean; gruppe: string | null }>({ offen: false, gruppe: null });

    const benenneDatenfeldUm = async (feld: ProtokollDatenfeld, label: string, optionen: string[] | null) => {
        try {
            uebernehmeOhneKopf(await aendereDatenfeldDefinition(protokollId, feld.id, label, optionen));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Datenfeld konnte nicht geändert werden.');
            throw e;
        }
    };

    // Ein zweiter Klick auf denselben Papierkorb, solange die erste Anfrage läuft, ändert nichts (sonst käme ein irreführendes "nicht gefunden")
    const loeschtGerade = useRef(new Set<number>());

    const loescheDatenfeldAusProtokoll = async (feld: ProtokollDatenfeld) => {
        if (loeschtGerade.current.has(feld.id)) return;
        // Ein Feld ohne Eintrag verschwindet ohne Rückfrage; mit Eintrag fragen wir nach, damit nichts versehentlich verloren geht.
        if ((feld.wert ?? '').trim() !== '') {
            const ja = await confirm({
                title: 'Datenfeld löschen?',
                message: `„${feld.label}" wird samt Eintrag aus diesem Feldeinsatz entfernt. Der Katalog und frühere Prüfungen bleiben unverändert.`,
                confirmLabel: 'Löschen',
                variant: 'danger',
            });
            if (!ja) return;
        }
        loeschtGerade.current.add(feld.id);
        try {
            uebernehmeOhneKopf(await loescheDatenfeld(protokollId, feld.id));
            toast.success('Datenfeld gelöscht.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Datenfeld konnte nicht gelöscht werden.');
        } finally {
            loeschtGerade.current.delete(feld.id);
        }
    };

    // ── Prüfpunkte ──────────────────────────────────────────────────────────

    // Pro Prüfpunkt läuft immer nur eine Anfrage. Folgt schnell eine zweite (Mangeltext verlassen, dann gleich
    // auf eine Schaltfläche tippen), wartet sie auf die erste: Der Server sieht sie in der Reihenfolge der Eingabe.
    const laufendeBewertung = useRef(new Map<number, Promise<unknown>>());
    const letzteBewertung = useRef(new Map<number, number>());
    const bestaetigterStand = useRef(new Map<number, ProtokollPruefpunkt>());

    const bewerte = useCallback(async (punkt: ProtokollPruefpunkt, ergebnis: WartungErgebnis | null, bemerkung: string) => {
        const nummer = (letzteBewertung.current.get(punkt.id) ?? 0) + 1;
        letzteBewertung.current.set(punkt.id, nummer);
        if (!bestaetigterStand.current.has(punkt.id)) bestaetigterStand.current.set(punkt.id, punkt);
        const davor = laufendeBewertung.current.get(punkt.id) ?? Promise.resolve();
        const bemerkungNeu = bemerkung.trim() === '' ? null : bemerkung.trim();
        // Sofort anzeigen, damit sich das Antippen direkt anfühlt. Bei einem Fehler gehen wir zurück.
        setProtokoll((alt) => {
            if (!alt) return alt;
            const punkte = alt.pruefpunkte.map((p) => (p.id === punkt.id ? { ...p, ergebnis, bemerkung: bemerkungNeu } : p));
            return { ...alt, pruefpunkte: punkte, statistik: berechneStatistik(punkte), status: alt.status === 'OFFEN' ? 'IN_BEARBEITUNG' : alt.status };
        });
        const lauf = davor.catch(() => undefined).then(async () => {
            const istNeuester = () => letzteBewertung.current.get(punkt.id) === nummer;
            try {
                const antwort = await bewertePruefpunkt(protokollId, punkt.id, ergebnis, bemerkung);
                const bestaetigt = antwort.pruefpunkt;
                if (bestaetigt) bestaetigterStand.current.set(punkt.id, bestaetigt);
                // Nur die Antwort auf die neueste Eingabe darf die Anzeige überschreiben.
                if (!istNeuester()) return;
                setProtokoll((alt) => alt && {
                    ...alt,
                    status: antwort.status,
                    statistik: antwort.statistik,
                    pruefpunkte: bestaetigt ? alt.pruefpunkte.map((p) => (p.id === bestaetigt.id ? bestaetigt : p)) : alt.pruefpunkte,
                });
            } catch (e) {
                if (!istNeuester()) return;
                // Zurück auf den letzten Stand, den der Server bestätigt hat (nicht auf einen eingefrorenen Zwischenstand).
                const zurueck = bestaetigterStand.current.get(punkt.id) ?? punkt;
                setProtokoll((alt) => {
                    if (!alt) return alt;
                    const punkte = alt.pruefpunkte.map((p) => (p.id === punkt.id ? zurueck : p));
                    return { ...alt, pruefpunkte: punkte, statistik: berechneStatistik(punkte) };
                });
                toast.error(e instanceof Error ? e.message : 'Die Bewertung konnte nicht gespeichert werden.');
            }
        });
        laufendeBewertung.current.set(punkt.id, lauf);
        await lauf;
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [protokollId]);

    const setzeFeld = useCallback(async (feld: ProtokollDatenfeld, wert: string) => {
        const wertNeu = wert.trim() === '' ? null : wert.trim();
        setProtokoll((alt) => alt && {
            ...alt,
            status: alt.status === 'OFFEN' ? 'IN_BEARBEITUNG' : alt.status,
            datenfelder: alt.datenfelder.map((d) => (d.id === feld.id ? { ...d, wert: wertNeu } : d)),
        });
        try {
            const antwort = await setzeDatenfeld(protokollId, feld.id, wert);
            const bestaetigt = antwort.datenfeld;
            setProtokoll((alt) => alt && {
                ...alt,
                status: antwort.status,
                datenfelder: bestaetigt ? alt.datenfelder.map((d) => (d.id === bestaetigt.id ? bestaetigt : d)) : alt.datenfelder,
            });
        } catch (e) {
            setProtokoll((alt) => alt && { ...alt, datenfelder: alt.datenfelder.map((d) => (d.id === feld.id ? feld : d)) });
            toast.error(e instanceof Error ? e.message : 'Der Wert konnte nicht gespeichert werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [protokollId]);

    // ── Angaben (Kopf) ──────────────────────────────────────────────────────

    const kopfGeaendert = useMemo(() => {
        if (!protokoll || !kopf) return false;
        const ist = kopfAus(protokoll);
        return (Object.keys(ist) as (keyof KopfEntwurf)[]).some((k) => ist[k] !== kopf[k]);
    }, [protokoll, kopf]);

    const speichereKopf = async () => {
        if (!protokoll || !kopf) return;
        setKopfVersucht(true);
        if (kopf.datum === '' || kopf.protokollTyp.trim() === '') return;
        setKopfSpeichert(true);
        try {
            const neu = await aendereKopf(protokoll.id, {
                titel: kopf.titel,
                protokollTyp: kopf.protokollTyp,
                datum: kopf.datum,
                intervallMonate: Number(kopf.intervall),
                technikerId: kopf.technikerId === '' ? null : Number(kopf.technikerId),
                projektId: kopf.projektId === '' ? null : Number(kopf.projektId),
                objektId: kopf.objektId === '' ? null : Number(kopf.objektId),
                bemerkungen: kopf.bemerkungen,
                ansprechpartnerName: kopf.ansprechpartnerName,
            });
            uebernehme(neu);
            setKopfVersucht(false);
            toast.success('Angaben gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Angaben konnten nicht gespeichert werden.');
        } finally {
            setKopfSpeichert(false);
        }
    };

    // ── Unterschriften und Status ───────────────────────────────────────────

    const speichereUnterschrift = (rolle: 'TECHNIKER' | 'KUNDE') => async (bild: string, name: string) => {
        try {
            uebernehmeOhneKopf(await unterschreibe(protokollId, rolle, bild, name));
            toast.success('Unterschrift gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Unterschrift konnte nicht gespeichert werden.');
            throw e;
        }
    };

    const loescheUnterschrift = (rolle: 'TECHNIKER' | 'KUNDE') => async () => {
        try {
            uebernehmeOhneKopf(await unterschreibe(protokollId, rolle, null, ''));
            toast.success('Unterschrift gelöscht.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Unterschrift konnte nicht gelöscht werden.');
        }
    };

    const [wiederOeffnenOffen, setWiederOeffnenOffen] = useState(false);

    const wechsle = async (ziel: WartungStatus, frage?: { titel: string; text: string; ok: string; art: 'info' | 'warning' }, grund?: string) => {
        if (!protokoll) return;
        if (frage) {
            const ja = await confirm({ title: frage.titel, message: frage.text, confirmLabel: frage.ok, variant: frage.art });
            if (!ja) return;
        }
        setStatusLaeuft(true);
        try {
            const neu = await wechsleStatus(protokoll.id, ziel, grund);
            if (kopfGeaendert && !neu.bearbeitbar) {
                // Der Entwurf ließe sich nicht mehr speichern, weil das Protokoll jetzt gesperrt ist.
                toast.info('Nicht gespeicherte Änderungen an den Angaben wurden verworfen.');
                uebernehme(neu);
            } else {
                uebernehmeOhneKopf(neu);
            }
            toast.success(`Status: ${STATUS_ANZEIGE[ziel].label}.`);
            setWiederOeffnenOffen(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Status konnte nicht geändert werden.');
        } finally {
            setStatusLaeuft(false);
        }
    };

    // ── Prüfpunkte ergänzen, umformulieren, löschen ─────────────────────────

    const fuegeHinzu = async () => {
        const label = neuerPunkt.trim();
        if (label === '' || punktHinzufuegen) return;
        setPunktHinzufuegen(true);
        try {
            uebernehmeOhneKopf(await fuegePruefpunktHinzu(protokollId, label, neuerAbschnitt === '' ? null : Number(neuerAbschnitt)));
            setNeuerPunkt('');
            toast.success('Prüfpunkt ergänzt.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Prüfpunkt konnte nicht ergänzt werden.');
        } finally {
            setPunktHinzufuegen(false);
        }
    };

    const aendereText = async (punkt: ProtokollPruefpunkt, label: string) => {
        try {
            uebernehmeOhneKopf(await aenderePruefpunktText(protokollId, punkt.id, label));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Prüfpunkt konnte nicht geändert werden.');
            throw e;
        }
    };

    const loesche = async (punkt: ProtokollPruefpunkt) => {
        // Ein noch unberührter Punkt verschwindet ohne Rückfrage: kein unnötiges Fenster. Mit Ergebnis oder Hinweis fragen wir nach.
        if (punkt.ergebnis !== null || (punkt.bemerkung ?? '').trim() !== '') {
            const ja = await confirm({
                title: 'Prüfpunkt löschen?',
                message: `„${punkt.label}" wird samt Ergebnis und Hinweis aus diesem Feldeinsatz entfernt. Der Katalog und frühere Prüfungen bleiben unverändert.`,
                confirmLabel: 'Löschen',
                variant: 'danger',
            });
            if (!ja) return;
        }
        try {
            uebernehmeOhneKopf(await loeschePruefpunkt(protokollId, punkt.id));
            toast.success('Prüfpunkt gelöscht.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Prüfpunkt konnte nicht gelöscht werden.');
        }
    };

    // ── Gespeicherte Unterschrift und Bericht im Projekt ────────────────────

    const verwendeGespeicherteUnterschrift = async () => {
        try {
            uebernehmeOhneKopf(await nutzeGespeicherteUnterschrift(protokollId));
            toast.success('Die gespeicherte Unterschrift ist eingesetzt.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die gespeicherte Unterschrift konnte nicht eingesetzt werden.');
            throw e;
        }
    };

    const merkeUnterschrift = async (bild: string, name: string) => {
        try {
            setGespeicherteUnterschrift(await speichereMeineUnterschrift(bild, name));
            toast.success('Die Unterschrift ist für künftige Feldeinsätze gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Unterschrift konnte nicht gespeichert werden.');
            throw e;
        }
    };

    const loescheGespeicherteUnterschrift = async () => {
        const ja = await confirm({
            title: 'Gespeicherte Unterschrift löschen?',
            message: 'Neue Feldeinsätze bekommen dann keine Unterschrift mehr automatisch. Bereits unterschriebene Protokolle bleiben, wie sie sind.',
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ja) return;
        try {
            await loescheMeineUnterschrift();
            setGespeicherteUnterschrift(null);
            toast.success('Die gespeicherte Unterschrift ist gelöscht.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Unterschrift konnte nicht gelöscht werden.');
        }
    };

    const legeBerichtImProjektAb = async () => {
        setBerichtLaeuft(true);
        try {
            const bericht = await erstelleBericht(protokollId);
            setProtokoll((alt) => alt && { ...alt, bericht: { dokumentId: bericht.dokumentId, url: bericht.url, erstelltAm: bericht.erstelltAm } });
            toast.success('Der Bericht liegt jetzt im Projekt.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Bericht konnte nicht erstellt werden.');
        } finally {
            setBerichtLaeuft(false);
        }
    };

    const scrollZu = (id: string) => {
        const ziel = document.getElementById(id);
        if (!ziel) return;
        const reduziert = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        ziel.scrollIntoView({ behavior: reduziert ? 'auto' : 'smooth', block: 'start' });
    };

    // ── Ableitungen ─────────────────────────────────────────────────────────

    const offene = protokoll ? protokoll.statistik.gesamt - protokoll.statistik.bewertet : 0;
    const technikerHat = !!protokoll && !!protokoll.unterschriftTechniker.bild && !!protokoll.unterschriftTechniker.name;
    const grundFuerAbschluss = !protokoll ? 'Die Wartung wird noch geladen.'
        : offene > 0 ? `Es ${offene === 1 ? 'ist noch ein Prüfpunkt' : `sind noch ${offene} Prüfpunkte`} nicht bewertet.`
            : !technikerHat ? 'Der Techniker hat noch nicht unterschrieben.' : undefined;

    const pruefAbschnitte = useMemo(() => (protokoll ? protokoll.abschnitte.filter((a) => a.bereich === 'PRUEFPUNKTE') : []), [protokoll]);

    const pruefGruppen = useMemo(() => {
        if (!protokoll) return [];
        const punkte = nurOffene ? protokoll.pruefpunkte.filter((p) => p.ergebnis === null) : protokoll.pruefpunkte;
        return gruppiereNachAbschnitt(protokoll.abschnitte, 'PRUEFPUNKTE', punkte);
    }, [protokoll, nurOffene]);

    const datenGruppen = useMemo(() => (protokoll ? gruppiereDatenfelder(protokoll) : []), [protokoll]);
    const maengel = protokoll?.pruefpunkte.filter((p) => p.ergebnis === 'NIO') ?? [];

    // ── Darstellung ─────────────────────────────────────────────────────────

    if (ladeStatus === 'laedt') {
        return (
            <PageLayout ribbonCategory="Wartung" title="Wird geladen">
                <div className="flex flex-col gap-4" aria-busy="true" aria-label="Die Wartung wird geladen">
                    {[0, 1, 2].map((i) => <div key={i} className="motion-safe:animate-pulse bg-white border border-slate-200 rounded-lg shadow-sm h-24" />)}
                </div>
            </PageLayout>
        );
    }

    if (ladeStatus === 'fehler' || !protokoll || !kopf) {
        return (
            <PageLayout ribbonCategory="Wartung" title="Wartung">
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <p>{fehlerText || 'Die Wartung konnte nicht geladen werden.'}</p>
                    <div className="mt-3 flex justify-center gap-2">
                        <Button variant="outline" size="sm" onClick={() => void laden()}>Erneut versuchen</Button>
                        <Link to="/wartung"><Button variant="ghost" size="sm">Zur Übersicht</Button></Link>
                    </div>
                </div>
            </PageLayout>
        );
    }

    const status = STATUS_ANZEIGE[protokoll.status];
    const prozent = fortschrittProzent(protokoll.statistik);
    const ort = [protokoll.kunde.name, protokoll.objekt?.name, protokoll.tuer?.name].filter(Boolean).join(' · ');
    const wiederherstellenZiel: WartungStatus = protokoll.abgeschlossenAm ? 'ABGESCHLOSSEN' : 'OFFEN';

    const aktionen = (
        <>
            <Button variant="outline" onClick={() => window.open(`/api/wartung/protokolle/${protokoll.id}/pdf`, '_blank', 'noopener')}
                aria-label="Protokoll als PDF öffnen" title="Öffnet das Protokoll als PDF zum Ansehen und Drucken">
                <FileText className="h-4 w-4" aria-hidden="true" /> PDF
            </Button>
            {protokoll.status === 'ABGESCHLOSSEN' && (
                <>
                    <Button
                        variant="outline"
                        disabled={statusLaeuft}
                        onClick={() => setWiederOeffnenOffen(true)}
                        title="Zum nachträglichen Ändern des Berichts; wer wann geöffnet hat, steht im Verlauf"
                    >
                        <RotateCcw className="h-4 w-4" aria-hidden="true" /> Wieder öffnen
                    </Button>
                    <Button
                        variant="ghost"
                        disabled={statusLaeuft}
                        onClick={() => wechsle('ARCHIVIERT', {
                            titel: 'Wartung archivieren?',
                            text: 'Das Protokoll verschwindet aus der Liste, bleibt aber erhalten und lässt sich jederzeit wiederherstellen.',
                            ok: 'Archivieren',
                            art: 'warning',
                        })}
                    >
                        <Archive className="h-4 w-4" aria-hidden="true" /> Archivieren
                    </Button>
                </>
            )}
            {protokoll.status === 'ARCHIVIERT' && (
                <Button variant="outline" disabled={statusLaeuft} onClick={() => wechsle(wiederherstellenZiel)}>
                    <ArchiveRestore className="h-4 w-4" aria-hidden="true" /> Wiederherstellen
                </Button>
            )}
        </>
    );

    const abgeschlossen = protokoll.abgeschlossenAm !== null;
    const bericht = protokoll.bericht;
    const projektOptionen = [
        { value: '', label: 'Kein Projekt' },
        ...(protokoll.projekt && !projekte.some((p) => p.id === protokoll.projekt!.id) ? [{ value: String(protokoll.projekt.id), label: protokoll.projekt.name }] : []),
        ...projekte.map((p) => ({ value: String(p.id), label: p.name })),
    ];
    const objektOptionen = [
        { value: '', label: 'Kein Objekt' },
        ...(protokoll.objekt && !objekte.some((o) => o.id === protokoll.objekt!.id) ? [{ value: String(protokoll.objekt.id), label: protokoll.objekt.name }] : []),
        ...objekte.map((o) => ({ value: String(o.id), label: o.bezeichnung })),
    ];
    const abschnittOptionen = [
        { value: '', label: 'Zusätzliche Prüfpunkte' },
        ...pruefAbschnitte.filter((a) => a.label !== 'Zusätzliche Prüfpunkte').map((a) => ({ value: String(a.id), label: a.label })),
    ];
    const gespeichertAm = gespeicherteUnterschrift ? formatDatum(gespeicherteUnterschrift.geaendertAm) : null;

    return (
        <PageLayout
            ribbonCategory="Wartung"
            title={protokoll.nummer}
            subtitle={ort}
            actions={aktionen}
        >
            <div className="flex flex-wrap items-center justify-between gap-2">
                <Link
                    to={protokoll.tuer ? `/wartung/anlagen/${protokoll.tuer.id}` : '/wartung'}
                    className="inline-flex min-h-11 items-center gap-1 text-sm text-slate-500 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500 rounded"
                >
                    <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {protokoll.tuer ? 'Zur Wartung und Historie' : 'Alle Wartungen'}
                </Link>
                <nav aria-label="Springe zu" className="flex gap-1">
                    {([['angaben', 'Angaben'], ['pruefpunkte', 'Prüfpunkte'], ['abschluss', 'Abschluss']] as [string, string][]).map(([zielId, text]) => (
                        <button
                            key={zielId}
                            type="button"
                            onClick={() => scrollZu(zielId)}
                            className="min-h-11 rounded-lg px-3 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        >
                            {text}
                        </button>
                    ))}
                </nav>
            </div>

            <Card className="sticky top-0 z-20 p-4 shadow-sm">
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <span className={cn('inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold', status.klasse)}>
                        {status.label}
                    </span>
                    {protokoll.titel && <span className="min-w-0 text-sm font-medium text-slate-700 break-words">{protokoll.titel}</span>}
                    <span className="hidden md:inline text-sm text-slate-500">{protokoll.protokollTyp} vom {formatDatum(protokoll.datum)}</span>
                    {protokoll.module.length > 0 && (
                        <span className="hidden md:inline min-w-0 text-sm text-slate-500 break-words">
                            {protokoll.module.map((m) => (m.untertypLabel ? `${m.name} (${m.untertypLabel})` : m.name)).join(' + ')}
                        </span>
                    )}
                    {/* Die Hauptaktion steht in der mitlaufenden Leiste, damit sie auch weit unten in der Checkliste erreichbar bleibt. */}
                    {protokoll.bearbeitbar && (
                        <Button size="sm" className="ml-auto min-h-11" onClick={() => scrollZu('abschluss')}>
                            <Check className="h-4 w-4" aria-hidden="true" /> Zum Abschluss
                        </Button>
                    )}
                </div>
                <div className="mt-3 flex items-center gap-3">
                    <div
                        role="progressbar"
                        aria-label="Fortschritt der Prüfung"
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={prozent}
                        className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100"
                    >
                        <div className={cn('h-full rounded-full transition-[width]', prozent === 100 ? 'bg-emerald-500' : 'bg-rose-600')} style={{ width: `${prozent}%` }} />
                    </div>
                    <p className="shrink-0 text-sm tabular-nums text-slate-600">
                        {protokoll.statistik.bewertet} von {protokoll.statistik.gesamt} bewertet
                        {protokoll.statistik.nichtInOrdnung > 0 && (
                            <span className="ml-2 font-semibold text-rose-700">
                                {protokoll.statistik.nichtInOrdnung} {protokoll.statistik.nichtInOrdnung === 1 ? 'Mangel' : 'Mängel'}
                            </span>
                        )}
                    </p>
                </div>
            </Card>

            <WiederOeffnenDialog open={wiederOeffnenOffen} laeuft={statusLaeuft} onOpenChange={setWiederOeffnenOffen}
                onBestaetigen={(grund) => void wechsle('IN_BEARBEITUNG', undefined, grund)} />
            <VerlaufKarte protokollId={protokoll.id} stand={`${protokoll.status}-${protokoll.abgeschlossenAm ?? ''}`} />

            {!protokoll.bearbeitbar && (
                <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    Dieser Feldeinsatz ist {status.label.toLowerCase()} und lässt sich nicht mehr ändern. Zum Ändern zuerst wieder öffnen.
                </p>
            )}
            {protokoll.vorlageProtokollId !== null && protokoll.bearbeitbar && (
                <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600" role="note">
                    Dieser Feldeinsatz wurde aus einer früheren Prüfung gestartet. Alle übernommenen Werte lassen sich ändern, die frühere Prüfung bleibt unverändert.
                </p>
            )}

            {/* ── Bereich 1: Angaben und Datenfelder ── */}
            <section id="angaben" aria-labelledby="angaben-titel" className="scroll-mt-28 space-y-4">
                <h2 id="angaben-titel" className="text-lg font-semibold text-slate-900">Angaben</h2>
                <Card className="p-4 sm:p-6">
                    <dl className="mb-4 grid grid-cols-[7.5rem_1fr] gap-x-3 gap-y-1.5 text-sm">
                        <dt className="text-slate-500">Kunde</dt><dd className="text-slate-900 break-words">{protokoll.kunde.name}</dd>
                        {protokoll.objekt && <><dt className="text-slate-500">Objekt</dt><dd className="text-slate-900 break-words">{protokoll.objekt.name}</dd></>}
                        {protokoll.tuer && <><dt className="text-slate-500">Wartung</dt><dd className="text-slate-900 break-words">{protokoll.tuer.name}</dd></>}
                        {protokoll.module.length > 0 && (
                            <>
                                <dt className="text-slate-500">Geprüft wird</dt>
                                <dd className="text-slate-900 break-words">{protokoll.module.map((m) => (m.untertypLabel ? `${m.name} (${m.untertypLabel})` : m.name)).join(' + ')}</dd>
                            </>
                        )}
                    </dl>
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <div>
                            <Label id="kopf-datum-label">Datum der Prüfung</Label>
                            <div role="group" aria-labelledby="kopf-datum-label">
                                <DatePicker value={kopf.datum} disabled={!protokoll.bearbeitbar} onChange={(wert) => setKopf({ ...kopf, datum: wert })}
                                    error={kopfVersucht && kopf.datum === '' ? 'Bitte ein Datum wählen.' : undefined} />
                            </div>
                        </div>
                        <div>
                            <Label htmlFor="kopf-techniker">Mitarbeiter</Label>
                            <Select
                                id="kopf-techniker"
                                value={kopf.technikerId}
                                disabled={!protokoll.bearbeitbar}
                                onChange={(wert) => setKopf({ ...kopf, technikerId: wert })}
                                options={[
                                    { value: '', label: 'Noch offen' },
                                    ...(protokoll.techniker && !techniker.some((t) => t.id === protokoll.techniker!.id)
                                        ? [{ value: String(protokoll.techniker.id), label: protokoll.techniker.name }] : []),
                                    ...techniker.map((m) => ({ value: String(m.id), label: `${m.vorname} ${m.nachname}`.trim() })),
                                ]}
                            />
                        </div>
                        <div>
                            <div className="flex items-center justify-between gap-2">
                                <Label htmlFor="kopf-objekt">Objekt</Label>
                                {protokoll.bearbeitbar && !protokoll.tuer && (
                                    <Button type="button" variant="ghost" size="sm" className="min-h-11 px-2 text-xs" onClick={() => setObjektDialog(true)}>
                                        <Plus className="h-3.5 w-3.5" aria-hidden="true" /> Neues Objekt
                                    </Button>
                                )}
                            </div>
                            <Select id="kopf-objekt" value={kopf.objektId} disabled={!protokoll.bearbeitbar || !!protokoll.tuer} options={objektOptionen}
                                onChange={(wert) => setKopf({ ...kopf, objektId: wert })} />
                            {protokoll.tuer && (
                                <p className="mt-1 text-xs text-slate-500">Das Objekt ergibt sich aus der Wartung „{protokoll.tuer.name}".</p>
                            )}
                        </div>
                        <div>
                            <Label htmlFor="kopf-projekt">Projekt</Label>
                            <Select id="kopf-projekt" value={kopf.projektId} disabled={!protokoll.bearbeitbar} options={projektOptionen}
                                onChange={(wert) => setKopf({ ...kopf, projektId: wert })} />
                        </div>
                        <div>
                            <Label htmlFor="kopf-ansprechpartner">Ansprechpartner des Kunden</Label>
                            <Input id="kopf-ansprechpartner" value={kopf.ansprechpartnerName} maxLength={200} disabled={!protokoll.bearbeitbar}
                                onChange={(e) => setKopf({ ...kopf, ansprechpartnerName: e.target.value })} />
                        </div>
                        <div className="sm:col-span-2">
                            <Label htmlFor="kopf-bemerkungen">Bemerkungen</Label>
                            <textarea
                                id="kopf-bemerkungen"
                                className="w-full min-h-[90px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50"
                                value={kopf.bemerkungen}
                                maxLength={10000}
                                disabled={!protokoll.bearbeitbar}
                                onChange={(e) => setKopf({ ...kopf, bemerkungen: e.target.value })}
                            />
                        </div>
                    </div>

                    <button
                        type="button"
                        aria-expanded={weitereAngaben}
                        aria-controls="kopf-weitere"
                        onClick={() => setWeitereAngaben((v) => !v)}
                        className="mt-3 inline-flex min-h-11 items-center text-sm font-medium text-rose-700 hover:underline focus:outline-none focus:ring-2 focus:ring-rose-500 rounded"
                    >
                        {weitereAngaben ? 'Weitere Angaben ausblenden' : 'Weitere Angaben (Titel, Protokollart, Intervall)'}
                    </button>
                    {weitereAngaben && (
                        <div id="kopf-weitere" className="mt-2 grid grid-cols-1 gap-4 sm:grid-cols-2">
                            <div>
                                <Label htmlFor="kopf-titel">Titel</Label>
                                <Input id="kopf-titel" value={kopf.titel} maxLength={200} disabled={!protokoll.bearbeitbar}
                                    onChange={(e) => setKopf({ ...kopf, titel: e.target.value })} />
                            </div>
                            <div>
                                <Label htmlFor="kopf-art">Protokollart</Label>
                                <Select
                                    id="kopf-art"
                                    value={kopf.protokollTyp}
                                    disabled={!protokoll.bearbeitbar}
                                    onChange={(wert) => setKopf({ ...kopf, protokollTyp: wert })}
                                    options={(PROTOKOLLARTEN.includes(kopf.protokollTyp) ? PROTOKOLLARTEN : [kopf.protokollTyp, ...PROTOKOLLARTEN])
                                        .map((a) => ({ value: a, label: a }))}
                                    error={kopfVersucht && kopf.protokollTyp.trim() === '' ? 'Bitte eine Protokollart wählen.' : undefined}
                                />
                            </div>
                            <div>
                                <Label htmlFor="kopf-intervall">Nächste Prüfung in</Label>
                                <Select
                                    id="kopf-intervall"
                                    value={kopf.intervall}
                                    disabled={!protokoll.bearbeitbar}
                                    onChange={(wert) => setKopf({ ...kopf, intervall: wert })}
                                    options={(INTERVALL_OPTIONEN.includes(Number(kopf.intervall)) ? INTERVALL_OPTIONEN : [Number(kopf.intervall), ...INTERVALL_OPTIONEN])
                                        .map((m) => ({ value: String(m), label: m === 1 ? '1 Monat' : `${m} Monaten` }))}
                                />
                            </div>
                        </div>
                    )}

                    {protokoll.bearbeitbar && (
                        <div className="mt-4 flex flex-wrap items-center justify-end gap-3">
                            {kopfGeaendert && <span role="status" className="text-sm text-amber-700">Noch nicht gespeichert</span>}
                            <Button className="min-h-11" onClick={speichereKopf} disabled={kopfSpeichert || !kopfGeaendert}
                                title={kopfGeaendert ? undefined : 'Es gibt keine ungespeicherten Änderungen'}>
                                {kopfSpeichert && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                                {kopfSpeichert ? 'Speichert...' : 'Angaben speichern'}
                            </Button>
                        </div>
                    )}
                </Card>

                {protokoll.bearbeitbar && !protokoll.tuer && (
                    <ObjektDialog
                        open={objektDialog}
                        onOpenChange={setObjektDialog}
                        kundeId={protokoll.kunde.id}
                        onGespeichert={(neu) => {
                            setObjekte((alt) => [...alt, { id: neu.id, bezeichnung: neu.bezeichnung, strasse: neu.strasse, plz: neu.plz, ort: neu.ort, ansprechpartnerName: neu.ansprechpartnerName, ansprechpartnerTelefon: neu.ansprechpartnerTelefon, notizen: neu.notizen, aktiv: true, anzahlWartungen: 0 }]);
                            setKopf((alt) => alt && { ...alt, objektId: String(neu.id) });
                        }}
                    />
                )}
                {datenGruppen.map((gruppe) => (
                    <Card key={gruppe.schluessel ?? 'allgemein'} className="p-4 sm:p-6" data-testid={`datengruppe-${gruppe.schluessel ?? 'allgemein'}`}>
                        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                            <h3 className="text-base font-semibold text-slate-900">{gruppe.titel}</h3>
                            {protokoll.bearbeitbar && (
                                <Button variant="ghost" size="sm" className="min-h-11" onClick={() => setDatenfeldDialog({ offen: true, gruppe: gruppe.schluessel })}
                                    aria-label={`Datenfeld hinzufügen: ${gruppe.titel}`}>
                                    <Plus className="h-4 w-4" aria-hidden="true" /> Feld hinzufügen
                                </Button>
                            )}
                        </div>
                        {gruppe.felder.length === 0 ? (
                            <p className="text-sm text-slate-500">Hier gibt es noch keine Datenfelder. Mit „Feld hinzufügen" ergänzen Sie eines für diesen Feldeinsatz.</p>
                        ) : (
                            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                {gruppe.felder.map((feld) => (
                                    <DatenfeldEingabe key={feld.id} feld={feld} bearbeitbar={protokoll.bearbeitbar} onSetzen={setzeFeld}
                                        onUmbenennen={benenneDatenfeldUm} onLoeschen={loescheDatenfeldAusProtokoll} />
                                ))}
                            </div>
                        )}
                    </Card>
                ))}
                {protokoll.bearbeitbar && (
                    <DatenfeldDialog
                        open={datenfeldDialog.offen}
                        onOpenChange={(offen) => setDatenfeldDialog((alt) => ({ ...alt, offen }))}
                        protokollId={protokollId}
                        gruppen={datenGruppen.map((g) => ({ schluessel: g.schluessel, titel: g.titel }))}
                        vorbelegteGruppe={datenfeldDialog.gruppe}
                        onHinzugefuegt={uebernehmeOhneKopf}
                    />
                )}
            </section>

            {/* ── Bereich 2: Prüfpunkte ── */}
            <section id="pruefpunkte" aria-labelledby="pruefpunkte-titel" className="scroll-mt-28 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <h2 id="pruefpunkte-titel" className="text-lg font-semibold text-slate-900">Prüfpunkte</h2>
                    <Button variant={nurOffene ? 'default' : 'outline'} size="sm" className="min-h-11" aria-pressed={nurOffene} onClick={() => setNurOffene((v) => !v)}>
                        Nur offene Punkte
                    </Button>
                </div>
                <p className="text-sm text-slate-500">
                    {offene === 0 ? 'Alle Prüfpunkte sind bewertet.' : `${offene} ${offene === 1 ? 'Prüfpunkt ist' : 'Prüfpunkte sind'} noch offen.`}
                </p>
                {pruefGruppen.length === 0 && (
                    <div className="text-center py-10 text-slate-500 bg-slate-50 rounded-lg">
                        <p>{nurOffene ? 'Es gibt keine offenen Prüfpunkte mehr.' : 'Dieser Feldeinsatz hat keine Prüfpunkte.'}</p>
                    </div>
                )}
                {pruefGruppen.map((gruppe) => (
                    <div key={gruppe.abschnitt?.id ?? 'ohne'}>
                        <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
                            {gruppe.abschnitt?.label ?? 'Weitere Prüfpunkte'}
                        </h3>
                        <ul className="flex flex-col gap-2">
                            {gruppe.eintraege.map((punkt) => (
                                <PruefpunktZeile
                                    key={punkt.id}
                                    punkt={punkt}
                                    bearbeitbar={protokoll.bearbeitbar}
                                    onBewerten={bewerte}
                                    onTextAendern={aendereText}
                                    onLoeschen={(p) => void loesche(p)}
                                />
                            ))}
                        </ul>
                    </div>
                ))}

                {protokoll.bearbeitbar && (
                    <Card className="p-4">
                        <h3 className="mb-3 text-sm font-semibold text-slate-900">Prüfpunkt hinzufügen</h3>
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                            <div className="min-w-0 flex-1">
                                <Label htmlFor="neuer-pruefpunkt">Was ist zusätzlich zu prüfen?</Label>
                                <Input
                                    id="neuer-pruefpunkt"
                                    value={neuerPunkt}
                                    maxLength={500}
                                    placeholder="z. B. Abgasmessung"
                                    onChange={(e) => setNeuerPunkt(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void fuegeHinzu(); } }}
                                />
                            </div>
                            {pruefAbschnitte.length > 1 && (
                                <div className="sm:w-60">
                                    <Label htmlFor="neuer-pruefpunkt-abschnitt">In Abschnitt</Label>
                                    <Select id="neuer-pruefpunkt-abschnitt" value={neuerAbschnitt} onChange={setNeuerAbschnitt} options={abschnittOptionen} />
                                </div>
                            )}
                            <Button className="min-h-11" onClick={() => void fuegeHinzu()} disabled={punktHinzufuegen || neuerPunkt.trim() === ''}>
                                {punktHinzufuegen ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />}
                                Hinzufügen
                            </Button>
                        </div>
                        <p className="mt-2 text-xs text-slate-500">Der Prüfpunkt gilt nur für diesen Feldeinsatz. Der Katalog und frühere Prüfungen bleiben unverändert.</p>
                    </Card>
                )}
            </section>

            {/* ── Unterschrift, Abschluss und Bericht ── */}
            <section id="abschluss" aria-labelledby="abschluss-titel" className="scroll-mt-28 space-y-6">
                <h2 id="abschluss-titel" className="text-lg font-semibold text-slate-900">Unterschrift und Abschluss</h2>

                <Card className="p-4 sm:p-6">
                    <h3 className="mb-4 text-base font-semibold text-slate-900">Ergebnis</h3>
                    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                        {([
                            ['IO', protokoll.statistik.inOrdnung],
                            ['NIO', protokoll.statistik.nichtInOrdnung],
                            ['NA', protokoll.statistik.nichtAnwendbar],
                        ] as [WartungErgebnis, number][]).map(([ergebnis, anzahl]) => (
                            <div key={ergebnis} className="rounded-lg border border-slate-200 p-3">
                                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{ERGEBNIS_ANZEIGE[ergebnis].lang}</dt>
                                <dd className={cn('mt-1 text-2xl font-bold tabular-nums', ergebnis === 'NIO' && anzahl > 0 ? 'text-rose-700' : 'text-slate-900')}>{anzahl}</dd>
                            </div>
                        ))}
                        <div className="rounded-lg border border-slate-200 p-3">
                            <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Noch offen</dt>
                            <dd className={cn('mt-1 text-2xl font-bold tabular-nums', offene > 0 ? 'text-amber-700' : 'text-slate-900')}>{offene}</dd>
                        </div>
                    </dl>

                    {maengel.length > 0 && (
                        <div className="mt-5">
                            <h4 className="mb-2 text-sm font-semibold text-rose-700">Festgestellte Mängel</h4>
                            <ul className="flex flex-col gap-2">
                                {maengel.map((m) => (
                                    <li key={m.id} className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm">
                                        <p className="font-medium text-slate-900">{m.label}</p>
                                        {m.bemerkung && <p className="mt-0.5 text-slate-600 whitespace-pre-wrap">{m.bemerkung}</p>}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </Card>

                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                    <Unterschriftenfeld
                        titel="Mitarbeiter"
                        bild={protokoll.unterschriftTechniker.bild}
                        name={protokoll.unterschriftTechniker.name}
                        vorschlagName={protokoll.techniker?.name}
                        bearbeitbar={protokoll.bearbeitbar}
                        onSpeichern={speichereUnterschrift('TECHNIKER')}
                        onLoeschen={loescheUnterschrift('TECHNIKER')}
                        gespeicherteVorhanden={gespeicherteUnterschrift !== null}
                        onGespeicherteVerwenden={verwendeGespeicherteUnterschrift}
                        onMerken={merkeUnterschrift}
                    />
                    <Unterschriftenfeld
                        titel="Kunde"
                        bild={protokoll.unterschriftKunde.bild}
                        name={protokoll.unterschriftKunde.name}
                        vorschlagName={protokoll.ansprechpartnerName ?? undefined}
                        bearbeitbar={protokoll.bearbeitbar}
                        onSpeichern={speichereUnterschrift('KUNDE')}
                        onLoeschen={loescheUnterschrift('KUNDE')}
                    />
                </div>
                {gespeicherteUnterschrift && (
                    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-500">
                        <span>Ihre Unterschrift ist für künftige Feldeinsätze gespeichert (vom {gespeichertAm}).</span>
                        <button type="button" className="inline-flex min-h-11 items-center gap-1 text-rose-700 underline underline-offset-2 hover:text-rose-800 focus:outline-none focus:ring-2 focus:ring-rose-500 rounded"
                            onClick={() => void loescheGespeicherteUnterschrift()}>
                            <Trash2 className="h-4 w-4" aria-hidden="true" /> Gespeicherte Unterschrift löschen
                        </button>
                    </p>
                )}

                {protokoll.bearbeitbar && (
                    <Card className="p-4 sm:p-6">
                        <h3 className="mb-3 text-base font-semibold text-slate-900">Feldeinsatz abschließen</h3>
                        <ul className="mb-4 flex flex-col gap-2 text-sm">
                            <li className="flex items-start gap-2">
                                {offene === 0
                                    ? <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
                                    : <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" aria-hidden="true" />}
                                <span className="text-slate-700">
                                    {offene === 0 ? 'Alle Prüfpunkte sind bewertet.' : `${offene} ${offene === 1 ? 'Prüfpunkt ist' : 'Prüfpunkte sind'} noch nicht bewertet.`}
                                    {offene > 0 && (
                                        <button type="button" className="ml-2 min-h-11 text-rose-700 underline underline-offset-2" onClick={() => { setNurOffene(true); scrollZu('pruefpunkte'); }}>
                                            Offene Punkte zeigen
                                        </button>
                                    )}
                                </span>
                            </li>
                            <li className="flex items-start gap-2">
                                {technikerHat
                                    ? <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
                                    : <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" aria-hidden="true" />}
                                <span className="text-slate-700">{technikerHat ? 'Der Mitarbeiter hat unterschrieben.' : 'Der Mitarbeiter muss noch unterschreiben.'}</span>
                            </li>
                        </ul>
                        <Button
                            className="min-h-11"
                            disabled={statusLaeuft || grundFuerAbschluss !== undefined}
                            title={grundFuerAbschluss}
                            onClick={() => wechsle('ABGESCHLOSSEN', {
                                titel: 'Feldeinsatz abschließen?',
                                text: 'Danach lässt sich das Protokoll nicht mehr ändern, außer Sie öffnen es wieder. Die nächste Prüfung der Wartung wird eingeplant.',
                                ok: 'Abschließen',
                                art: 'info',
                            })}
                        >
                            {statusLaeuft && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                            Feldeinsatz abschließen
                        </Button>
                        {grundFuerAbschluss && <p className="mt-2 text-sm text-slate-500">{grundFuerAbschluss}</p>}
                    </Card>
                )}

                {abgeschlossen && (
                    <Card className="p-4 sm:p-6">
                        <h3 className="mb-1 text-base font-semibold text-slate-900">Bericht</h3>
                        <p className="text-sm text-slate-600">
                            Abgeschlossen am {formatDatum(protokoll.abgeschlossenAm)}.
                            {protokoll.naechsteWartung && <> Nächste Prüfung: <span className="font-semibold">{formatDatum(protokoll.naechsteWartung)}</span>.</>}
                        </p>
                        {protokoll.projekt ? (
                            <p className="mt-2 text-sm text-slate-600">Der Bericht wird im Projekt <span className="font-medium text-slate-900">{protokoll.projekt.name}</span> abgelegt.</p>
                        ) : (
                            <p className="mt-2 text-sm text-amber-700" role="status">
                                Dieser Feldeinsatz gehört zu keinem Projekt. Zum Ablegen des Berichts bitte oben bei den Angaben ein Projekt wählen: Dafür den Feldeinsatz kurz wieder öffnen.
                            </p>
                        )}
                        <div className="mt-3 flex flex-wrap items-center gap-2">
                            <Button className="min-h-11" onClick={() => void legeBerichtImProjektAb()} disabled={berichtLaeuft || !protokoll.projekt}
                                title={protokoll.projekt ? undefined : 'Zuerst ein Projekt zuordnen'}>
                                {berichtLaeuft ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <FileText className="h-4 w-4" aria-hidden="true" />}
                                {bericht ? 'Bericht neu erstellen' : 'PDF-Bericht im Projekt speichern'}
                            </Button>
                            {bericht && (
                                <a href={bericht.url} target="_blank" rel="noopener noreferrer"
                                    className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-rose-200 bg-white px-4 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                                    <ExternalLink className="h-4 w-4" aria-hidden="true" /> Bericht öffnen
                                </a>
                            )}
                        </div>
                        {bericht && <p className="mt-2 text-xs text-slate-500">Der Bericht liegt im Projekt unter Dokumente{bericht.erstelltAm ? ` (erstellt ${formatDatum(bericht.erstelltAm)})` : ''}.</p>}
                    </Card>
                )}
            </section>
        </PageLayout>
    );
}
