import { Link, useInRouterContext } from 'react-router-dom';
import { Briefcase, User } from 'lucide-react';

interface Props {
    projektId?: number | null;
    projektName?: string | null;
    kundeId?: number | null;
    kundeName?: string | null;
}

/** Springt direkt zum Projekt oder zum Kunden, wie es der Kalender bei Terminen tut. Ohne Angaben wird nichts gezeigt. */
export function VerknuepfungsLinks({ projektId, projektName, kundeId, kundeName }: Props) {
    const imRouter = useInRouterContext();
    if (projektId == null && kundeId == null) return null;
    const klasse = 'inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-600';
    const link = (ziel: string, text: string, titel: string, icon: React.ReactNode) => (imRouter
        ? <Link to={ziel} className={klasse} title={titel}>{icon}{text}</Link>
        : <a href={ziel} className={klasse} title={titel}>{icon}{text}</a>);
    return (
        <div className="flex flex-wrap items-center gap-2" data-testid="verknuepfungen">
            {projektId != null && link(`/projekte?projektId=${encodeURIComponent(String(projektId))}`, projektName ? `Projekt: ${projektName}` : 'Projekt öffnen', 'Das Projekt öffnen', <Briefcase className="h-3.5 w-3.5" aria-hidden="true" />)}
            {kundeId != null && link(`/kunden?kundeId=${encodeURIComponent(String(kundeId))}`, kundeName ? `Kunde: ${kundeName}` : 'Kunde öffnen', 'Den Kunden öffnen', <User className="h-3.5 w-3.5" aria-hidden="true" />)}
        </div>
    );
}
