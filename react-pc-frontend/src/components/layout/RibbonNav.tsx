import { Fragment, useState, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { FirmenLogo } from './FirmenLogo';
import {
    BarChart3, Briefcase, Building2, Clock, Euro, FileCheck, FileJson,
    FileText, Gem, Globe, Home, Layers, List, Mail, MailPlus, Package, Settings, Settings2,
    ShoppingCart, Truck, GanttChartSquare, ChevronUp, ChevronDown, User, LogOut,
    Calendar, CalendarDays, Plane, Receipt, Wallet, Stethoscope,
    Phone, Voicemail, Cuboid, ClipboardCheck, Warehouse, ShieldCheck, History, Factory, Calculator, LayoutGrid, FlaskConical
} from 'lucide-react';
import { Inbox, Percent } from 'lucide-react';
import { Button } from '../ui/button';
import { NotificationBell } from './NotificationBell';
import { useAuth } from '../../auth/AuthContext';
import { useTelefonBerechtigung } from '../../features/telefon/useTelefonBerechtigung';
import { useLagerAktiv, useModulAktiv, useWartungAktiv } from '../../features/modulschalter/useFeatures';
import { useDarf } from '../../features/funktionsrechte/useFunktionsrechte';
import { useNeueSprachnachrichten } from '../../features/telefon/useNeueSprachnachrichten';
import { useMenue } from '../../features/menue/useMenue';
import { AlleFunktionenDialog } from './AlleFunktionenDialog';
import { Schnellzugriffsleiste } from './Schnellzugriffsleiste';
import { MeinePostfaecherDialog } from '../../features/postfaecher/MeinePostfaecherDialog';
import { useKiAktiv } from '../../features/ki/useKiAktiv';

// Navigation structure with subgroups for better organization
interface NavItem {
    name: string;
    href: string;
    icon: React.ComponentType<{ className?: string }>;
    /** Weitere Adressen, bei denen dieser Punkt als geöffnet gilt (zum Beispiel Reiter einer Seite mit eigener Adresse). */
    auch?: string[];
}

interface NavSubgroup {
    label: string;
    items: NavItem[];
}

interface NavCategory {
    category: string;
    subgroups: NavSubgroup[];
}

const NAVIGATION: NavCategory[] = [
    {
        category: 'Vorlagen & Stammdaten',
        subgroups: [
            {
                label: 'Dokumente',
                items: [
                    { name: 'Textvorlagen', href: '/textbausteine', icon: FileText },
                    { name: 'Stundensätze (Abrechnung)', href: '/arbeitszeitarten', icon: Clock },
                ]
            },
            {
                label: 'Kontakte',
                items: [
                    { name: 'Kunden', href: '/kunden', icon: User },
                    { name: 'Mitarbeiter', href: '/mitarbeiter', icon: User },
                    { name: 'Lieferanten', href: '/lieferanten', icon: Truck },
                ]
            },
            {
                label: 'Katalog',
                items: [
                    { name: 'Artikel', href: '/artikel', icon: Package },
                    { name: 'Leistungen', href: '/leistungen', icon: List },
                    { name: 'Lager', href: '/lager', icon: Warehouse },
                    { name: 'Arbeitsgänge', href: '/arbeitsgaenge', icon: Clock },
                    { name: 'Fremdleistungen', href: '/fremdleistungen', icon: Factory },
                    { name: 'Kostenrechnung', href: '/kostenrechnung', icon: Calculator },
                    { name: 'Kategorien', href: '/produktkategorien', icon: Layers },
                    { name: 'Werkstoffe', href: '/werkstoffe', icon: Cuboid },
                    { name: 'Aufschläge', href: '/aufschlaege', icon: Percent },
                ]
            },
            {
                label: 'Administration',
                items: [
                    { name: 'Wer darf was?', href: '/rechte-uebersicht', icon: ShieldCheck, auch: ['/abteilung-berechtigungen'] },
                    { name: 'Aktivitäten', href: '/aktivitaeten', icon: History },
                    { name: 'Firma', href: '/firma', icon: Building2 },
                    { name: 'Formularwesen', href: '/formulare', icon: FileJson },
                    { name: 'Einstellungen', href: '/einstellungen', icon: Settings },
                    { name: 'Test-Box', href: '/testbox', icon: FlaskConical },
                ]
            }
        ]
    },
    {
        category: 'Projektmanagement',
        subgroups: [
            {
                label: 'Aufträge',
                items: [
                    { name: 'Startbildschirm', href: '/start', icon: Home },
                    { name: 'Projekte', href: '/projekte', icon: Briefcase },
                    { name: 'Anfragen', href: '/anfragen', icon: FileCheck },
                ]
            },
            {
                label: 'Wartung',
                items: [
                    { name: 'Wartungen', href: '/wartung', icon: ClipboardCheck },
                ]
            },
            {
                label: 'Dokumente',
                items: [
                    { name: 'Dokumente', href: '/dokumentuebersicht', icon: FileText },
                ]
            },
            {
                label: 'Planung',
                items: [
                    { name: 'Kalender', href: '/kalender', icon: Calendar },
                    { name: 'Plantafel', href: '/plantafel', icon: GanttChartSquare },
                ]
            },
            {
                label: 'Einkauf',
                items: [
                    { name: 'Bestellen', href: '/bestellungen/liste', icon: ShoppingCart },
                    { name: 'Bestellungen', href: '/bestellungen', icon: FileText },
                    { name: 'Bedarf', href: '/bestellungen/bedarf', icon: List },
                ]
            }
        ]
    },
    {
        category: 'Zeiterfassung',
        subgroups: [
            {
                label: 'Übersicht',
                items: [
                    { name: 'Kalender', href: '/zeitbuchungen', icon: Calendar },
                    { name: 'Monatsabschluss', href: '/monatsabschluss', icon: FileCheck },
                ]
            },
            {
                label: 'Berichte',
                items: [
                    { name: 'Auswertung', href: '/auswertung', icon: BarChart3 },
                    { name: 'Steuerberater', href: '/steuerberater', icon: Mail },
                ]
            },
            {
                label: 'Einstellungen',
                items: [
                    { name: 'Zeitkonten', href: '/zeitkonten', icon: Clock },
                    { name: 'Feiertage', href: '/feiertage', icon: CalendarDays },
                ]
            },
            {
                label: 'Abwesenheiten',
                items: [
                    { name: 'Urlaubsanträge', href: '/urlaubsantraege', icon: Plane },
                    { name: 'Lange Krankheit', href: '/langzeitkrankmeldungen', icon: Stethoscope },
                ]
            }
        ]
    },
    {
        category: 'Kommunikation',
        subgroups: [
            {
                label: 'E-Mail',
                items: [
                    { name: 'E-Mail Center', href: '/emails/inbox', icon: Mail },
                    { name: 'E-Mail Vorlagen', href: '/email-textvorlagen', icon: MailPlus },
                    { name: 'E-Mail-Einstellungen', href: '/email-einstellungen', icon: Settings2 },
                ]
            },
            {
                label: 'Telefon',
                items: [
                    { name: 'Anrufe', href: '/telefon/anrufe', icon: Phone },
                    { name: 'Anrufbeantworter', href: '/telefon/anrufbeantworter', icon: Voicemail },
                ]
            },
            {
                label: 'Website',
                items: [
                    { name: 'Neuigkeiten', href: '/website', icon: Globe },
                ]
            }
        ]
    },
    {
        category: 'Finanzen & Controlling',
        subgroups: [
            {
                label: 'Buchhaltung',
                items: [
                    { name: 'Offene Posten', href: '/offeneposten', icon: Euro },
                    { name: 'Rechnungen', href: '/rechnungsuebersicht', icon: FileText },
                    { name: 'Belege & Kasse', href: '/belege-kasse', icon: Receipt },
                    { name: 'Mietabrechnung', href: '/miete', icon: Home },
                ]
            },
            {
                label: 'Auswertung',
                items: [
                    { name: 'Erfolgsanalyse', href: '/analyse', icon: BarChart3 },
                    { name: 'Kostenstellen', href: '/kostenstellen', icon: Wallet },
                ]
            }
        ]
    }
];

const ADMIN_ONLY_PATHS = new Set(['/rechte-uebersicht', '/aktivitaeten', '/abteilung-berechtigungen', '/firma', '/formulare', '/einstellungen', '/benutzer', '/website', '/kostenrechnung', '/testbox']);
/** Nur mit dem Abteilungs-Recht „Anrufe & Anrufbeantworter" sichtbar. */
const TELEFON_PATHS = new Set(['/telefon/anrufe', '/telefon/anrufbeantworter']);
/** Nur sichtbar, solange das Wartungsmodul in den Einstellungen eingeschaltet ist. */
const WARTUNG_PATHS = new Set(['/wartung']);
/** Nur sichtbar, solange das Lagermodul eingeschaltet ist (ohne Angabe: eingeschaltet). */
const LAGER_PATHS = new Set(['/lager']);
/** Nur mit dem Recht „Ansehen“ an der Funktion Bestellungen (Rechteverwaltung). */
const BESTELLEN_PATHS = new Set(['/bestellungen/liste']);
/** Nur sichtbar, solange das jeweilige Modul eingeschaltet ist (ohne Angabe: eingeschaltet). */
const MIETE_PATHS = new Set(['/miete']);
const WEBSITE_PATHS = new Set(['/website']);


export function RibbonNavigation() {
    const location = useLocation();
    const navigate = useNavigate();
    const { user, isAdmin, logout } = useAuth();
    const kiAktiv = useKiAktiv();
    const darfTelefon = useTelefonBerechtigung() === true;
    // Ohne das Recht "Sehen" (Rechteverwaltung) erscheint der Menüpunkt nicht; solange unbekannt, bleibt er sichtbar
    const darfWartungSehen = useDarf('WARTUNG') !== false;
    const darfLagerSehen = useDarf('LAGER') !== false;
    const darfBestellenSehen = useDarf('BESTELLUNGEN') !== false;
    const wartungAktiv = useWartungAktiv() === true && darfWartungSehen;
    const lagerAktiv = useLagerAktiv() !== false && darfLagerSehen;
    const mieteAktiv = useModulAktiv('miete') !== false;
    const websiteAktiv = useModulAktiv('website') !== false;
    // Zähler neuer Nachrichten am Menüpunkt "Anrufbeantworter".
    const neueSprachnachrichten = useNeueSprachnachrichten(darfTelefon);
    const zaehler: Record<string, number> = { '/telefon/anrufbeantworter': neueSprachnachrichten };

    const erlaubteNavigation = useMemo<NavCategory[]>(() => {
        return NAVIGATION
            .map((category) => ({
                ...category,
                subgroups: category.subgroups
                    .map((subgroup) => ({
                        ...subgroup,
                        items: subgroup.items.filter((item) =>
                            (isAdmin || !ADMIN_ONLY_PATHS.has(item.href))
                            && (darfTelefon || !TELEFON_PATHS.has(item.href))
                            && (wartungAktiv || !WARTUNG_PATHS.has(item.href))
                            && (lagerAktiv || !LAGER_PATHS.has(item.href))
                            && (darfBestellenSehen || !BESTELLEN_PATHS.has(item.href))
                            && (mieteAktiv || !MIETE_PATHS.has(item.href))
                            && (websiteAktiv || !WEBSITE_PATHS.has(item.href))),
                    }))
                    .filter((subgroup) => subgroup.items.length > 0),
            }))
            .filter((category) => category.subgroups.length > 0);
    }, [isAdmin, darfTelefon, wartungAktiv, lagerAktiv, darfBestellenSehen, mieteAktiv, websiteAktiv]);

    // Hauptmenü: erlaubte Punkte ohne die ausgeblendeten. Die Seite, auf der man gerade ist, bleibt immer stehen,
    // damit man sieht, wo man ist. Solange der Stand unbekannt ist, wird nichts ausgeblendet.
    const { bekannt: menueBekannt, ausgeblendet } = useMenue();
    const anzahlAusgeblendet = useMemo(
        () => erlaubteNavigation.reduce((summe, k) => summe + k.subgroups.reduce((s2, sg) => s2 + sg.items.filter((i) => ausgeblendet.has(i.href)).length, 0), 0),
        [erlaubteNavigation, ausgeblendet],
    );
    const visibleNavigation = useMemo<NavCategory[]>(() => {
        if (!menueBekannt) return erlaubteNavigation;
        const istAktuell = (item: NavItem) => [item.href, ...(item.auch ?? [])].some((h) => location.pathname === h || location.pathname.startsWith(h + '/'));
        return erlaubteNavigation
            .map((category) => ({
                ...category,
                subgroups: category.subgroups
                    .map((subgroup) => ({ ...subgroup, items: subgroup.items.filter((item) => !ausgeblendet.has(item.href) || istAktuell(item)) }))
                    .filter((subgroup) => subgroup.items.length > 0),
            }))
            .filter((category) => category.subgroups.length > 0);
    }, [erlaubteNavigation, menueBekannt, ausgeblendet, location.pathname]);
    const [alleOffen, setAlleOffen] = useState(false);
    const [postfaecherOffen, setPostfaecherOffen] = useState(false);
    // Aufklappliste an einem Hauptpunkt (zum Beispiel "Stammdaten"): alle Module dieser Kategorie auf einen Blick
    const [kategorieOffen, setKategorieOffen] = useState<{ kategorie: string; links: number; oben: number } | null>(null);

    const oeffneKategorie = (kategorie: string, knopf: HTMLElement) => {
        const r = knopf.getBoundingClientRect();
        setKategorieOffen(alt => (alt?.kategorie === kategorie ? null : { kategorie, links: Math.max(8, Math.min(r.left - 120, window.innerWidth - 296)), oben: r.bottom + 4 }));
    };

    const [isExpanded, setIsExpanded] = useState(true);

    const routeCategory = useMemo(() => {
        return visibleNavigation.find(group =>
            group.subgroups.some(sg => sg.items.some(item => [item.href, ...(item.auch ?? [])].some(h => location.pathname.startsWith(h))))
        );
    }, [location.pathname, visibleNavigation]);
    // Hervorgehoben wird der Hauptpunkt, zu dem die aktuelle Seite gehört
    const activeCategory = routeCategory?.category ?? '';

    // User Menu State
    const currentUser = user;
    const [showUserMenu, setShowUserMenu] = useState(false);

    const handleLogout = async () => {
        setShowUserMenu(false);
        await logout();
        navigate('/login', { replace: true });
    };


    const toggleRibbon = () => setIsExpanded(!isExpanded);

    return (
        <div className="flex flex-col bg-white border-b border-slate-200 shadow-sm sticky top-0 z-40 transition-all">
            {/* Top Bar: Logo & Tabs */}
            {/* gap-4 statt gap-8 (vorher): gibt der Kategorie-Leiste bei 1440px
                die paar Pixel, die ihr sonst durch die Aussenabstaende der
                Nachbar-Elemente fehlen (siehe Spec C, Befund 3). */}
            <div className="flex items-center px-4 h-16 border-b border-rose-100 bg-white shadow-sm gap-2 min-[1680px]:gap-4">
                {/* Firmenlogo (#209): das der Firma, sonst das mitgelieferte Programmlogo */}
                <div className="flex items-center shrink-0">
                    <FirmenLogo />
                </div>

                {/* Startbildschirm: von jeder Seite aus mit einem Klick erreichbar */}
                <Link
                    to="/start"
                    aria-label="Startbildschirm"
                    title="Startbildschirm"
                    className={cn(
                        'flex shrink-0 items-center justify-center rounded-full p-2 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400',
                        location.pathname === '/' || location.pathname === '/start' ? 'bg-rose-100 text-rose-600' : 'bg-slate-100 text-slate-500 hover:bg-rose-50 hover:text-rose-600',
                    )}
                >
                    <Home className="h-5 w-5" aria-hidden="true" />
                </Link>

                {/* Category Tabs */}
                {/* "no-scrollbar" entfernt (vorher): eine still scrollende
                    Kategorie-Leiste ist keine Loesung -- wenn hier noch etwas
                    ueberlaeuft, soll es als Scrollbalken sichtbar sein statt
                    unsichtbar abgeschnitten (Spec C, Befund 3). */}
                <div className="flex-1 flex overflow-x-auto overflow-y-hidden gap-0.5 min-[1680px]:gap-2 h-full items-end">
                    {visibleNavigation.map((group) => (
                        <Fragment key={group.category}>
                        <button
                            aria-haspopup="menu"
                            aria-expanded={kategorieOffen?.kategorie === group.category}
                            onClick={(e) => oeffneKategorie(group.category, e.currentTarget)}
                            className={cn(
                                // px-1.5 bei 1440px, px-2.5 ab 1480px, px-4 ab 1680px:
                                // spart genug Breite, damit alle fünf Kategorien auch bei breiteren Linux-Systemfonts
                                // auf CI-Runnern und Bildschirmen zwischen 1440px und 1600px ohne Überlauf nebeneinander passen.
                                "px-1.5 min-[1480px]:px-2.5 min-[1680px]:px-4 py-3 text-sm font-semibold whitespace-nowrap transition-all rounded-t-lg relative bottom-[-1px]",
                                activeCategory === group.category
                                    ? "text-rose-700 bg-rose-50 border-t-2 border-x border-rose-200 border-b-transparent shadow-sm z-10"
                                    : "text-slate-500 hover:text-slate-800 hover:bg-slate-50 border-transparent border-b-2 border-b-transparent mb-[1px]"
                            )}
                        >
                            {group.category}
                        </button>
                        <button
                            type="button"
                            aria-haspopup="menu"
                            aria-expanded={kategorieOffen?.kategorie === group.category}
                            aria-label={`Module von ${group.category}`}
                            title={`Alle Module von ${group.category} anzeigen`}
                            onClick={(e) => oeffneKategorie(group.category, e.currentTarget)}
                            className="self-center -ml-1 rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300"
                        >
                            <ChevronDown className={cn('w-3.5 h-3.5 transition-transform', kategorieOffen?.kategorie === group.category && 'rotate-180')} aria-hidden="true" />
                        </button>
                        </Fragment>
                    ))}
                </div>

                {/* KI-Hilfe Button – ohne KI-Schlüssel gar nicht da (Erklärung steht in Einstellungen → KI-Funktionen) */}
                {kiAktiv && (
                    <button
                        onClick={() => window.dispatchEvent(new CustomEvent('ki-hilfe-open'))}
                        className="flex items-center gap-1.5 px-2.5 py-2 rounded-lg text-rose-600 hover:bg-rose-50 transition-colors"
                        title="KI-Hilfe öffnen"
                    >
                        <Gem className="w-4 h-4" />
                        <span className="text-sm font-medium hidden lg:inline">KI-Hilfe</span>
                    </button>
                )}

                {/* Notification Bell */}
                <div className="flex items-center">
                    <NotificationBell />
                </div>

                {/* User Selector */}
                <div className="relative pl-3 border-l border-slate-200">
                    <button
                        onClick={() => setShowUserMenu(!showUserMenu)}
                        className="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors group"
                    >
                        <div className="w-8 h-8 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 border border-rose-200">
                            <User className="w-4 h-4" />
                        </div>
                        <div className="text-left hidden md:block">
                            {/* Kuerzung hier ist erlaubt, weil der volle Name im
                                aufgeklappten Nutzermenue direkt darunter steht (siehe
                                "showUserMenu"-Panel weiter unten) und zusaetzlich im
                                title-Attribut steht. data-kuerzung-erlaubt markiert das
                                als gewollte Ausnahme fuer keinTextGekuerzt (siehe
                                e2e/hilfen/design.ts) -- ohne das Attribut waere ein
                                abgeschnittener Name ein Fehler.

                                Task 10b (Abschnitt 7, Design-Review Abschnitt 6 Befund c):
                                "2xl:max-w-none" (ab 1536px unbegrenzt breit) griff zu frueh --
                                bei 1536px ist noch nicht genug Platz frei, ein rund 55 Zeichen
                                langer Name sprengte die Kategorie-Leiste dort um 119px (bei
                                1650px noch um 5px), weil dieser Block und die Kategorie-Leiste
                                sich dieselbe Zeile teilen und ein breiterer Name der
                                Kategorie-Leiste Platz wegnimmt. Zwei Stellschrauben standen zur
                                Wahl: die Grenze spaeter greifen lassen (z.B. "min-[1780px]:
                                max-w-none") oder eine feste Obergrenze statt "none" setzen (z.B.
                                "2xl:max-w-[18rem]"). Gewaehlt: die Grenze verschieben.
                                Nachgerechnet mit den Design-Review-Messwerten (119px Ueberstand
                                bei 369px Namensbreite, 247px bei 497px Namensbreite -- linear,
                                Differenz und Ursache identisch): der Ueberstand waechst 1:1 mit
                                der Namensbreite, die Kategorie-Leiste vertraegt bei 1536px nur
                                rund 250px Namensbreite verlustfrei. Eine feste Obergrenze muesste
                                also klein genug sein (< 15rem), und genau diese Grenze gilt schon
                                heute unveraendert bei 1440px (max-w-[10rem] = 160px) und
                                verursacht dort nachweislich 0px Ueberstand, auch bei sehr langen
                                Namen. Die Grenze auf 1780px zu verschieben nutzt also einfach die
                                bereits bewaehrte 160px-Kuerzung eine Stufe weiter, statt eine neue
                                Zahl zu erfinden -- deshalb kein "2xl:max-w-[18rem]": 18rem (288px)
                                liegt ueber der 250px-Schwelle und haette die Luecke nicht
                                geschlossen, wie die Nachrechnung zeigt. Ab 1780px ist der
                                Platzgewinn gegenueber 1536px so gross, dass "max-w-none" laut
                                denselben Messwerten wieder 0px Ueberstand ergibt (1780px: 0px bzw.
                                3px je nach Namenslaenge). */}
                            <p
                                className="text-sm font-semibold text-slate-700 group-hover:text-slate-900 max-w-[10rem] min-[1780px]:max-w-none truncate"
                                title={currentUser ? currentUser.displayName : undefined}
                                data-kuerzung-erlaubt
                            >
                                {currentUser ? currentUser.displayName : "Lade..."}
                            </p>
                            <p className="text-xs text-slate-500">{isAdmin ? 'Administrator' : 'Angemeldet'}</p>
                        </div>
                        <ChevronDown className={cn("w-4 h-4 text-slate-400 transition-transform duration-200", showUserMenu ? "rotate-180" : "")} />
                    </button>

                    {showUserMenu && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setShowUserMenu(false)} />
                            <div className="absolute right-0 top-full mt-2 w-56 bg-white rounded-xl shadow-lg border border-slate-100 py-2 z-50 animate-in fade-in zoom-in-95 duration-200">
                                <div className="px-4 py-2 border-b border-slate-50 mb-2">
                                    {/* Hier steht der VOLLE Name -- das ist die Begruendung
                                        dafuer, dass der kompakte Anzeigename oben gekuerzt
                                        werden darf. Deshalb kein truncate/line-clamp: lieber
                                        umbrechen als kuerzen. */}
                                    <p className="text-sm font-semibold text-slate-700 break-words">{currentUser?.displayName || 'Benutzer'}</p>
                                    <p className="text-xs text-slate-500">{currentUser?.username || 'Kein Username'}</p>
                                </div>
                                <div className="border-t border-slate-100 mt-2 pt-2 pb-1">
                                    <Link
                                        to="/mein-tagesverlauf"
                                        onClick={() => setShowUserMenu(false)}
                                        className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                                    >
                                        <History className="w-4 h-4" />
                                        Mein Tagesverlauf
                                    </Link>
                                    <button
                                        onClick={() => { setShowUserMenu(false); setAlleOffen(true); }}
                                        className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                                    >
                                        <LayoutGrid className="w-4 h-4" />
                                        Alle Funktionen &amp; Menü anpassen
                                    </button>
                                    <button
                                        onClick={() => { setShowUserMenu(false); setPostfaecherOffen(true); }}
                                        className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                                    >
                                        <Inbox className="w-4 h-4" />
                                        Meine Postfächer
                                    </button>
                                    {isAdmin && (
                                        <Link
                                            to="/benutzer"
                                            onClick={() => setShowUserMenu(false)}
                                            className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                                        >
                                            <User className="w-4 h-4" />
                                            Benutzer verwalten
                                        </Link>
                                    )}
                                    <button
                                        onClick={handleLogout}
                                        className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        Abmelden
                                    </button>
                                </div>
                            </div>
                        </>
                    )}
                </div>

                {/* Toggle Button */}
                {/* Nur ein Symbol, deshalb braucht der Knopf einen Namen fuer
                    Screenreader -- sonst liest er sich als "Schaltflaeche". */}
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={toggleRibbon}
                    aria-label={isExpanded ? 'Schnellzugriffsleiste einklappen' : 'Schnellzugriffsleiste ausklappen'}
                    aria-expanded={isExpanded}
                    className="ml-2 text-slate-400"
                >
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </Button>
            </div>

            {kategorieOffen && createPortal(
                <>
                    <div className="fixed inset-0 z-[60]" onClick={() => setKategorieOffen(null)} aria-hidden="true" />
                    <div
                        role="menu"
                        aria-label={`Module von ${kategorieOffen.kategorie}`}
                        onKeyDown={(e) => { if (e.key === 'Escape') setKategorieOffen(null); }}
                        style={{ top: kategorieOffen.oben, left: kategorieOffen.links }}
                        className="fixed z-[61] w-72 max-h-[70vh] overflow-y-auto rounded-xl border border-slate-100 bg-white py-1.5 shadow-lg"
                    >
                        {(erlaubteNavigation.find(k => k.category === kategorieOffen.kategorie)?.subgroups ?? []).map(untergruppe => (
                            <div key={untergruppe.label} role="group" aria-label={untergruppe.label}>
                                <p className="px-3 pb-0.5 pt-2 text-[11px] font-semibold uppercase tracking-wide text-slate-400">{untergruppe.label}</p>
                                {untergruppe.items.map(item => (
                                    <Link
                                        key={item.href}
                                        to={item.href}
                                        role="menuitem"
                                        onClick={() => setKategorieOffen(null)}
                                        className="flex items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 hover:text-rose-700 focus:bg-slate-50 focus:outline-none"
                                    >
                                        <item.icon className="w-4 h-4 shrink-0 text-slate-400" aria-hidden="true" />
                                        <span className="truncate">{item.name}</span>
                                        {(zaehler[item.href] ?? 0) > 0 && (
                                            <span className="ml-auto shrink-0 rounded-full bg-rose-600 px-1.5 text-[10px] font-bold text-white" aria-label={`${zaehler[item.href]} neu`}>{zaehler[item.href] > 99 ? '99+' : zaehler[item.href]}</span>
                                        )}
                                        {ausgeblendet.has(item.href) && <span className="ml-auto shrink-0 text-[10px] text-slate-400">ausgeblendet</span>}
                                    </Link>
                                ))}
                            </div>
                        ))}
                    </div>
                </>,
                document.body,
            )}
            {isExpanded && (
                <Schnellzugriffsleiste navigation={erlaubteNavigation} istAdmin={isAdmin} zaehler={zaehler} anzahlAusgeblendet={anzahlAusgeblendet} onAlleFunktionen={() => setAlleOffen(true)} />
            )}
            <MeinePostfaecherDialog offen={postfaecherOffen} onSchliessen={() => setPostfaecherOffen(false)} />
            {alleOffen && (
                <AlleFunktionenDialog offen={alleOffen} onSchliessen={() => setAlleOffen(false)} navigation={erlaubteNavigation} istAdmin={isAdmin} />
            )}
        </div>
    );
}
