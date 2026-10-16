/**
 * `crypto.randomUUID()` existiert nur in sicheren Kontexten (HTTPS oder
 * `localhost`). Bei lokalem LAN-Betrieb über reines HTTP (siehe
 * docs/INSTALLATION.md: "Nicht nötig" für HTTPS im LAN) ist die Funktion im
 * Browser schlicht nicht vorhanden und wirft `TypeError: crypto.randomUUID
 * is not a function`. generateId() nutzt sie, wenn verfügbar, sonst einen
 * Fallback nach UUID-v4-Schema.
 */
export function generateId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}
