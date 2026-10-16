import { useState } from 'react';
import { Settings2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { useAuth } from '../auth/AuthContext';
import { baustein } from '../features/startbildschirm/bausteine';
import { useStart } from '../features/startbildschirm/useStart';
import { StartAnpassenDialog } from '../features/startbildschirm/StartAnpassenDialog';

/**
 * Startbildschirm (#290): Bausteine mit Funktionen und Auswertungen, die jeder selbst auswählt und ordnet. Die Firma kann einen Aufbau vorgeben. Jeder Baustein zeigt nur,
 * was der Benutzer ohnehin sehen darf; ist etwas nicht verfügbar, steht das im Baustein.
 */
export default function Startbildschirm() {
    const { isAdmin } = useAuth();
    const { eintraege } = useStart();
    const [anpassen, setAnpassen] = useState(false);
    const sichtbar = eintraege.map(baustein).filter((b): b is NonNullable<ReturnType<typeof baustein>> => b != null);

    return (
        <PageLayout
            ribbonCategory="Projektmanagement"
            title="Startbildschirm"
            subtitle="Das Wichtigste auf einen Blick. Mit „Anpassen“ wählst du selbst, welche Bausteine hier stehen und in welcher Reihenfolge."
            actions={
                <Button variant="outline" size="sm" onClick={() => setAnpassen(true)}>
                    <Settings2 className="mr-2 h-4 w-4" aria-hidden="true" /> Anpassen
                </Button>
            }
        >
            {sichtbar.length === 0 ? (
                <p className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600" data-testid="start-leer">
                    Noch keine Bausteine ausgewählt. Mit „Anpassen“ legst du fest, was hier stehen soll.
                </p>
            ) : (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3" data-testid="start-bausteine">
                    {sichtbar.map(b => <div key={b.id}><b.Inhalt /></div>)}
                </div>
            )}
            {anpassen && <StartAnpassenDialog offen onSchliessen={() => setAnpassen(false)} istAdmin={isAdmin} />}
        </PageLayout>
    );
}
