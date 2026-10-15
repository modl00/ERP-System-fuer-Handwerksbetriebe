import { useCallback, useRef } from 'react';
import { Truck } from 'lucide-react';

import { SearchableSelect, type SearchableSelectOption } from '../../components/ui/searchable-select';
import { sucheLieferanten } from './bestellwesenApi';

interface Props {
    lieferantId: number | null;
    lieferantName: string;
    onWaehlen: (lieferant: { id: number; name: string }) => void;
    disabled?: boolean;
}

/** Auswahl des Lieferanten mit Suche (Name oder Ort); sucht beim Tippen auf dem Server, damit auch lange Listen funktionieren. */
export function LieferantWahl({ lieferantId, lieferantName, onWaehlen, disabled }: Props) {
    const namen = useRef(new Map<number, string>());

    const suche = useCallback(async (term: string): Promise<SearchableSelectOption[]> => {
        const treffer = await sucheLieferanten(term);
        treffer.forEach((l) => namen.current.set(l.id, l.name));
        return treffer.map((l) => ({ value: String(l.id), label: l.name, sublabel: l.ort ?? undefined }));
    }, []);

    if (disabled) {
        return <div className="px-3 py-2 font-medium text-slate-900">{lieferantName}</div>;
    }
    return (
        <SearchableSelect
            value={lieferantId === null ? '' : String(lieferantId)}
            selectedLabel={lieferantName}
            onChange={(wert) => {
                if (!wert) return;
                const id = Number(wert);
                onWaehlen({ id, name: namen.current.get(id) ?? '' });
            }}
            onSearch={suche}
            placeholder="Lieferant wählen"
            searchPlaceholder="Name oder Ort suchen"
            icon={<Truck className="w-4 h-4" />}
        />
    );
}
