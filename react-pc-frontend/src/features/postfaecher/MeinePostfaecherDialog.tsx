import { useCallback, useEffect, useState } from 'react';
import { Inbox, Loader2, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { ANBIETER, EIGENER_SERVER, anbieterFuer } from '../../components/settings/sections/mailAnbieter';
import { aenderePostfach, ladePostfaecher, legePostfachAn, loeschePostfach, testePostfach, type Postfach, type PostfachEingabe, type Testergebnis } from './postfaecherApi';

const LEER: PostfachEingabe = { bezeichnung: '', emailAdresse: '', imapHost: '', imapPort: 993, smtpHost: '', smtpPort: 465, benutzername: '', passwort: '', aktiv: true };

const ausPostfach = (p: Postfach): PostfachEingabe => ({
    bezeichnung: p.bezeichnung, emailAdresse: p.emailAdresse, imapHost: p.imapHost, imapPort: p.imapPort, smtpHost: p.smtpHost ?? '', smtpPort: p.smtpPort ?? 465,
    benutzername: p.benutzername === p.emailAdresse ? '' : p.benutzername, passwort: '', aktiv: p.aktiv,
});

interface Props { offen: boolean; onSchliessen: () => void }

/**
 * „Meine Postfächer" (Issue #272, Stufe 1): Jeder Benutzer verwaltet hier seine eigenen Postfächer, mit verschlüsselt gespeichertem Passwort und Verbindungstest.
 * Das Abholen der Mails aus diesen Postfächern folgt in einem nächsten Schritt.
 */
export function MeinePostfaecherDialog({ offen, onSchliessen }: Props) {
    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto">
                {offen && <Inhalt />}
            </DialogContent>
        </Dialog>
    );
}

function Inhalt() {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [liste, setListe] = useState<Postfach[] | null>(null);
    const [fehler, setFehler] = useState('');
    // null = Liste; sonst das Formular: id = null heißt neu
    const [bearbeite, setBearbeite] = useState<{ id: number | null; daten: PostfachEingabe } | null>(null);

    // Ein Zähler statt eines direkten Aufrufs: Jede Änderung zählt hoch und lädt die Liste neu
    const [ladeStand, setLadeStand] = useState(0);
    const laden = useCallback(async () => { setLadeStand(n => n + 1); }, []);

    useEffect(() => {
        let aktuell = true;
        ladePostfaecher().then(
            (l) => { if (aktuell) { setListe(l); setFehler(''); } },
            (e) => { if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Postfächer konnten nicht geladen werden.'); },
        );
        return () => { aktuell = false; };
    }, [ladeStand]);

    const loeschen = async (p: Postfach) => {
        const ja = await bestaetige({ title: 'Postfach entfernen?', message: `Das Postfach „${p.bezeichnung}" (${p.emailAdresse}) wird aus dem Programm entfernt. In Ihrem Postfach beim Anbieter ändert sich nichts.`, confirmLabel: 'Entfernen', variant: 'danger' });
        if (!ja) return;
        try {
            await loeschePostfach(p.id);
            toast.success('Das Postfach wurde entfernt.');
            await laden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das Postfach konnte nicht gelöscht werden.');
        }
    };

    if (bearbeite) {
        return <Formular id={bearbeite.id} start={bearbeite.daten} onZurueck={() => setBearbeite(null)} onGespeichert={async () => { setBearbeite(null); await laden(); }} />;
    }

    return (
        <>
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2"><Inbox className="h-5 w-5 text-rose-600" aria-hidden="true" /> Meine Postfächer</DialogTitle>
                <DialogDescription>Hier legen Sie Ihre eigenen E-Mail-Postfächer an. Das Passwort wird verschlüsselt gespeichert und nie wieder angezeigt. Das Abholen der Mails aus diesen Postfächern folgt in einem nächsten Schritt.</DialogDescription>
            </DialogHeader>
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {liste === null && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>}
            {liste && liste.length === 0 && <p className="text-sm text-slate-500">Sie haben noch kein eigenes Postfach angelegt.</p>}
            {liste && liste.length > 0 && (
                <ul className="space-y-2" data-testid="postfach-liste">
                    {liste.map(p => (
                        <li key={p.id} className="flex items-start gap-3 rounded-lg border border-slate-200 p-3">
                            <div className="min-w-0 flex-1">
                                <p className="truncate font-medium text-slate-900">{p.bezeichnung} {!p.aktiv && <span className="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs font-normal text-slate-600">aus</span>}</p>
                                <p className="truncate text-sm text-slate-600">{p.emailAdresse}</p>
                                <p className="truncate text-xs text-slate-500">Empfang: {p.imapHost}:{p.imapPort}{p.smtpHost ? ` · Versand: ${p.smtpHost}:${p.smtpPort}` : ''}</p>
                            </div>
                            <Button variant="ghost" size="sm" aria-label={`${p.bezeichnung} bearbeiten`} onClick={() => setBearbeite({ id: p.id, daten: ausPostfach(p) })}><Pencil className="h-4 w-4" aria-hidden="true" /></Button>
                            <Button variant="ghost" size="sm" aria-label={`${p.bezeichnung} entfernen`} onClick={() => void loeschen(p)}><Trash2 className="h-4 w-4 text-rose-600" aria-hidden="true" /></Button>
                        </li>
                    ))}
                </ul>
            )}
            <DialogFooter>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => setBearbeite({ id: null, daten: LEER })}><Plus className="h-4 w-4" aria-hidden="true" /> Postfach hinzufügen</Button>
            </DialogFooter>
        </>
    );
}

