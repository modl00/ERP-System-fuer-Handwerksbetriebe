import { Wrench } from 'lucide-react';
import { MODUL_ICONS } from './modulIcons';

export function ModulIcon({ name, className }: { name: string | null | undefined; className?: string }) {
    const Icon = (name && MODUL_ICONS[name]) || Wrench;
    return <Icon className={className ?? 'w-5 h-5'} aria-hidden="true" />;
}
