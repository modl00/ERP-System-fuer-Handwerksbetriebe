import { useRef, useState } from "react";
import { RefreshCw, Plus, BookTemplate, Layers, Package2, Percent, X } from "lucide-react";
import { Button } from "../components/ui/button";
import { cn } from "../lib/utils";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { ArtikelSuche, type ArtikelSucheHandle } from "../components/artikel/ArtikelSuche";
import { VorlagenUebernehmenDialog } from "../components/VorlagenUebernehmenDialog";
import WerkstoffEditor from "./WerkstoffEditor";
import Aufschlaege from "./Aufschlaege";

function EingebettetDialog({ titel, onSchliessen, children }: { titel: string; onSchliessen: () => void; children: React.ReactNode }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onSchliessen}>
            <div
                className="relative w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-xl bg-white shadow-2xl"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
                    <h2 className="text-base font-semibold text-slate-900">{titel}</h2>
                    <button
                        type="button"
                        onClick={onSchliessen}
                        aria-label="Schließen"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                    >
                        <X className="h-4 w-4" />
                    </button>
                </div>
                <div className="p-0">{children}</div>
            </div>
        </div>
    );
}

function WerkstoffeDialog({ onSchliessen }: { onSchliessen: () => void }) {
    return <EingebettetDialog titel="Werkstoffe" onSchliessen={onSchliessen}><WerkstoffEditor eingebettet /></EingebettetDialog>;
}

function AufschlaegeDialog({ onSchliessen }: { onSchliessen: () => void }) {
    return <EingebettetDialog titel="Aufschläge" onSchliessen={onSchliessen}><Aufschlaege eingebettet /></EingebettetDialog>;
}

export default function ArtikelEditor() {
    const sucheRef = useRef<ArtikelSucheHandle>(null);
    const [loading, setLoading] = useState(false);
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showVorlagen, setShowVorlagen] = useState(false);
    const [showWerkstoffe, setShowWerkstoffe] = useState(false);
    const [showAufschlaege, setShowAufschlaege] = useState(false);

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Artikelverwaltung"
            subtitle="Artikel, Werkstoffe und Aufschläge verwalten."
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
                        className="border-slate-300 text-slate-700 hover:bg-slate-50"
                        onClick={() => setShowWerkstoffe(true)}
                    >
                        <Package2 className="w-4 h-4 mr-2" />
                        Werkstoffe
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-slate-300 text-slate-700 hover:bg-slate-50"
                        onClick={() => setShowAufschlaege(true)}
                    >
                        <Percent className="w-4 h-4 mr-2" />
                        Aufschläge
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-slate-300 text-slate-700 hover:bg-slate-50"
                        onClick={() => setShowCategoryManagerModal(true)}
                    >
                        <Layers className="w-4 h-4 mr-2" />
                        Kategorien
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => sucheRef.current?.neuLaden()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            <ArtikelSuche ref={sucheRef} urlSync onLadezustand={setLoading} />

            {showCreateModal && (
                <CreateArticleModal
                    onClose={() => setShowCreateModal(false)}
                    onSave={() => { sucheRef.current?.neuLaden(); setShowCreateModal(false); }}
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
            {showWerkstoffe && <WerkstoffeDialog onSchliessen={() => setShowWerkstoffe(false)} />}
            {showAufschlaege && <AufschlaegeDialog onSchliessen={() => setShowAufschlaege(false)} />}
        </PageLayout>
    );
}
