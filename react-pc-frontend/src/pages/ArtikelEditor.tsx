import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BookTemplate, RefreshCw, FolderPlus, Plus, Percent, Cuboid } from "lucide-react";
import { Button } from "../components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { cn } from "../lib/utils";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { ArtikelSuche, type ArtikelSucheHandle } from "../components/artikel/ArtikelSuche";
import { AufschlagPflege } from "../components/artikel/AufschlagPflege";
import { useDarfAufschlaegeAendern } from "../features/aufschlaege/useDarfAufschlaegeAendern";
import { WerkstoffPflege } from "../components/artikel/WerkstoffPflege";
import { VorlagenUebernehmenDialog } from "../components/VorlagenUebernehmenDialog";

export default function ArtikelEditor() {
    const navigate = useNavigate();
    const sucheRef = useRef<ArtikelSucheHandle>(null);
    const aufschlaegeGeaendert = useRef(false);
    const [loading, setLoading] = useState(false);
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showVorlagen, setShowVorlagen] = useState(false);
    const darfAufschlaegeAendern = useDarfAufschlaegeAendern();
    const [showAufschlaege, setShowAufschlaege] = useState(false);
    const schliesseAufschlaege = (offen: boolean) => {
        setShowAufschlaege(offen);
        if (!offen && aufschlaegeGeaendert.current) { aufschlaegeGeaendert.current = false; sucheRef.current?.neuLaden(); }
    };
    const [showWerkstoffe, setShowWerkstoffe] = useState(false);
    const [sucheSchluessel, setSucheSchluessel] = useState(0);
    const werkstoffeGeaendert = useRef(false);
    const werkstoffFensterSchliessen = () => {
        setShowWerkstoffe(false);
        if (werkstoffeGeaendert.current) {
            werkstoffeGeaendert.current = false;
            setSucheSchluessel(k => k + 1);
        }
    };

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Artikelübersicht"
            subtitle="Verwaltung der Artikel und Preise."
            actions={
                <>
                    <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => setShowCreateModal(true)}>
                        <Plus className="w-4 h-4 mr-2" />
                        Neu
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => setShowVorlagen(true)}
                    >
                        <BookTemplate className="w-4 h-4 mr-2" />
                        Stahlprofile aus Vorlage
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => setShowCategoryManagerModal(true)}
                    >
                        <FolderPlus className="w-4 h-4 mr-2" />
                        Kategorien anlegen
                    </Button>
                    {darfAufschlaegeAendern && (
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={() => setShowAufschlaege(true)}
                        >
                            <Percent className="w-4 h-4 mr-2" aria-hidden="true" />
                            Aufschläge
                        </Button>
                    )}
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => setShowWerkstoffe(true)}
                    >
                        <Cuboid className="w-4 h-4 mr-2" aria-hidden="true" />
                        Werkstoffe pflegen
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => sucheRef.current?.neuLaden()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            <ArtikelSuche key={sucheSchluessel} ref={sucheRef} urlSync onLadezustand={setLoading} />

            {showCreateModal && (
                <CreateArticleModal
                    onClose={() => setShowCreateModal(false)}
                    onSave={(artikel) => {
                        setShowCreateModal(false);
                        navigate(`/artikel/${encodeURIComponent(artikel.id)}`, { state: { vonListe: true } });
                    }}
                />
            )}
            {showCategoryManagerModal && (
                <CategoryTreeModal
                    mode="manage"
                    onClose={() => setShowCategoryManagerModal(false)}
                />
            )}
            <VorlagenUebernehmenDialog
                art="profile"
                offen={showVorlagen}
                onOpenChange={setShowVorlagen}
                onFertig={() => { setShowVorlagen(false); sucheRef.current?.neuLaden(); }}
            />
            {darfAufschlaegeAendern && (
                <Dialog open={showAufschlaege} onOpenChange={schliesseAufschlaege} className="w-full max-w-5xl max-h-[90vh]" aria-labelledby="artikel-aufschlaege-titel">
                    <DialogContent>
                        <DialogHeader>
                            <DialogTitle id="artikel-aufschlaege-titel">Aufschläge</DialogTitle>
                            <DialogDescription>Wie viel Prozent auf den Einkaufspreis kommen, damit der Verkaufspreis stimmt.</DialogDescription>
                        </DialogHeader>
                        <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                            {showAufschlaege && <AufschlagPflege onGeaendert={() => { aufschlaegeGeaendert.current = true; }} />}
                        </div>
                        <DialogFooter>
                            <Button variant="outline" size="sm" onClick={() => schliesseAufschlaege(false)}>Schließen</Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            )}
            <Dialog open={showWerkstoffe} onOpenChange={(offen) => { if (offen) setShowWerkstoffe(true); else werkstoffFensterSchliessen(); }} className="w-full max-w-5xl max-h-[90vh]" aria-labelledby="artikel-werkstoffe-titel">
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle id="artikel-werkstoffe-titel">Werkstoffe</DialogTitle>
                        <DialogDescription>Die Auswahlliste „Werkstoff" am Artikel: Stahl, Edelstahl, Aluminium und was Sie sonst verarbeiten.</DialogDescription>
                    </DialogHeader>
                    <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                        {showWerkstoffe && <WerkstoffPflege onGeaendert={() => { werkstoffeGeaendert.current = true; }} />}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" size="sm" onClick={werkstoffFensterSchliessen}>Schließen</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
}
