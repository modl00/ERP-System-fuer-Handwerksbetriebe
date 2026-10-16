import { useCallback, useEffect, useState } from 'react';
import { ExternalLink, Loader2, RefreshCw, Send } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useToast } from '../components/ui/toast';
import {
    bereiteAuf, ladeEinstellung, ladePunkte, sende, speichereEinstellung,
    type Einordnung, type Einstellung, type Punkt, type Vorschlag,
} from '../features/testbox/testboxApi';

const EINORDNUNGEN: { wert: Einordnung; text: string }[] = [
    { wert: 'FUNKTIONIERT', text: 'Funktioniert' },
    { wert: 'PROBLEM', text: 'Problem' },
    { wert: 'WUNSCH', text: 'Wunsch' },
    { wert: 'ENTSCHEIDUNG', text: 'Entscheidung' },
];
const ANZEIGE: Record<Einordnung, string> = { FUNKTIONIERT: 'Funktioniert', PROBLEM: 'Problem', WUNSCH: 'Wunsch', ENTSCHEIDUNG: 'Entscheidung', ANTWORT: 'Antwort' };

/**
 * Test-Box (nur Administratoren): zeigt die offenen Rückfragen und die Dinge zum Testen aus Gitea. Man antwortet in einem Satz, die KI bereitet den Kommentar auf,
 * und nach der Bestätigung geht er ins Ticket. Die KI formuliert und ordnet nur ein; Schließen und Labels bleiben bei Ihnen.
 */
export default function Testbox() {
    const [einstellung, setEinstellung] = useState<Einstellung | null>(null);
    const [punkte, setPunkte] = useState<Punkt[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [laedt, setLaedt] = useState(false);

    const laden = useCallback(() => {
        setLaedt(true);
        return ladePunkte().then(
            p => { setPunkte(p); setFehler(''); setLaedt(false); },
            e => { setPunkte([]); setFehler(e instanceof Error ? e.message : 'Die offenen Punkte konnten nicht geladen werden.'); setLaedt(false); },
        );
    }, []);

    useEffect(() => {
        let aktiv = true;
        ladeEinstellung().then(
            e => {
                if (!aktiv) return;
                setEinstellung(e);
                if (e.tokenGesetzt && e.url && e.repo) void laden();
                else setPunkte([]);
            },
            e => { if (aktiv) { setFehler(e instanceof Error ? e.message : 'Die Einstellung konnte nicht geladen werden.'); setPunkte([]); } },
        );
        return () => { aktiv = false; };
    }, [laden]);

    const eingerichtet = !!einstellung && einstellung.tokenGesetzt && !!einstellung.url && !!einstellung.repo;

    return (
        <PageLayout
            ribbonCategory="Administration"
            title="Test-Box"
            subtitle="Offene Rückfragen und Dinge zum Testen aus Gitea beantworten. Die KI formuliert den Kommentar, Sie bestätigen ihn."
            actions={eingerichtet ? (
                <Button variant="outline" size="sm" onClick={() => void laden()} disabled={laedt}>
                    <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" /> Aktualisieren
                </Button>
            ) : undefined}
        >
            {einstellung && <Verbindung einstellung={einstellung} offen={!eingerichtet} onGespeichert={e => { setEinstellung(e); void laden(); }} />}
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {(punkte === null || laedt) && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Die offenen Punkte werden geladen …</p>}
            {eingerichtet && punkte !== null && !laedt && punkte.length === 0 && !fehler && <p className="text-sm text-slate-600">Es gibt nichts zu beantworten oder zu testen.</p>}
            <div className="space-y-4">
                {!laedt && punkte?.map(p => <PunktKarte key={p.nummer} punkt={p} onGesendet={() => void laden()} />)}
            </div>
        </PageLayout>
    );
}

function Verbindung({ einstellung, offen, onGespeichert }: { einstellung: Einstellung; offen: boolean; onGespeichert: (e: Einstellung) => void }) {
    const toast = useToast();
    const [url, setUrl] = useState(einstellung.url);
    const [repo, setRepo] = useState(einstellung.repo);
    const [token, setToken] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    const speichern = async () => {
        setArbeitet(true);
        try {
            onGespeichert(await speichereEinstellung({ url, repo, token: token === '' ? undefined : token }));
            setToken('');
            toast.success('Der Zugang zu Gitea ist gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellung konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(false);
        }
    };
    const klasse = 'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500';
    return (
        <details open={offen} className="rounded-xl border border-slate-200 bg-white">
            <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-slate-900">Zugang zu Gitea {einstellung.tokenGesetzt ? '(eingerichtet)' : '(noch nicht eingerichtet)'}</summary>
            <div className="space-y-3 border-t border-slate-100 p-4">
                <p className="text-sm text-slate-600">Der Zugangsschlüssel sollte nur für Tickets (lesen und schreiben) berechtigt sein, nicht für den Code. Er wird nie wieder angezeigt.</p>
                <div className="grid gap-3 sm:grid-cols-2">
                    <label className="text-sm font-medium text-slate-700">Adresse von Gitea
                        <input value={url} onChange={e => setUrl(e.target.value)} placeholder="http://192.168.0.10:3000" className={klasse} />
                    </label>
                    <label className="text-sm font-medium text-slate-700">Repository
                        <input value={repo} onChange={e => setRepo(e.target.value)} placeholder="Besitzer/Name" className={klasse} />
                    </label>
                </div>
                <label className="block text-sm font-medium text-slate-700">Zugangsschlüssel
                    <input type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)}
                        placeholder={einstellung.tokenGesetzt ? 'gesetzt, leer lassen zum Behalten' : 'Schlüssel eintragen'} className={klasse} />
                </label>
                <div className="flex gap-2">
                    <Button size="sm" onClick={() => void speichern()} disabled={arbeitet} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">Speichern</Button>
                    {einstellung.tokenGesetzt && <Button size="sm" variant="outline" disabled={arbeitet} onClick={() => { setToken(''); void speichereEinstellung({ url, repo, token: '' }).then(onGespeichert, () => toast.error('Der Schlüssel konnte nicht entfernt werden.')); }}>Schlüssel entfernen</Button>}
                </div>
            </div>
        </details>
    );
}

