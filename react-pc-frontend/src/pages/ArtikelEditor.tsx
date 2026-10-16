import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, FolderPlus, Plus } from "lucide-react";
import { Button } from "../components/ui/button";
import { cn } from "../lib/utils";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { ArtikelSuche, type ArtikelSucheHandle } from "../components/artikel/ArtikelSuche";
import Aufschlaege from "./Aufschlaege";
import WerkstoffEditor from "./WerkstoffEditor";

type TabId = 'artikel' | 'werkstoffe' | 'aufschlaege';
const TABS: { id: TabId; label: string }[] = [
    { id: 'artikel',     label: 'Artikel' },
    { id: 'werkstoffe',  label: 'Werkstoffe' },
    { id: 'aufschlaege', label: 'Aufschläge' },
];

function istGueltigerTab(v: string): v is TabId {
    return TABS.some(t => t.id === v);
}

function useTabInHash(): [TabId, (t: TabId) => void] {
    const [activeTab, setActiveTab] = useState<TabId>(() => {
        const raw = window.location.hash.replace(/^#/, '');
        return istGueltigerTab(raw) ? raw : 'artikel';
    });
    useEffect(() => {
        const onChange = () => {
            const raw = window.location.hash.replace(/^#/, '');
            if (istGueltigerTab(raw)) setActiveTab(raw);
        };
        window.addEventListener('hashchange', onChange);
        return () => window.removeEventListener('hashchange', onChange);
    }, []);
    const wechsle = useCallback((t: TabId) => {
        setActiveTab(t);
        window.history.replaceState(null, '', `${window.location.pathname}#${t}`);
    }, []);
    return [activeTab, wechsle];
}

export default function ArtikelEditor() {
    const [activeTab, wechsleTab] = useTabInHash();
    const sucheRef = useRef<ArtikelSucheHandle>(null);
    const [loading, setLoading] = useState(false);
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Artikelverwaltung"
            subtitle="Artikel, Werkstoffe und Aufschläge verwalten."
            actions={
                activeTab === 'artikel' ? (
                    <>
                        <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => setShowCreateModal(true)}>
                            <Plus className="w-4 h-4 mr-2" />
                            Neu
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
                        <Button variant="outline" size="sm" onClick={() => sucheRef.current?.neuLaden()}>
                            <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                            Aktualisieren
                        </Button>
                    </>
                ) : undefined
            }
        >
            {/* Reiter */}
            <div
                role="tablist"
                aria-label="Artikelverwaltung Bereiche"
                className="flex gap-1 border-b border-slate-200 mb-6"
            >
                {TABS.map(tab => (
                    <button
                        key={tab.id}
                        role="tab"
                        aria-selected={activeTab === tab.id}
                        onClick={() => wechsleTab(tab.id)}
                        className={cn(
                            "px-4 py-2 text-sm font-medium rounded-t transition-colors -mb-px",
                            activeTab === tab.id
                                ? "border border-b-white border-slate-200 bg-white text-rose-700"
                                : "text-slate-600 hover:text-slate-900 hover:bg-slate-50"
                        )}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Inhalt */}
            {activeTab === 'artikel' && (
                <>
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
                </>
            )}
            {activeTab === 'werkstoffe' && <WerkstoffEditor eingebettet />}
            {activeTab === 'aufschlaege' && <Aufschlaege eingebettet />}
        </PageLayout>
    );
}
