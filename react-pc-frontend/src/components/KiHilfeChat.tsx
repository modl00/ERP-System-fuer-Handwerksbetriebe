import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router-dom';
import { Gem, X, Send, Trash2, Minimize2, ExternalLink, ShieldCheck, Cloud, Paperclip, FileText, Loader2 } from 'lucide-react';
import { KiBelegKarte } from './KiBelegKarte';
import { KiAssistentVorschlag, type AssistentVorschlag, type VorschlagStatus } from './KiAssistentVorschlag';
import { cn } from '../lib/utils';
import { begrenze, groesseLinks, groesseRechts, leseRahmen, standardRahmen, verschiebe, type Rahmen } from './kiChatRahmen';
import { holeBrowserFehler } from '../lib/browserFehler';

interface SourceLink {
    title: string;
    url: string;
}

/** Ein Dokument aus dem Chat: die Datei selbst (für „Als Beleg ablegen") und der gelesene Text (für Aufgaben an die KI). */
/** Ein Dokument oder eine E-Mail aus dem E-Mail-Center ({@code emailId}) im Chat; bei einer E-Mail gibt es keine Datei. */
interface Anhang { file: File | null; name: string; text: string | null; hinweis: string | null; emailId?: number }

interface AssistentQuelle {
    anbieter: string;
    modell: string;
    lokal: boolean;
}

interface Message {
    role: 'user' | 'assistant';
    text: string;
    sources?: SourceLink[];
    /** Nur im Assistenten-Modus: wer geantwortet hat und was vorgeschlagen wurde. */
    quelle?: AssistentQuelle;
    vorschlaege?: (AssistentVorschlag & { status: VorschlagStatus })[];
}

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

function getCurrentUserId(): number | null {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') return parsed.id;
    } catch { /* ignore */ }
    return null;
}

function lsKey(base: string, userId: number | null): string {
    return userId != null ? `${base}-${userId}` : base;
}

function readLS<T>(key: string, fallback: T): T {
    try {
        const raw = localStorage.getItem(key);
        return raw ? JSON.parse(raw) : fallback;
    } catch { return fallback; }
}

