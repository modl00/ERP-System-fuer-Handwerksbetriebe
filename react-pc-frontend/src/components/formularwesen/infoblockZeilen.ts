/** Zerlegt Text mit Tab-Zeichen in Bezeichnung/Wert-Paare; Zeilen ohne Wert bleiben als reine Bezeichnung. */
export function zerlegeInfoblockZeilen(text: string): { bezeichnung: string; wert: string | null }[] {
    return text.split('\n')
        .map(z => {
            const tab = z.indexOf('\t');
            return tab < 0 ? { bezeichnung: z, wert: null as string | null } : { bezeichnung: z.slice(0, tab), wert: z.slice(tab + 1).trim() };
        })
        .filter(z => z.wert === null || z.wert !== '');
}
