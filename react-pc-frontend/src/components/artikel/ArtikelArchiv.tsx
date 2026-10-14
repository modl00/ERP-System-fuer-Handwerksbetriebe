import { useImperativeHandle, useRef, useState, type Ref } from "react";
import { ArchiveRestore, Trash2 } from "lucide-react";
import { Button } from "../ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "../ui/dialog";
import { useOptionalAuth } from "../../auth/AuthContext";
import { useToast } from "../ui/toast";
import { ArtikelSuche, type ArtikelSucheHandle } from "./ArtikelSuche";
import { ArtikelLoeschen } from "./ArtikelLoeschen";
import { stelleArtikelWiederHer } from "./artikelArchivApi";
import type { Artikel } from "../../types";

export interface ArtikelArchivHandle { neuLaden: () => void }

/**
 * Der Reiter „Archiv“ der Artikelübersicht: alle archivierten Artikel. Jeder kann einen Artikel wiederherstellen; endgültig löschen darf nur ein
 * Administrator, und zwar erst nach der Ansicht, wo der Artikel überall verwendet wird.
 */
export function ArtikelArchiv({ ref, onLadezustand }: { ref?: Ref<ArtikelArchivHandle>; onLadezustand?: (laedt: boolean) => void }) {
    const toast = useToast();
    const darfLoeschen = useOptionalAuth()?.isAdmin ?? false;
    const sucheRef = useRef<ArtikelSucheHandle>(null);
    const [loeschen, setLoeschen] = useState<Artikel | null>(null);
    const [arbeitet, setArbeitet] = useState<number | null>(null);

    useImperativeHandle(ref, () => ({ neuLaden: () => sucheRef.current?.neuLaden() }), []);

    const wiederherstellen = async (a: Artikel) => {
        setArbeitet(a.id);
        try {
            await stelleArtikelWiederHer(a.id);
            toast.success(`„${a.produktname}“ ist wiederhergestellt und steht wieder in der Artikelliste.`);
            sucheRef.current?.neuLaden();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : "Der Artikel konnte nicht wiederhergestellt werden.");
        } finally {
            setArbeitet(null);
        }
    };

    return (
        <div className="space-y-3" data-testid="artikel-archiv">
            <p className="text-sm text-slate-600">
                Hier liegen Artikel, die aus Liste und Auswahl genommen wurden. Sie lassen sich jederzeit wiederherstellen.
                {!darfLoeschen && " Endgültig löschen darf nur ein Administrator."}
            </p>
            <ArtikelSuche ref={sucheRef} urlSync={false} archiv onLadezustand={onLadezustand}
                zeilenAktion={a => (
                    <div className="flex flex-wrap justify-center gap-1.5">
                        <Button type="button" variant="outline" size="sm" disabled={arbeitet === a.id} onClick={() => void wiederherstellen(a)}
                            aria-label={`${a.produktname} wiederherstellen`}>
                            <ArchiveRestore className="w-4 h-4 mr-1.5" aria-hidden="true" />
                            Wiederherstellen
                        </Button>
                        {darfLoeschen && (
                            <Button type="button" variant="ghost" size="sm" onClick={() => setLoeschen(a)} aria-label={`${a.produktname} endgültig löschen`}>
                                <Trash2 className="w-4 h-4 mr-1.5" aria-hidden="true" />
                                Löschen
                            </Button>
                        )}
                    </div>
                )} />

            <Dialog open={loeschen != null} onOpenChange={offen => { if (!offen) setLoeschen(null); }} className="w-full max-w-2xl max-h-[90vh]" aria-labelledby="artikel-loeschen-titel">
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle id="artikel-loeschen-titel">Artikel endgültig löschen</DialogTitle>
                        <DialogDescription>Erst ansehen, wo der Artikel verwendet wird, dann entscheiden.</DialogDescription>
                    </DialogHeader>
                    <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                        {loeschen && (
                            <ArtikelLoeschen artikelId={loeschen.id} name={loeschen.produktname} onAbbrechen={() => setLoeschen(null)}
                                onGeloescht={() => { setLoeschen(null); sucheRef.current?.neuLaden(); }} />
                        )}
                    </div>
                </DialogContent>
            </Dialog>
        </div>
    );
}