export function KiHilfeChat() {
    const location = useLocation();
    const navigate = useNavigate();
    const [userId, setUserId] = useState<number | null>(() => getCurrentUserId());

    // Derive user-scoped localStorage keys
    const keyMessages = lsKey('ki-hilfe-messages', userId);
    const keyOpen = lsKey('ki-hilfe-open', userId);
    const keyMinimized = lsKey('ki-hilfe-minimized', userId);
    const keyRahmen = lsKey('ki-hilfe-rahmen', userId);

    const [open, setOpen] = useState(() => readLS(keyOpen, false));
    const [minimized, setMinimized] = useState(() => readLS(keyMinimized, false));
    const [messages, setMessages] = useState<Message[]>(() => readLS(keyMessages, []));
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    // Ein Fenster für alles: Die KI beantwortet Fragen zum Programm (Werkzeug „programm_hilfe") und zu den Daten des Betriebs und macht Vorschläge, die man bestätigt.
    // Ist für die KI noch kein Anbieter eingerichtet, antwortet wie bisher die reine Programmhilfe. Unbekannter Stand zählt als bereit: Beim Fragen kommt die genaue Meldung.
    // Dokument aus dem Chat (hineingezogen oder per Büroklammer): gelesener Text für Aufgaben, die Datei selbst für „Als Beleg ablegen"
    const [anhang, setAnhang] = useState<Anhang | null>(null);
    const [liestDokument, setLiestDokument] = useState(false);
    const [ziehen, setZiehen] = useState(false);
    const [belegDatei, setBelegDatei] = useState<File | null>(null);
    const dateiFeld = useRef<HTMLInputElement>(null);
    const [assistentBereit, setAssistentBereit] = useState<boolean | null>(null);
    const assistent = assistentBereit !== false;
    const [assistentMeldung, setAssistentMeldung] = useState<string | null>(null);
    const [geoLocation, setGeoLocation] = useState<{ latitude: number; longitude: number } | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);
    const laufendeAnfrageRef = useRef<AbortController | null>(null);
    const offeneFrageRef = useRef<string | null>(null);

    // Detect user profile changes (login/switch)
    useEffect(() => {
        const checkUser = () => {
            const newId = getCurrentUserId();
            if (newId !== userId) setUserId(newId);
        };
        // Check on storage events (other tabs) and periodically (same tab)
        const onStorage = (e: StorageEvent) => {
            if (e.key === FRONTEND_USER_STORAGE_KEY) checkUser();
        };
        window.addEventListener('storage', onStorage);
        const interval = setInterval(checkUser, 2000);
        return () => {
            window.removeEventListener('storage', onStorage);
            clearInterval(interval);
        };
    }, [userId]);

    // Anderer Benutzer: Chat-Zustand beim Rendern aus dessen Speicher übernehmen.
    const [chatFuerBenutzer, setChatFuerBenutzer] = useState(userId);
    if (chatFuerBenutzer !== userId) {
        setChatFuerBenutzer(userId);
        setMessages(readLS(keyMessages, []));
        setOpen(readLS(keyOpen, false));
        setMinimized(readLS(keyMinimized, false));
        setError(null);
        setInput('');
        setLoading(false);
    }

    // Beim Benutzerwechsel und beim Verlassen der Seite keine Antwort mehr abwarten:
    useEffect(() => () => {
        laufendeAnfrageRef.current?.abort();
        laufendeAnfrageRef.current = null;
        offeneFrageRef.current = null;
    }, [userId]);

    // Lage und Größe des Fensters (Issue #251): frei verschiebbar und in der Größe änderbar, wird gemerkt
    const [rahmen, setRahmen] = useState<Rahmen>(() => begrenze(leseRahmen(readLS<unknown>(keyRahmen, null)) ?? standardRahmen(window.innerWidth, window.innerHeight), window.innerWidth, window.innerHeight));
    const rahmenRef = useRef(rahmen);
    useEffect(() => { rahmenRef.current = rahmen; }, [rahmen]);
    useEffect(() => {
        const anpassen = () => setRahmen(r => begrenze(r, window.innerWidth, window.innerHeight));
        window.addEventListener('resize', anpassen);
        return () => window.removeEventListener('resize', anpassen);
    }, []);

    const startZiehen = (art: 'verschieben' | 'rechts' | 'links') => (e: React.PointerEvent<HTMLElement>) => {
        if (e.button !== 0 || (art === 'verschieben' && (e.target as HTMLElement).closest('button'))) return;
        e.preventDefault();
        const start = rahmenRef.current;
        const x0 = e.clientX;
        const y0 = e.clientY;
        const bewegen = (ev: PointerEvent) => {
            const dx = ev.clientX - x0;
            const dy = ev.clientY - y0;
            const vw = window.innerWidth;
            const vh = window.innerHeight;
            setRahmen(art === 'verschieben' ? verschiebe(start, dx, dy, vw, vh) : art === 'rechts' ? groesseRechts(start, dx, dy, vw, vh) : groesseLinks(start, dx, dy, vw, vh));
        };
        const ende = () => {
            window.removeEventListener('pointermove', bewegen);
            window.removeEventListener('pointerup', ende);
            window.removeEventListener('pointercancel', ende);
            try { localStorage.setItem(keyRahmen, JSON.stringify(rahmenRef.current)); } catch { /* egal */ }
        };
        window.addEventListener('pointermove', bewegen);
        window.addEventListener('pointerup', ende);
        window.addEventListener('pointercancel', ende);
    };

    const rahmenZuruecksetzen = () => {
        const neu = standardRahmen(window.innerWidth, window.innerHeight);
        setRahmen(neu);
        try { localStorage.removeItem(keyRahmen); } catch { /* egal */ }
    };

    // Persist state to localStorage on change
    useEffect(() => { localStorage.setItem(keyMessages, JSON.stringify(messages)); }, [messages, keyMessages]);

    useEffect(() => {
        if (!open) return;
        let abgebrochen = false;
        fetch('/api/ki-assistent/status')
            .then(r => (r.ok ? r.json() : null))
            .then((s: { bereit?: boolean; meldung?: string | null } | null) => {
                if (abgebrochen) return;
                setAssistentBereit(s && s.bereit === false ? false : true);
                setAssistentMeldung(s && s.bereit === false ? (s.meldung ?? null) : null);
            })
            .catch(() => { /* Stand unbekannt: beim Fragen kommt die genaue Meldung */ });
        return () => { abgebrochen = true; };
    }, [open]);
    useEffect(() => { localStorage.setItem(keyOpen, JSON.stringify(open)); }, [open, keyOpen]);
    useEffect(() => { localStorage.setItem(keyMinimized, JSON.stringify(minimized)); }, [minimized, keyMinimized]);

    // Sync state across tabs via storage event
    useEffect(() => {
        const onStorage = (e: StorageEvent) => {
            if (!e.newValue) return;
            try {
                if (e.key === keyMessages) setMessages(JSON.parse(e.newValue));
                if (e.key === keyOpen) setOpen(JSON.parse(e.newValue));
                if (e.key === keyMinimized) setMinimized(JSON.parse(e.newValue));
            } catch { /* ignore parse errors */ }
        };
        window.addEventListener('storage', onStorage);
        return () => window.removeEventListener('storage', onStorage);
    }, [keyMessages, keyOpen, keyMinimized]);

    // Request geolocation once (silently fail if denied)
    useEffect(() => {
        if ('geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
                (pos) => setGeoLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
                () => { /* user denied or unavailable */ }
            );
        }
    }, []);

    const scrollToBottom = useCallback(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, []);

    useEffect(() => {
        scrollToBottom();
    }, [messages, scrollToBottom]);

    useEffect(() => {
        if (open && !minimized) {
            inputRef.current?.focus();
        }
    }, [open, minimized]);

    /** Collect current page context for RAG-enhanced responses. */
    const gatherPageContext = useCallback(() => {
        const main = document.querySelector('main');

        // Page title from the first h1 or the document title
        const h1 = main?.querySelector('h1');
        const pageTitle = h1?.textContent?.trim() || document.title;

        // Collect visible error messages on screen
        const errorEls = document.querySelectorAll(
            '[role="alert"], .bg-red-50, .bg-yellow-50'
        );
        const errors = Array.from(errorEls)
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 5)
            .join('; ');

        // Collect the project-level status badge (directly next to h1)
        const projectBadge = h1?.parentElement?.querySelector('.rounded-full')?.textContent?.trim();

        // Collect Bento stats (label → value pairs in the stats row)
        const bentoStats: string[] = [];
        const statBlocks = main?.querySelectorAll('.text-\\[11px\\].text-slate-400.uppercase');
        statBlocks?.forEach(label => {
            const parent = label.parentElement;
            const valueEl = parent?.querySelector('.font-semibold');
            if (valueEl) {
                const l = label.textContent?.trim();
                const v = valueEl.textContent?.trim();
                if (l && v) bentoStats.push(`${l}: ${v}`);
            }
        });

        // Collect info/rule messages (blue info cards)
        const infoMessages = Array.from(document.querySelectorAll('.bg-blue-50'))
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 3);

        // Collect disabled buttons and their reasons
        const disabledButtons = Array.from(main?.querySelectorAll('button[disabled]') ?? [])
            .map(btn => {
                const label = btn.textContent?.trim();
                const reason = btn.getAttribute('title') || '';
                return reason ? `"${label}" (${reason})` : `"${label}" (deaktiviert)`;
            })
            .filter(Boolean)
            .slice(0, 5);

        // Collect Geschäftsdokumente (document cards) with structured data
        // Each card has: type badge + status badge + document number + description
        const dokumentCards: string[] = [];
        const cards = main?.querySelectorAll('.bg-white.rounded-xl.border.p-4');
        cards?.forEach(card => {
            const badgeRow = card.querySelector('.flex.items-center.gap-2.mb-1');
            if (!badgeRow) return;

            const badges = Array.from(badgeRow.querySelectorAll('.rounded-full'))
                .map(b => b.textContent?.trim())
                .filter(Boolean);

            const docNumber = card.querySelector('.font-semibold.text-slate-900')?.textContent?.trim();
            const betreff = card.querySelector('.text-sm.text-slate-600')?.textContent?.trim();
            const betrag = card.querySelector('.text-lg.font-bold')?.textContent?.trim();
            const origin = card.querySelector('.text-\\[10px\\].text-slate-400')?.textContent?.trim();

            if (docNumber || badges.length) {
                let line = badges.join(' | ');
                if (docNumber) line += ` Nr. ${docNumber}`;
                if (betreff) line += ` "${betreff}"`;
                if (betrag) line += ` ${betrag}`;
                if (origin) line += ` (${origin})`;
                dokumentCards.push(line);
            }
        });

        // Collect sidebar metadata (Kunde, Kundennummer, Ansprechpartner, etc.)
        const sidebarData: string[] = [];
        const sidebarLabels = document.querySelectorAll('aside .text-slate-500, aside .text-xs.text-slate-400');
        sidebarLabels?.forEach(label => {
            const parent = label.parentElement;
            const valueEl = parent?.querySelector('.font-medium, .font-semibold, .text-slate-700');
            if (valueEl && valueEl !== label) {
                const l = label.textContent?.trim()?.replace(/:$/, '');
                const v = valueEl.textContent?.trim();
                if (l && v && v !== '–') sidebarData.push(`${l}: ${v}`);
            }
        });

        // Fallback: collect general form data (text-slate-500 label → font-medium value)
        const formData: string[] = [];
        if (!sidebarData.length && !dokumentCards.length) {
            const labelEls = main?.querySelectorAll('.text-slate-500');
            labelEls?.forEach(label => {
                const parent = label.parentElement;
                const valueEl = parent?.querySelector('.font-medium, .font-semibold');
                if (valueEl) {
                    const l = label.textContent?.trim()?.replace(/:$/, '');
                    const v = valueEl.textContent?.trim();
                    if (l && v && v !== '–') {
                        formData.push(`${l}: ${v}`);
                    }
                }
            });
        }

        // Collect section headings for structural context
        const headings = Array.from(main?.querySelectorAll('h1, h2, h3, h4') ?? [])
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 8)
            .join(', ');

        // Collect active tab name
        const activeTab = main?.querySelector('[data-state="active"], [aria-selected="true"], .border-rose-500, .text-rose-600.border-b-2, .border-b-2.border-rose-500')?.textContent?.trim();

        // Build a structured visible content string
        const parts: string[] = [];
        if (headings) parts.push(`Ueberschriften: ${headings}`);
        if (projectBadge) parts.push(`Projekt-Status: ${projectBadge}`);
        if (bentoStats.length) parts.push(`Kennzahlen: ${bentoStats.join(' | ')}`);
        if (activeTab) parts.push(`Aktiver Tab: ${activeTab}`);
        if (dokumentCards.length) parts.push(`Geschaeftsdokumente:\n${dokumentCards.map(d => `  - ${d}`).join('\n')}`);
        if (sidebarData.length) parts.push(`Seitenleiste: ${sidebarData.slice(0, 15).join(' | ')}`);
        if (formData.length) parts.push(`Formulardaten: ${formData.slice(0, 15).join(' | ')}`);
        if (disabledButtons.length) parts.push(`Deaktivierte Buttons: ${disabledButtons.join(', ')}`);
        if (infoMessages.length) parts.push(`Hinweise: ${infoMessages.join('; ')}`);

        return {
            route: location.pathname,
            pageTitle,
            visibleContent: parts.join('\n') || undefined,
            errorMessages: errors || undefined,
            latitude: geoLocation?.latitude,
            longitude: geoLocation?.longitude,
        };
    }, [location.pathname, geoLocation]);

    const dokumentLesen = async (file: File) => {
        setError(null); setBelegDatei(null); setLiestDokument(true);
        try {
            const form = new FormData();
            form.append('datei', file);
            const res = await fetch('/api/ki-assistent/dokument/lesen', { method: 'POST', body: form });
            const d = await res.json().catch(() => ({})) as { name?: string; text?: string | null; hinweis?: string | null; message?: string; error?: string };
            if (!res.ok) { setError(d.message || d.error || 'Das Dokument konnte nicht gelesen werden.'); return; }
            setAnhang({ file, name: d.name || file.name, text: d.text ?? null, hinweis: d.hinweis ?? null });
        } catch {
            setError('Das Dokument konnte nicht gelesen werden. Ist der Server erreichbar?');
        } finally {
            setLiestDokument(false);
        }
    };

    /** Strg+V mit einem Bild in der Zwischenablage (zum Beispiel ein Screenshot): wird wie ein hineingezogenes Dokument gelesen. Reiner Text wird normal eingefügt. */
    const einfuegen = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
        if (!assistent || loading || liestDokument) return;
        const bild = Array.from(e.clipboardData?.files ?? []).find(f => f.type.startsWith('image/'));
        if (!bild) return;
        e.preventDefault();
        const jetzt = new Date();
        const zeit = `${jetzt.getFullYear()}-${String(jetzt.getMonth() + 1).padStart(2, '0')}-${String(jetzt.getDate()).padStart(2, '0')}_${String(jetzt.getHours()).padStart(2, '0')}${String(jetzt.getMinutes()).padStart(2, '0')}`;
        const endung = bild.type === 'image/jpeg' ? 'jpg' : bild.type === 'image/webp' ? 'webp' : 'png';
        void dokumentLesen(new File([bild], `Bild_${zeit}.${endung}`, { type: bild.type }));
    };

    /** Eine E-Mail aus dem E-Mail-Center als Anhang holen (Text, Absender, Betreff, Nummer); verändert nichts. */
    const emailAnhaengen = useCallback(async (emailId: number) => {
        setError(null); setBelegDatei(null); setLiestDokument(true);
        try {
            const res = await fetch(`/api/ki-assistent/dokument/email/${emailId}`);
            const d = await res.json().catch(() => ({})) as { name?: string; text?: string | null; hinweis?: string | null; message?: string; error?: string };
            if (!res.ok) { setError(d.message || d.error || 'Die E-Mail konnte nicht gelesen werden.'); return; }
            setAnhang({ file: null, name: d.name || 'E-Mail', text: d.text ?? null, hinweis: d.hinweis ?? null, emailId });
        } catch {
            setError('Die E-Mail konnte nicht gelesen werden. Ist der Server erreichbar?');
        } finally {
            setLiestDokument(false);
        }
    }, []);

    const dokumentEntfernen = () => { setAnhang(null); setBelegDatei(null); if (dateiFeld.current) dateiFeld.current.value = ''; };

    const sendMessage = async (vorgabe?: string) => {
        const trimmed = (vorgabe ?? input).trim();
        if (!trimmed || loading) return;

        const userMessage: Message = { role: 'user', text: trimmed };
        const newMessages = [...messages, userMessage];
        setMessages(newMessages);
        setInput('');
        setError(null);
        setLoading(true);

        try {
            if (assistent) {
                // Wo man gerade ist, damit „Was bedeutet das hier?" beantwortbar ist (der Server lässt den sichtbaren Inhalt bei gesperrten Programmdaten weg)
                const ctx = gatherPageContext();
                const antwort = await fetch('/api/ki-assistent/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        messages: newMessages.map(m => ({ role: m.role, text: m.text })),
                        seite: { route: ctx.route, titel: ctx.pageTitle, fehler: ctx.errorMessages, inhalt: ctx.visibleContent },
                        dokument: anhang?.text ? { name: anhang.name, text: anhang.text } : undefined,
                        // Für Fehlerberichte: Der Server hängt sie nach Ihrer Bestätigung bereinigt an (nichts geht ohne Bestätigung hinaus)
                        browser: { fehler: holeBrowserFehler() },
                    }),
                });
                const d = await antwort.json();
                if (!antwort.ok || d.error) {
                    setError(d.error || d.message || 'Fehler bei der KI-Anfrage');
                    return;
                }
                setMessages(prev => [...prev, {
                    role: 'assistant', text: d.text, quelle: d.quelle,
                    vorschlaege: (d.vorschlaege ?? []).map((v: AssistentVorschlag) => ({ id: v.id, werkzeug: v.werkzeug, beschreibung: v.beschreibung, status: { art: 'offen' } as VorschlagStatus })),
                }]);
                return;
            }
            const context = gatherPageContext();
            const res = await fetch('/api/ki-hilfe/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    messages: newMessages.map(m => ({ role: m.role, text: m.text })),
                    context,
                }),
            });

            const data = await res.json();

            if (!res.ok || data.error) {
                setError(data.error || 'Fehler bei der KI-Anfrage');
                return;
            }

            setMessages(prev => [...prev, { role: 'assistant', text: data.reply, sources: data.sources }]);
        } catch {
            setError('Verbindung zur KI fehlgeschlagen. Ist der Server erreichbar?');
        } finally {
            setLoading(false);
        }
    };

    const setzeVorschlagStatus = (msgIndex: number, id: string, status: VorschlagStatus) => {
        setMessages(prev => prev.map((m, i) => (i !== msgIndex || !m.vorschlaege ? m
            : { ...m, vorschlaege: m.vorschlaege.map(v => (v.id === id ? { ...v, status } : v)) })));
    };

    const entscheide = async (msgIndex: number, id: string, aktion: 'bestaetigen' | 'verwerfen') => {
        if (aktion === 'bestaetigen') setzeVorschlagStatus(msgIndex, id, { art: 'laeuft' });
        try {
            const res = await fetch(`/api/ki-assistent/vorschlaege/${encodeURIComponent(id)}/${aktion}`, { method: 'POST' });
            const d = await res.json().catch(() => ({})) as { ok?: boolean; meldung?: string };
            const meldung = d.meldung ?? (res.ok ? 'Erledigt.' : 'Das hat nicht geklappt.');
            if (aktion === 'verwerfen') setzeVorschlagStatus(msgIndex, id, { art: 'verworfen', meldung: res.ok ? 'Verworfen. Es wurde nichts geändert.' : meldung });
            else setzeVorschlagStatus(msgIndex, id, { art: res.ok ? 'bestaetigt' : 'fehler', meldung });
        } catch {
            setzeVorschlagStatus(msgIndex, id, { art: 'fehler', meldung: 'Die Verbindung ist fehlgeschlagen. Es wurde nichts geändert.' });
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    const clearChat = () => {
        setMessages([]);
        setError(null);
        localStorage.setItem(keyMessages, '[]');
    };

    // Custom-Event zum Öffnen von außen (z.B. aus der RibbonNav)
    useEffect(() => {
        const handler = () => {
            setOpen(true);
            setMinimized(false);
        };
        window.addEventListener('ki-hilfe-open', handler);
        return () => window.removeEventListener('ki-hilfe-open', handler);
    }, []);

    // Eine E-Mail aus dem E-Mail-Center wurde auf das Chatfenster gezogen (oder über das Menü gesendet): Chat öffnen und die Mail anhängen
    useEffect(() => {
        const handler = (e: Event) => {
            const id = (e as CustomEvent<{ emailId?: number }>).detail?.emailId;
            if (typeof id !== 'number') return;
            setOpen(true);
            setMinimized(false);
            void emailAnhaengen(id);
        };
        window.addEventListener('ki-email-anhaengen', handler);
        return () => window.removeEventListener('ki-email-anhaengen', handler);
    }, [emailAnhaengen]);

    // Wenn geschlossen oder minimiert: nichts rendern (Trigger ist jetzt in der RibbonNav)
    if (!open || minimized) {
        return null;
    }

    // Full chat panel
    return createPortal(
        <div
            data-ki-chat="true"
            style={{ left: rahmen.x, top: rahmen.y, width: rahmen.w, height: rahmen.h }}
            className="fixed z-50 flex flex-col bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden"
            onDragOver={e => { if (assistent && e.dataTransfer.types.includes('Files')) { e.preventDefault(); setZiehen(true); } }}
            onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setZiehen(false); }}
            onDrop={e => { setZiehen(false); if (!assistent) return; const f = e.dataTransfer.files?.[0]; if (f) { e.preventDefault(); void dokumentLesen(f); } }}
        >
            <div data-testid="ki-groesse-rechts" aria-hidden="true" onPointerDown={startZiehen('rechts')} className="absolute bottom-0 right-0 z-20 h-4 w-4 cursor-nwse-resize touch-none" />
            <div data-testid="ki-groesse-links" aria-hidden="true" onPointerDown={startZiehen('links')} className="absolute bottom-0 left-0 z-20 h-4 w-4 cursor-nesw-resize touch-none" />
            {ziehen && (
                <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center bg-rose-50/90 border-2 border-dashed border-rose-400 rounded-2xl" data-testid="ki-drop">
                    <p className="flex items-center gap-2 text-sm font-semibold text-rose-700"><FileText className="h-5 w-5" aria-hidden="true" /> Dokument hier ablegen</p>
                </div>
            )}
            {/* Header: hier lässt sich das Fenster verschieben, ein Doppelklick stellt es zurück */}
            <div
                data-testid="ki-kopf"
                title="Zum Verschieben ziehen, Doppelklick stellt die Standardlage wieder her"
                onPointerDown={startZiehen('verschieben')}
                onDoubleClick={(e) => { if (!(e.target as HTMLElement).closest('button')) rahmenZuruecksetzen(); }}
                className="flex items-center justify-between px-4 py-3 bg-gradient-to-r from-rose-600 to-rose-700 text-white flex-shrink-0 cursor-move select-none touch-none">
                <div className="flex items-center gap-2">
                    <Gem className="w-5 h-5" />
                    <div>
                        <h3 className="text-sm font-semibold">KI</h3>
                        <p className="text-xs text-rose-200">{assistent ? 'Fragen zum Programm und zu Ihren Daten' : 'Programmhilfe'}</p>
                    </div>
                </div>
                <div className="flex items-center gap-1">
                    <button
                        onClick={clearChat}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Chat leeren"
                    >
                        <Trash2 className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setMinimized(true)}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Minimieren"
                    >
                        <Minimize2 className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setOpen(false)}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Schließen"
                    >
                        <X className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
                {messages.length === 0 && (
                    <div className="text-center text-slate-400 mt-12 space-y-3">
                        <Gem className="w-10 h-10 mx-auto text-rose-300" />
                        <p className="text-sm font-medium">Wie kann ich dir helfen?</p>
                        <div className="space-y-2">
                            {[
                                'Wie erstelle ich ein neues Anfrage?',
                                'Wo finde ich die Zeiterfassung?',
                                'Wie funktioniert die Kalkulation?',
                            ].map((suggestion) => (
                                <button
                                    key={suggestion}
                                    onClick={() => {
                                        setInput(suggestion);
                                        inputRef.current?.focus();
                                    }}
                                    className="block w-full text-left text-xs px-3 py-2 rounded-lg bg-slate-50 hover:bg-rose-50 hover:text-rose-700 transition-colors border border-slate-100"
                                >
                                    {suggestion}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {messages.map((msg, i) => (
                    <div
                        key={i}
                        className={cn(
                            'flex',
                            msg.role === 'user' ? 'justify-end' : 'justify-start'
                        )}
                    >
                        <div
                            className={cn(
                                'max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
                                msg.role === 'user'
                                    ? 'bg-rose-600 text-white rounded-br-md'
                                    : 'bg-slate-100 text-slate-800 rounded-bl-md'
                            )}
                        >
                            {msg.role === 'assistant' ? (
                                <>
                                    <MarkdownText text={msg.text} onNavigate={navigate} />
                                    {msg.vorschlaege?.map(v => (
                                        <KiAssistentVorschlag key={v.id} vorschlag={v} status={v.status}
                                            onBestaetigen={() => void entscheide(i, v.id, 'bestaetigen')} onVerwerfen={() => void entscheide(i, v.id, 'verwerfen')} />
                                    ))}
                                    {msg.quelle && (
                                        <p className="mt-2 flex items-center gap-1 text-[11px] text-slate-500" data-testid="ki-quelle">
                                            {msg.quelle.lokal ? <ShieldCheck className="h-3 w-3 text-emerald-600" aria-hidden="true" /> : <Cloud className="h-3 w-3 text-amber-600" aria-hidden="true" />}
                                            {msg.quelle.anbieter}{msg.quelle.modell ? ` · ${msg.quelle.modell}` : ''} · {msg.quelle.lokal ? 'Daten bleiben im Haus' : 'Fragen und Daten gingen an einen Cloud-Anbieter'}
                                        </p>
                                    )}
                                    {msg.sources && msg.sources.length > 0 && (
                                        <div className="mt-2 pt-2 border-t border-slate-200/60">
                                            <p className="text-[11px] text-slate-400 font-medium mb-1">Quellen:</p>
                                            <div className="space-y-0.5">
                                                {msg.sources.map((source, j) => (
                                                    <a
                                                        key={j}
                                                        href={source.url}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="flex items-center gap-1 text-[11px] text-rose-600 hover:text-rose-800 hover:underline truncate"
                                                    >
                                                        <ExternalLink className="w-3 h-3 flex-shrink-0" />
                                                        <span className="truncate">{source.title || source.url}</span>
                                                    </a>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </>
                            ) : (
                                <span className="whitespace-pre-wrap">{msg.text}</span>
                            )}
                        </div>
                    </div>
                ))}

                {loading && (
                    <div className="flex justify-start">
                        <div className="bg-slate-100 rounded-2xl rounded-bl-md px-4 py-3">
                            <div className="flex gap-1.5">
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                            </div>
                        </div>
                    </div>
                )}

                {!assistent && (
                    <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900" data-testid="ki-nur-hilfe">
                        <p className="font-semibold">Die KI für Ihre Daten ist noch nicht eingerichtet.</p>
                        <p className="mt-0.5">Es antwortet nur die Programmhilfe: Sie erklärt das Programm, kann aber nichts nachschlagen, anlegen oder Dokumente lesen.{assistentMeldung ? ` (${assistentMeldung})` : ''}</p>
                        <button type="button" onClick={() => navigate('/einstellungen')} className="mt-1 font-medium text-rose-700 underline hover:text-rose-800">Zu den KI-Einstellungen</button>
                    </div>
                )}

                {error && (
                    <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2 text-sm text-red-700">
                        {error}
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div className="flex-shrink-0 border-t border-slate-200 p-3">
                {assistent && anhang && (
                    <div className="mb-2 rounded-lg border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700" data-testid="ki-anhang">
                        <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 flex-shrink-0 text-rose-600" aria-hidden="true" />
                            <span className="min-w-0 flex-1 truncate font-medium" title={anhang.name}>{anhang.name}</span>
                            <button type="button" onClick={dokumentEntfernen} aria-label="Dokument entfernen" title="Dokument entfernen" className="rounded p-1 hover:bg-slate-200"><X className="h-3.5 w-3.5" aria-hidden="true" /></button>
                        </div>
                        {anhang.hinweis && <p className="mt-1 text-amber-800">{anhang.hinweis}</p>}
                        <div className="mt-1.5 flex flex-wrap gap-1.5">
                            {anhang.emailId != null && anhang.text && (
                                <button type="button" disabled={loading} data-testid="ki-email-anfrage"
                                    onClick={() => void sendMessage(`Lege aus dieser E-Mail eine Anfrage an (E-Mail-Nr. ${anhang.emailId}). Suche zuerst mit kunden_suchen nach dem Absender (E-Mail-Adresse) und nach der Firma, ob es den Kunden schon gibt. Gibt es ihn, nimm ihn, sonst lege ihn mit den Angaben aus der E-Mail neu an. Trage alle Details ein, die in der E-Mail stehen, und verknüpfe die E-Mail mit der Anfrage.`)}
                                    className="rounded-full border border-rose-300 bg-rose-50 px-2.5 py-1 font-medium text-rose-700 hover:bg-rose-100 disabled:opacity-50">Kunde prüfen und Anfrage anlegen</button>
                            )}
                            {anhang.emailId != null && anhang.text && (
                                <button type="button" disabled={loading} data-testid="ki-email-notiz"
                                    onClick={() => void sendMessage(`Speichere das Wichtige aus dieser E-Mail (E-Mail-Nr. ${anhang.emailId}) als kurze Notiz beim passenden Lieferanten. Suche ihn zuerst mit lieferanten_suchen nach Absender und Firma. Schreibe nur die Information auf, nicht die ganze E-Mail, mit Datum und Betreff als Quelle.`)}
                                    className="rounded-full border border-slate-300 bg-white px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50">Beim Lieferanten notieren</button>
                            )}
                            {anhang.text && ([
                                ['Zusammenfassen', 'Fasse das Dokument kurz zusammen.'],
                                ['Wichtige Angaben', 'Liste die wichtigsten Angaben auf: Namen, Daten, Fristen, Beträge, Nummern.'],
                                ['Was ist zu tun?', 'Was muss ich nach diesem Dokument tun? Nenne Aufgaben und Fristen.'],
                            ] as const).map(([titel, frage]) => (
                                <button key={titel} type="button" disabled={loading} onClick={() => void sendMessage(frage)}
                                    className="rounded-full border border-slate-300 bg-white px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50">{titel}</button>
                            ))}
                            {anhang.file && <button type="button" onClick={() => setBelegDatei(anhang.file)}
                                className="rounded-full border border-rose-300 bg-white px-2.5 py-1 font-medium text-rose-700 hover:bg-rose-50">Als Beleg ablegen</button>}
                        </div>
                        {anhang.text && <p className="mt-1 text-slate-500">Oder unten eine eigene Aufgabe zu diesem Dokument eingeben.</p>}
                    </div>
                )}
                {assistent && liestDokument && (
                    <p role="status" className="mb-2 flex items-center gap-1.5 text-xs text-slate-600"><Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Dokument wird gelesen …</p>
                )}
                {assistent && (!anhang || belegDatei) && <div className="mb-2"><KiBelegKarte key={belegDatei?.name ?? 'leer'} datei={belegDatei} /></div>}
                <div className="flex items-end gap-2">
                    {assistent && (
                        <>
                            <input ref={dateiFeld} type="file" accept=".pdf,.txt,.csv,.xml,.json,.md,image/*" className="sr-only" aria-label="Dokument auswählen" onChange={e => { const f = e.target.files?.[0]; if (f) void dokumentLesen(f); }} />
                            <button type="button" onClick={() => dateiFeld.current?.click()} disabled={loading || liestDokument} aria-label="Dokument anhängen" title="Dokument anhängen (oder hineinziehen, Bilder auch mit Strg+V einfügen)"
                                className="flex-shrink-0 p-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40"><Paperclip className="w-4 h-4" aria-hidden="true" /></button>
                        </>
                    )}
                    <textarea
                        ref={inputRef}
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        onPaste={einfuegen}
                        placeholder="Frage stellen..."
                        rows={1}
                        className="flex-1 resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent max-h-24 overflow-y-auto"
                        disabled={loading}
                    />
                    <button
                        onClick={() => void sendMessage()}
                        disabled={!input.trim() || loading}
                        className="flex-shrink-0 p-2.5 rounded-xl bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    >
                        <Send className="w-4 h-4" />
                    </button>
                </div>
                <p className="text-[10px] text-slate-400 mt-1.5 text-center">
                    KI-Antworten können fehlerhaft sein · Änderungen nur nach Ihrer Bestätigung
                </p>
            </div>
        </div>,
        document.body
    );
}

/** Valid internal routes that can be linked */
const VALID_ROUTES = new Set([
    '/projekte', '/anfragen', '/kunden', '/lieferanten', '/artikel',
    '/bestellungen', '/bestellungen/bedarf', '/textbausteine', '/leistungen',
    '/arbeitsgaenge', '/produktkategorien', '/mitarbeiter', '/arbeitszeitarten',
    '/kalender', '/emails', '/formulare', '/offeneposten',
    '/rechnungsuebersicht', '/miete', '/analyse', '/zeitbuchungen',
    '/auswertung', '/steuerberater', '/zeitkonten', '/feiertage',
    '/urlaubsantraege', '/firma', '/benutzer',
]);

/** Simple markdown renderer for assistant messages */
function MarkdownText({ text, onNavigate }: { text: string; onNavigate: (path: string) => void }) {
    const lines = text.split('\n');
    const elements: React.ReactNode[] = [];

    const fmt = (t: string) => formatInline(t, onNavigate);

    let i = 0;
    while (i < lines.length) {
        const line = lines[i];

        // Detect markdown table: header row | separator row | data rows
        if (line.includes('|') && i + 1 < lines.length && /^[\s|:-]+$/.test(lines[i + 1].replace(/[^|:\s-]/g, ''))) {
            const tableLines: string[] = [];
            let j = i;
            while (j < lines.length && lines[j].includes('|')) {
                tableLines.push(lines[j]);
                j++;
            }
            if (tableLines.length >= 2) {
                const parseRow = (row: string) =>
                    row.split('|').map(c => c.trim()).filter(c => c.length > 0);

                const headers = parseRow(tableLines[0]);
                const dataRows = tableLines.slice(2).map(parseRow);

                elements.push(
                    <div key={i} className="my-2 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="bg-slate-50">
                                    {headers.map((h, hi) => (
                                        <th key={hi} className="px-2 py-1.5 text-left font-semibold text-slate-700 border-b border-slate-200">
                                            {fmt(h)}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {dataRows.map((row, ri) => (
                                    <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                                        {row.map((cell, ci) => (
                                            <td key={ci} className="px-2 py-1.5 text-slate-600 border-b border-slate-100">
                                                {fmt(cell)}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
                i = j;
                continue;
            }
        }

        if (line.startsWith('### ')) {
            elements.push(<h4 key={i} className="font-semibold text-sm mt-2 mb-1">{fmt(line.slice(4))}</h4>);
        } else if (line.startsWith('## ')) {
            elements.push(<h3 key={i} className="font-bold text-sm mt-2 mb-1">{fmt(line.slice(3))}</h3>);
        } else if (line.startsWith('# ')) {
            elements.push(<h2 key={i} className="font-bold text-base mt-2 mb-1">{fmt(line.slice(2))}</h2>);
        } else if (line.startsWith('- ') || line.startsWith('* ')) {
            elements.push(
                <div key={i} className="flex gap-1.5 ml-2">
                    <span className="text-rose-400 mt-0.5">•</span>
                    <span>{fmt(line.slice(2))}</span>
                </div>
            );
        } else if (/^\d+\.\s/.test(line)) {
            const match = line.match(/^(\d+)\.\s(.*)$/);
            if (match) {
                elements.push(
                    <div key={i} className="flex gap-1.5 ml-2">
                        <span className="text-rose-500 font-medium">{match[1]}.</span>
                        <span>{fmt(match[2])}</span>
                    </div>
                );
            }
        } else if (line.trim() === '') {
            elements.push(<div key={i} className="h-2" />);
        } else {
            elements.push(<p key={i}>{fmt(line)}</p>);
        }
        i++;
    }

    return <div className="space-y-0.5">{elements}</div>;
}

/** Format inline markdown: **bold**, `code`, *italic*, [link](/route) */
function formatInline(text: string, onNavigate: (path: string) => void): React.ReactNode {
    const parts: React.ReactNode[] = [];
    // Match: [text](/route), **bold**, `code`, *italic*
    const regex = /(\[([^\]]+)\]\((\/?[a-z][a-z0-9-/]*)\)|\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*)/g;
    let lastIndex = 0;
    let match;

    while ((match = regex.exec(text)) !== null) {
        if (match.index > lastIndex) {
            parts.push(text.slice(lastIndex, match.index));
        }

        if (match[2] && match[3]) {
            // Markdown link [text](/route)
            const linkText = match[2];
            const href = match[3].startsWith('/') ? match[3] : '/' + match[3];
            if (VALID_ROUTES.has(href)) {
                parts.push(
                    <button
                        key={match.index}
                        onClick={() => onNavigate(href)}
                        className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-semibold hover:bg-rose-200 hover:text-rose-800 transition-colors border border-rose-200 shadow-sm align-middle"
                        title={`Zu ${linkText} navigieren`}
                    >
                        <ExternalLink className="w-3 h-3" />
                        {linkText}
                    </button>
                );
            } else {
                // Unknown route, render as plain text
                parts.push(<strong key={match.index} className="font-semibold">{linkText}</strong>);
            }
        } else if (match[4]) {
            parts.push(<strong key={match.index} className="font-semibold">{match[4]}</strong>);
        } else if (match[5]) {
            parts.push(
                <code key={match.index} className="bg-slate-200 text-rose-700 px-1 py-0.5 rounded text-xs font-mono">
                    {match[5]}
                </code>
            );
        } else if (match[6]) {
            parts.push(<em key={match.index}>{match[6]}</em>);
        }

        lastIndex = match.index + match[0].length;
    }

    if (lastIndex < text.length) {
        parts.push(text.slice(lastIndex));
    }

    return parts.length === 0 ? text : <>{parts}</>;
}
