import { useState } from 'react';
import { Loader2, Mail, Paperclip } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
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

/**
 * E-Mail an den Lieferanten mit dem Bestellformular als PDF im Anhang. Die Bestellnummer steht im Betreff, damit die Antwort später zugeordnet werden
 * kann. Die E-Mail geht über denselben Weg wie alle anderen (Absender aus dem gewählten Benutzerprofil).
 */
export function BestellMailDialog({ bestellung, onGesendet, onSchliessen }: Props) {
    const nummer = bestellung.bestellnummer ?? '';
    const [empfaenger, setEmpfaenger] = useState(bestellung.lieferantEmails[0] ?? '');
    const [betreff, setBetreff] = useState(`Bestellung ${nummer}`);
    const [text, setText] = useState(
        `Guten Tag,\n\nanbei unsere Bestellung ${nummer}.\n\nBitte bestätigen Sie uns die Bestellung mit dem Liefertermin und nennen Sie die Bestellnummer auf Auftragsbestätigung, Lieferschein und Rechnung.\n\nVielen Dank und freundliche Grüße`,
    );
    const [sendet, setSendet] = useState(false);
    const [fehler, setFehler] = useState('');

    const adresseOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(empfaenger.trim());

    // Alle Positions-Anhänge aus dem bestellung-Objekt zusammensammeln
    const alleAnhaenge = bestellung.positionen.flatMap(p => p.anhaenge ?? []);
    const anzahlAnhaenge = alleAnhaenge.length;

    const senden = async () => {
        setSendet(true);
        setFehler('');
        try {
            const pdf = await fetch(pdfAdresse(bestellung.id));
            if (!pdf.ok) throw new Error('Das Bestellformular konnte nicht erstellt werden.');
            const blob = await pdf.blob();

            // Positions-Anhänge laden
            const anhangBlobs = await Promise.all(
                alleAnhaenge.map(async (a) => {
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
            form.append('attachments', blob, `Bestellung_${nummer.replace(/[^A-Za-z0-9._-]/g, '_')}.pdf`);
            anhangBlobs.forEach(a => {
                if (a) form.append('attachments', a.blob, a.name);
            });
            const res = await fetch('/api/emails/send', { method: 'POST', body: form });
            if (!res.ok) throw new Error('Die E-Mail konnte nicht gesendet werden. Bitte die E-Mail-Einstellungen prüfen.');
            onGesendet();
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die E-Mail konnte nicht gesendet werden.');
        } finally {
            setSendet(false);
        }
    };

    return (
        <Dialog open onOpenChange={(o) => { if (!o && !sendet) onSchliessen(); }} aria-label={`Bestellung ${nummer} per E-Mail senden`}>
            <DialogContent className="sm:max-w-[620px]">
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
                    <div className="flex flex-col gap-1 text-sm text-slate-600">
                        <p className="flex items-center gap-2"><Paperclip className="h-4 w-4" /> Bestellung_{nummer}.pdf</p>
                        {anzahlAnhaenge > 0 && alleAnhaenge.map(a => (
                            <p key={a.id} className="flex items-center gap-2 pl-6"><Paperclip className="h-3.5 w-3.5 text-slate-400" /> {a.originalDateiname}</p>
                        ))}
                    </div>
                    {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
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
