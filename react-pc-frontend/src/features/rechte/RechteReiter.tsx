import { NavLink } from 'react-router-dom';
import { cn } from '../../lib/utils';

/** Die Bereiche von „Wer darf was?"; jeder hat seine eigene Adresse, damit alte Lesezeichen weiter gehen. */
const RECHTE_BEREICHE = [
    { href: '/rechte-uebersicht', label: 'Wer darf was?' },
    { href: '/abteilung-berechtigungen', label: 'Rechte der Abteilungen' },
] as const;

/**
 * Reiter oben auf den Seiten der Rechteverwaltung: erst sehen, wer was darf, dann bei der Abteilung ändern. Ein Menüpunkt statt zwei, die Seiten
 * selbst bleiben unverändert.
 */
export function RechteReiter() {
    return (
        <nav aria-label="Bereiche der Rechteverwaltung" className="mb-6 flex flex-wrap gap-1 border-b border-slate-200">
            {RECHTE_BEREICHE.map(b => (
                <NavLink key={b.href} to={b.href} end
                    className={({ isActive }) => cn('-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
                        isActive ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-500 hover:border-slate-200 hover:text-slate-700')}>
                    {b.label}
                </NavLink>
            ))}
        </nav>
    );
}
