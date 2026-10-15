/**
 * PDF aus einer Antwort anzeigen oder speichern. Ein Fenster muss <b>beim Klick</b> geöffnet werden, sonst blockt
 * der Browser es als Pop-up: Erst {@link oeffneWartefenster}, dann laden, dann {@link zeigePdf} in dieses Fenster.
 */
export function oeffneWartefenster(hinweis: string): Window | null {
    const fenster = window.open('', '_blank');
    if (!fenster) return null;
    try {
        fenster.opener = null;
        fenster.document.title = hinweis;
        fenster.document.body.style.cssText = 'font-family: system-ui, sans-serif; color: #475569; padding: 2rem';
        fenster.document.body.textContent = `${hinweis} …`;
    } catch {
        // Ein Fenster, das wir nicht beschreiben dürfen, bekommt später trotzdem die Adresse
    }
    return fenster;
}

export function schliesseFenster(fenster: Window | null): void {
    try {
        fenster?.close();
    } catch {
        // schon zu
    }
}

/** Zeigt das PDF im Wartefenster. {@code false}: Es gab kein Fenster (Pop-up-Sperre), dann sollte der Aufrufer es speichern lassen. */
export function zeigePdf(blob: Blob, fenster: Window | null): boolean {
    if (!fenster || fenster.closed) return false;
    const url = URL.createObjectURL(blob);
    fenster.location.href = url;
    // Das Fenster liest die Adresse sofort; eine Weile später darf der Speicher frei werden
    window.setTimeout(() => URL.revokeObjectURL(url), 120_000);
    return true;
}

export function speicherePdf(blob: Blob, dateiname: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = dateiname;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
