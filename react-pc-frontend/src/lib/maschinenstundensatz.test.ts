import { describe, expect, it } from 'vitest';
import { maschinenstundensatz } from './maschinenstundensatz';

describe('maschinenstundensatz', () => {
    it('rechnet Abschreibung, Wartung, Energie und Raum auf die Stunden im Jahr um', () => {
        // 24.000 € über 8 Jahre = 3.000 € + 1.200 + 1.800 + 600 = 6.600 € im Jahr bei 1.200 Std. = 5,50 € je Stunde
        expect(maschinenstundensatz({ anschaffung: 24000, nutzungsdauerJahre: 8, auslastungStundenJahr: 1200, wartungJahr: 1200, energieJahr: 1800, raumJahr: 600 })).toBe(5.5);
    });

    it('rundet auf Cent', () => {
        expect(maschinenstundensatz({ anschaffung: 10000, nutzungsdauerJahre: 7, auslastungStundenJahr: 900, wartungJahr: 0, energieJahr: 0, raumJahr: 0 })).toBe(1.59);
    });

    it('ohne Nutzungsdauer oder Auslastung lässt sich nicht rechnen', () => {
        expect(maschinenstundensatz({ anschaffung: 1000, nutzungsdauerJahre: 0, auslastungStundenJahr: 100, wartungJahr: 0, energieJahr: 0, raumJahr: 0 })).toBeNull();
        expect(maschinenstundensatz({ anschaffung: 1000, nutzungsdauerJahre: 5, auslastungStundenJahr: 0, wartungJahr: 0, energieJahr: 0, raumJahr: 0 })).toBeNull();
    });
});
