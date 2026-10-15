import { useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { EINHEITEN, legeKategorieAn, type NeueKategorie } from './kategorieApi';
import type { LeistungsFolder } from '../../types';

/** Der Pfad „Haupt / Unter“ einer Kategorie für die Auswahl. */
function pfad(f: LeistungsFolder, alle: LeistungsFolder[]): string {
    const eltern = alle.find(x => x.id === f.parentId);
    return eltern ? `${pfad(eltern, alle)} / ${f.name}` : f.name;
}

interface Props {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    folders: LeistungsFolder[];
    /** Vorbelegung der übergeordneten Kategorie (zum Beispiel die gerade gewählte), {@code null} = oberste Ebene. */
    elternId: string | null;
    onAngelegt: (neu: NeueKategorie) => void | Promise<void>;
}

/** Eine neue Kategorie für Leistungen direkt anlegen, ohne die Seite zu verlassen (#234/#233). */
export function NeueKategorieDialog({ open, onOpenChange, folders, elternId, onAngelegt }: Props) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[480px]">
                {open && <Formular folders={folders} elternId={elternId} onAbbrechen={() => onOpenChange(false)} onAngelegt={onAngelegt} />}
            </DialogContent>
        </Dialog>
    );
}

function Formular({ folders, elternId, onAbbrechen, onAngelegt }: { folders: LeistungsFolder[]; elternId: string | null; onAbbrechen: () => void; onAngelegt: Props['onAngelegt'] }) {
    const [name, setName] = useState('');
    const [eltern, setEltern] = useState(elternId ?? '');
    const [einheit, setEinheit] = useState('STUECK');
    const [fehler, setFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const speichern = async () => {
        const sauber = name.trim();
        if (sauber === '') { setFehler('Bitte einen Namen eingeben.'); return; }
        setSpeichert(true);
        setFehler('');
        try {
            const neu = await legeKategorieAn(sauber, eltern || null, einheit);
            await onAngelegt(neu);
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Kategorie konnte nicht angelegt werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle>Neue Kategorie</DialogTitle>
                <DialogDescription>Ordnet Ihre Leistungen. Sie lässt sich danach gleich für die Leistung wählen.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
                <div>
                    <Label htmlFor="neue-kategorie-name">Name</Label>
                    <Input id="neue-kategorie-name" value={name} maxLength={120} autoFocus placeholder="z. B. Geländer"
                        onChange={e => setName(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') void speichern(); }} />
                </div>
                <div>
                    <Label>Gehört zu</Label>
                    <Select value={eltern} onChange={setEltern} aria-label="Gehört zu"
                        options={[{ value: '', label: '– oberste Ebene –' }, ...folders.map(f => ({ value: f.id, label: pfad(f, folders) }))]} />
                </div>
                <div>
                    <Label>Abgerechnet nach</Label>
                    <Select value={einheit} onChange={setEinheit} aria-label="Abgerechnet nach" options={EINHEITEN} />
                </div>
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-2 text-sm text-rose-800">{fehler}</p>}
            </div>
            <DialogFooter>
                <Button variant="outline" onClick={onAbbrechen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void speichern()} disabled={speichert}>
                    {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Plus className="h-4 w-4" aria-hidden="true" />} Anlegen
                </Button>
            </DialogFooter>
        </>
    );
}