function PunktKarte({ punkt, onGesendet }: { punkt: Punkt; onGesendet: () => void }) {
    const toast = useToast();
    const [antwort, setAntwort] = useState('');
    const [einordnung, setEinordnung] = useState<Einordnung>('ANTWORT');
    const [vorschlag, setVorschlag] = useState<Vorschlag | null>(null);
    const [kommentar, setKommentar] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    const aufbereiten = async (art: Einordnung, text: string) => {
        setArbeitet(true);
        try {
            const v = await bereiteAuf(punkt.nummer, text, art);
            setVorschlag(v);
            setKommentar(v.kommentar);
            setEinordnung(v.einordnung);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Antwort konnte nicht aufbereitet werden.');
        } finally {
            setArbeitet(false);
        }
    };

    const waehle = (art: Einordnung) => {
        setEinordnung(art);
        if (art === 'FUNKTIONIERT' && antwort.trim() === '') void aufbereiten(art, 'ok');
    };

    const senden = async () => {
        setArbeitet(true);
        try {
            await sende(punkt.nummer, einordnung, kommentar, antwort);
            toast.success(`Ihre Antwort steht jetzt in Ticket #${punkt.nummer}.`);
            onGesendet();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Antwort konnte nicht gesendet werden.');
            setArbeitet(false);
        }
    };

    return (
        <Card className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <h2 className="text-base font-semibold text-slate-900">#{punkt.nummer} {punkt.titel}</h2>
                <div className="flex flex-wrap items-center gap-2">
                    {punkt.wartet && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">wartet auf Ihre Antwort</span>}
                    {punkt.labels.map(l => <span key={l} className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{l}</span>)}
                    {punkt.url && <a href={punkt.url} target="_blank" rel="noopener noreferrer" aria-label={`Ticket ${punkt.nummer} in Gitea öffnen`} className="text-slate-500 hover:text-rose-700"><ExternalLink className="h-4 w-4" aria-hidden="true" /></a>}
                </div>
            </div>
            <p className="mt-2 max-h-48 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm text-slate-700" aria-label={`Frage zu Ticket ${punkt.nummer}`}>{punkt.frage || 'Keine Beschreibung.'}</p>

            {vorschlag === null ? (
                <div className="mt-3 space-y-2">
                    <div className="flex flex-wrap gap-1" role="group" aria-label={`Einordnung zu Ticket ${punkt.nummer}`}>
                        {EINORDNUNGEN.map(e => (
                            <button key={e.wert} type="button" aria-pressed={einordnung === e.wert} onClick={() => waehle(e.wert)} disabled={arbeitet}
                                className={`rounded-full border px-3 py-1 text-xs font-medium ${einordnung === e.wert ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-300 text-slate-700 hover:border-rose-300'}`}>{e.text}</button>
                        ))}
                    </div>
                    <textarea aria-label={`Ihre Antwort zu Ticket ${punkt.nummer}`} value={antwort} onChange={e => setAntwort(e.target.value)} rows={3} placeholder="Ihre Antwort in ein, zwei Sätzen …"
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500" />
                    <Button size="sm" onClick={() => void aufbereiten(einordnung, antwort)} disabled={arbeitet || antwort.trim() === ''}>
                        {arbeitet && <Loader2 className="mr-2 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />}Aufbereiten
                    </Button>
                </div>
            ) : (
                <div className="mt-3 space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vorschlag für den Kommentar ({ANZEIGE[einordnung]}){vorschlag.ki ? ', von der KI aufbereitet' : ''}</p>
                    {vorschlag.hinweis && <p className="text-xs text-amber-700">{vorschlag.hinweis}</p>}
                    <textarea aria-label={`Kommentar für Ticket ${punkt.nummer}`} value={kommentar} onChange={e => setKommentar(e.target.value)} rows={4}
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500" />
                    <div className="flex gap-2">
                        <Button size="sm" onClick={() => void senden()} disabled={arbeitet || kommentar.trim() === ''} className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                            {arbeitet ? <Loader2 className="mr-2 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Send className="mr-2 h-4 w-4" aria-hidden="true" />}In Gitea senden
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => { setVorschlag(null); setKommentar(''); }} disabled={arbeitet}>Zurück</Button>
                    </div>
                </div>
            )}
        </Card>
    );
}
