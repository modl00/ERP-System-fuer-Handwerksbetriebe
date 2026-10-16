import { describe, expect, it } from 'vitest';
import { normalisiereSmbAdresse, smbVariablen } from './netzwerkordner';

describe('Netzwerkordner: Variablen für den Stack', () => {
    it.each([
        ['\\\\nas\\erp\\dokumente', 'nas/erp/dokumente'],
        ['//nas/erp/dokumente/', 'nas/erp/dokumente'],
        ['smb://nas.local/erp-dokumente', 'nas.local/erp-dokumente'],
        ['  SMB://nas/erp  ', 'nas/erp'],
        ['nas/erp', 'nas/erp'],
        ['//nas//erp', 'nas/erp'],
    ])('macht aus %s die Adresse %s', (roh, soll) => {
        expect(normalisiereSmbAdresse(roh)).toBe(soll);
    });

    it('erzeugt die vier Zeilen mit Platzhalter für das Passwort, das Passwort steht nie darin', () => {
        const e = smbVariablen('\\\\nas.local\\erp-dokumente', 'erika', 'vers=3.0');
        expect(e.fehler).toBe('');
        expect(e.zeilen.split('\n')).toEqual([
            'ERP_SMB_ADRESSE=nas.local/erp-dokumente',
            'ERP_SMB_BENUTZER=erika',
            'ERP_SMB_PASSWORT=<hier Ihr Passwort eintragen>',
            'ERP_SMB_OPTIONEN=vers=3.0',
        ]);
        expect(e.warnung).toBe('');
    });

    it('ältere Geräte bekommen die passende Version, SMB 1 mit Warnung', () => {
        expect(smbVariablen('nas/erp', 'max', 'vers=2.1').zeilen).toContain('ERP_SMB_OPTIONEN=vers=2.1');
        expect(smbVariablen('nas/erp', 'max', 'vers=1.0').warnung).toContain('unsicher');
    });

    it('sagt in Klartext, was fehlt, und erzeugt dann keine Zeilen', () => {
        expect(smbVariablen('', '', 'vers=3.0')).toMatchObject({ zeilen: '', fehler: '' });
        expect(smbVariablen('', 'max', 'vers=3.0').fehler).toContain('Adresse');
        expect(smbVariablen('nas', 'max', 'vers=3.0').fehler).toContain('Gerät und Freigabe');
        expect(smbVariablen('nas/erp', '', 'vers=3.0').fehler).toContain('Benutzer');
        expect(smbVariablen('nas/mein ordner', 'max', 'vers=3.0').fehler).toContain('Leerzeichen');
        expect(smbVariablen('nas/erp', 'max', 'vers=3.0').zeilen).not.toBe('');
    });

    it('lehnt Zeichen ab, die in der Mount-Option oder im Stack Ärger machen würden', () => {
        for (const adresse of ['nas/erp,rw', 'nas/er"p', 'nas/$HOME', 'nas/erp`x`']) expect(smbVariablen(adresse, 'max', 'vers=3.0').fehler).toContain('keine Kommas');
        for (const benutzer of ['ma,x', 'ma=x', 'ma x', 'ma"x']) expect(smbVariablen('nas/erp', benutzer, 'vers=3.0').fehler).toContain('keine Kommas');
    });
});
