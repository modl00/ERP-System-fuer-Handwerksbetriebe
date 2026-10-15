import {
    AppWindow,
    ArrowDownToLine,
    Cog,
    DoorClosed,
    LifeBuoy,
    Lock,
    OctagonAlert,
    Radio,
    Warehouse,
    Wind,
    type LucideIcon,
} from 'lucide-react';

/** Icons der Katalog-Module. Der Katalog speichert den Namen, hier steht die Zuordnung. */
export const MODUL_ICONS: Record<string, LucideIcon> = {
    DoorClosed,
    Wind,
    Warehouse,
    Lock,
    Radio,
    AppWindow,
    LifeBuoy,
    ArrowDownToLine,
    Cog,
    OctagonAlert,
};

/** Namen der wählbaren Symbole in der Reihenfolge der Auswahl im Modul-Editor. */
export const MODUL_ICON_NAMEN = Object.keys(MODUL_ICONS);

/** Deutsche Bezeichnung der Symbole für die Auswahl im Editor (die Namen oben sind Bibliotheksnamen). */
export const MODUL_ICON_BEZEICHNUNG: Record<string, string> = {
    DoorClosed: 'Tür',
    Wind: 'Rauchschutz',
    Warehouse: 'Tor',
    Lock: 'Schloss',
    Radio: 'Funk',
    AppWindow: 'Fenster',
    LifeBuoy: 'Rettung',
    ArrowDownToLine: 'Boden',
    Cog: 'Antrieb',
    OctagonAlert: 'Warnung',
};
