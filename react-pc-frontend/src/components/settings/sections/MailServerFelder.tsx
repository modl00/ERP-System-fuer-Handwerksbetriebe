import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { Select } from '../../ui/select-custom';
import { ANBIETER, EIGENER_SERVER, SSL_PORT, STARTTLS_PORT, verschluesselungFuer, type Anbieter, type Art, type ServerAngabe } from './mailAnbieter';

/** Server-Angaben für den Versand (SMTP) und den Empfang (IMAP) an einer Stelle, mit Vorlagen für die häufigsten Anbieter. Die Verschlüsselung setzt nur den Port. */

export function AnbieterAuswahl({ id, wert, onChange }: { id: string; wert: string; onChange: (anbieter: Anbieter | null) => void }) {
    const aktuell = ANBIETER.find((a) => a.id === wert);
    return (
        <div>
            <Label htmlFor={id}>Mein Anbieter</Label>
            <Select
                id={id}
                className="sm:max-w-md"
                value={wert}
                onChange={(v) => onChange(ANBIETER.find((a) => a.id === v) ?? null)}
                options={[...ANBIETER.map((a) => ({ value: a.id, label: a.name })), { value: EIGENER_SERVER, label: 'Anderer Anbieter oder eigener Server' }]}
            />
            <p className="text-xs text-slate-500 mt-1">
                Die Auswahl trägt Server und Port unten ein. Die Angaben können Sie jederzeit ändern; im Zweifel gilt die Anleitung Ihres Anbieters.
            </p>
            {aktuell?.hinweis && <p className="mt-1 rounded-lg bg-amber-50 p-2 text-xs text-amber-900" role="note">{aktuell.hinweis}</p>}
        </div>
    );
}

interface ServerBlockProps {
    /** Eindeutiger Anfang der Feld-Kennungen, zum Beispiel „postfachSmtp" */
    idPrefix: string;
    art: Art;
    titel: string;
    hinweis?: string;
    wert: ServerAngabe;
    onChange: (neu: ServerAngabe) => void;
    hostBeschriftung: string;
    hostBeispiel: string;
}

/** Server, Verschlüsselung und Port in einer Zeile: Die Verschlüsselung setzt den üblichen Port, der Port bleibt von Hand änderbar. */
export function ServerBlock({ idPrefix, art, titel, hinweis, wert, onChange, hostBeschriftung, hostBeispiel }: ServerBlockProps) {
    const modus = verschluesselungFuer(art, wert.port);
    return (
        <section className="rounded-lg border border-slate-200 p-4">
            <h4 className="text-sm font-semibold text-slate-900">{titel}</h4>
            {hinweis && <p className="mb-3 text-xs text-slate-500">{hinweis}</p>}
            <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-3">
                <div className="md:col-span-1">
                    <Label htmlFor={`${idPrefix}Host`}>{hostBeschriftung}</Label>
                    <Input id={`${idPrefix}Host`} placeholder={hostBeispiel} value={wert.host} onChange={(e) => onChange({ ...wert, host: e.target.value })} autoComplete="off" />
                </div>
                <div>
                    <Label htmlFor={`${idPrefix}Verschluesselung`}>Verschlüsselung</Label>
                    <Select
                        id={`${idPrefix}Verschluesselung`}
                        value={modus}
                        onChange={(v) => onChange({ ...wert, port: v === 'SSL' ? SSL_PORT[art] : STARTTLS_PORT[art] })}
                        options={[
                            { value: 'SSL', label: `SSL/TLS (Port ${SSL_PORT[art]})` },
                            { value: 'STARTTLS', label: `STARTTLS (Port ${STARTTLS_PORT[art]})` },
                        ]}
                    />
                </div>
                <div>
                    <Label htmlFor={`${idPrefix}Port`}>Port</Label>
                    <Input
                        id={`${idPrefix}Port`}
                        type="number"
                        min={1}
                        max={65535}
                        // Leeres Feld bleibt leer, damit die Zahl getippt werden kann
                        value={wert.port === 0 ? '' : wert.port}
                        onChange={(e) => onChange({ ...wert, port: parseInt(e.target.value, 10) || 0 })}
                    />
                    <p className="mt-1 text-xs text-slate-500">
                        {wert.port > 0 ? `Mit Port ${wert.port} wird per ${modus === 'SSL' ? 'SSL/TLS' : 'STARTTLS'} verschlüsselt.` : 'Bitte einen Port eintragen.'}
                    </p>
                </div>
            </div>
        </section>
    );
}
