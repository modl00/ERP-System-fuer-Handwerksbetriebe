/**
 * Netzwerkordner mit Benutzername und Passwort im Docker-Betrieb (#133): Das Programm kann eine Freigabe im Container nicht selbst einhängen, das übernimmt der Stack
 * (docker-compose.netzwerk.yml). Diese Hilfe macht aus Adresse und Benutzer die vier Zeilen für „Environment variables" in Portainer. Das Passwort wird hier nie
 * eingegeben oder gespeichert: In der Zeile steht ein Platzhalter, den man im Stack selbst ersetzt.
 */

export type SmbVersion = 'vers=3.0' | 'vers=2.1' | 'vers=1.0';

export const SMB_VERSIONEN: { wert: SmbVersion; text: string }[] = [
    { wert: 'vers=3.0', text: 'Neu (SMB 3, Standard)' },
    { wert: 'vers=2.1', text: 'Älteres Gerät (SMB 2.1)' },
    { wert: 'vers=1.0', text: 'Sehr altes Gerät (SMB 1, unsicher)' },
];

export interface SmbErgebnis {
    /** Die vier Zeilen zum Kopieren; leer, wenn die Eingabe noch nicht reicht. */
    zeilen: string;
    /** Die Adresse so, wie der Stack sie braucht (ohne Schrägstriche am Anfang, mit „/"). */
    adresse: string;
    /** Was an der Eingabe nicht stimmt, in Klartext; leer, wenn alles passt. */
    fehler: string;
    /** Ein Hinweis, der trotz gültiger Eingabe angezeigt werden sollte. */
    warnung: string;
}

/** Macht aus {@code \\nas\erp}, {@code //nas/erp}, {@code smb://nas/erp} oder {@code nas/erp} die Form {@code nas/erp}. */
export function normalisiereSmbAdresse(roh: string): string {
    return roh.trim().replace(/^smb:\/\//i, '').replace(/\\/g, '/').replace(/^\/+/, '').replace(/\/+$/, '').replace(/\/{2,}/g, '/');
}

export function smbVariablen(adresseRoh: string, benutzerRoh: string, version: SmbVersion): SmbErgebnis {
    const adresse = normalisiereSmbAdresse(adresseRoh);
    const benutzer = benutzerRoh.trim();
    const leer = { zeilen: '', adresse, warnung: '' };
    if (!adresse && !benutzer) return { ...leer, fehler: '' };
    if (!adresse) return { ...leer, fehler: 'Bitte die Adresse der Freigabe eintragen, zum Beispiel nas.local/erp-dokumente.' };
    if (!adresse.includes('/')) return { ...leer, fehler: 'Die Adresse braucht Gerät und Freigabe, zum Beispiel nas.local/erp-dokumente (nicht nur den Gerätenamen).' };
    if (/\s/.test(adresse)) return { ...leer, fehler: 'In der Adresse dürfen keine Leerzeichen stehen. Hat die Freigabe ein Leerzeichen im Namen, bitte am Gerät umbenennen.' };
    if (!benutzer) return { ...leer, fehler: 'Bitte den Benutzer der Freigabe eintragen.' };
    // Zeichen, die in der Mount-Option oder in einer Variablen Ärger machen (Komma trennt Optionen, = und Anführungszeichen, Zeilenumbruch)
    if (/[,"'\r\n$`]/.test(adresse) || /[,"'\r\n$`=\s]/.test(benutzer)) {
        return { ...leer, fehler: 'Adresse und Benutzer dürfen keine Kommas, Anführungszeichen, Leerzeichen, $ oder Gleichheitszeichen enthalten.' };
    }
    const zeilen = [`ERP_SMB_ADRESSE=${adresse}`, `ERP_SMB_BENUTZER=${benutzer}`, 'ERP_SMB_PASSWORT=<hier Ihr Passwort eintragen>', `ERP_SMB_OPTIONEN=${version}`].join('\n');
    return { zeilen, adresse, fehler: '', warnung: version === 'vers=1.0' ? 'SMB 1 gilt als unsicher. Bitte nur für sehr alte Geräte und nur im eigenen Netz nutzen.' : '' };
}