function Formular({ id, start, onZurueck, onGespeichert }: { id: number | null; start: PostfachEingabe; onZurueck: () => void; onGespeichert: () => Promise<void> }) {
    const toast = useToast();
    const [d, setD] = useState<PostfachEingabe>(start);
    const [anbieter, setAnbieter] = useState(() => (start.imapHost ? anbieterFuer(start.smtpHost, start.imapHost) : EIGENER_SERVER));
    const [fehler, setFehler] = useState('');
    const [test, setTest] = useState<Testergebnis | null>(null);
    const [arbeitet, setArbeitet] = useState<'test' | 'speichern' | null>(null);
    const setze = <K extends keyof PostfachEingabe>(k: K, v: PostfachEingabe[K]) => { setD(alt => ({ ...alt, [k]: v })); setTest(null); };

    const waehleAnbieter = (wert: string) => {
        setAnbieter(wert);
        const a = ANBIETER.find(x => x.id === wert);
        if (a) setD(alt => ({ ...alt, imapHost: a.imap.host, imapPort: a.imap.port, smtpHost: a.smtp.host, smtpPort: a.smtp.port }));
        setTest(null);
    };
    const hinweis = ANBIETER.find(a => a.id === anbieter)?.hinweis;
    const kannTesten = d.emailAdresse.trim() !== '' && d.imapHost.trim() !== '' && (d.passwort !== '' || id != null);

    const testen = async () => {
        setArbeitet('test');
        setFehler('');
        try { setTest(await testePostfach(d, id ?? undefined)); } catch (e) { setFehler(e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.'); } finally { setArbeitet(null); }
    };

    const speichern = async () => {
        setArbeitet('speichern');
        setFehler('');
        try {
            if (id == null) await legePostfachAn(d); else await aenderePostfach(id, d);
            toast.success('Das Postfach ist gespeichert.');
            await onGespeichert();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Das Postfach konnte nicht gespeichert werden.');
            setArbeitet(null);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle>{id == null ? 'Postfach hinzufügen' : 'Postfach ändern'}</DialogTitle>
                <DialogDescription>Wählen Sie Ihren Anbieter, dann trägt das Programm die Server ein. Das Passwort wird verschlüsselt gespeichert.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
                <div>
                    <Label htmlFor="pf-anbieter">Anbieter</Label>
                    <select id="pf-anbieter" value={anbieter} onChange={e => waehleAnbieter(e.target.value)} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm">
                        <option value={EIGENER_SERVER}>Eigener Server</option>
                        {ANBIETER.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                    {hinweis && <p className="mt-1 text-xs text-slate-600">{hinweis}</p>}
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                    <div><Label htmlFor="pf-name">Name des Postfachs</Label><Input id="pf-name" value={d.bezeichnung} maxLength={120} placeholder="z. B. Mein Postfach" onChange={e => setze('bezeichnung', e.target.value)} /></div>
                    <div><Label htmlFor="pf-adresse">E-Mail-Adresse</Label><Input id="pf-adresse" type="email" value={d.emailAdresse} maxLength={255} onChange={e => setze('emailAdresse', e.target.value)} /></div>
                    <div><Label htmlFor="pf-benutzer">Benutzername (leer = die Adresse)</Label><Input id="pf-benutzer" value={d.benutzername} maxLength={255} autoComplete="off" onChange={e => setze('benutzername', e.target.value)} /></div>
                    <div><Label htmlFor="pf-passwort">{id == null ? 'Passwort' : 'Passwort (leer = unverändert)'}</Label><Input id="pf-passwort" type="password" value={d.passwort} autoComplete="new-password" onChange={e => setze('passwort', e.target.value)} /></div>
                    <div><Label htmlFor="pf-imap">Empfangs-Server (IMAP)</Label><Input id="pf-imap" value={d.imapHost} placeholder="imap.beispiel.de" onChange={e => { setAnbieter(EIGENER_SERVER); setze('imapHost', e.target.value); }} /></div>
                    <div><Label htmlFor="pf-imap-port">Port Empfang</Label>
                        <select id="pf-imap-port" value={d.imapPort} onChange={e => setze('imapPort', Number(e.target.value))} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm"><option value={993}>993 (SSL/TLS)</option><option value={143}>143 (STARTTLS)</option></select></div>
                    <div><Label htmlFor="pf-smtp">Sende-Server (SMTP, freiwillig)</Label><Input id="pf-smtp" value={d.smtpHost} placeholder="smtp.beispiel.de" onChange={e => { setAnbieter(EIGENER_SERVER); setze('smtpHost', e.target.value); }} /></div>
                    <div><Label htmlFor="pf-smtp-port">Port Versand</Label>
                        <select id="pf-smtp-port" value={d.smtpPort ?? 465} onChange={e => setze('smtpPort', Number(e.target.value))} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm"><option value={465}>465 (SSL/TLS)</option><option value={587}>587 (STARTTLS)</option><option value={25}>25</option></select></div>
                </div>
                <label className="flex items-center gap-2 text-sm text-slate-800"><input type="checkbox" className="h-4 w-4 accent-rose-600" checked={d.aktiv} onChange={e => setze('aktiv', e.target.checked)} /> Postfach ist eingeschaltet</label>
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {test && <p role="status" className={`rounded-lg p-3 text-sm ${test.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>{test.meldung}</p>}
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={onZurueck} disabled={arbeitet !== null}>Zurück</Button>
                <Button variant="outline" onClick={() => void testen()} disabled={!kannTesten || arbeitet !== null}>{arbeitet === 'test' && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Verbindung testen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={arbeitet !== null || !d.bezeichnung.trim() || !d.emailAdresse.trim() || !d.imapHost.trim() || (id == null && d.passwort === '')}>
                    {arbeitet === 'speichern' && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Speichern
                </Button>
            </DialogFooter>
        </>
    );
}
