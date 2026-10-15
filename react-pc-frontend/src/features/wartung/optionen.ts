/** Die Möglichkeiten einer Auswahl stehen zeilenweise im Textfeld: getrimmt, ohne Leere und Doppelte. */
export function optionenAusText(text: string): string[] {
    return [...new Set(text.split('\n').map((z) => z.trim()).filter((z) => z !== ''))];
}
