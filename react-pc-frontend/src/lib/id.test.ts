import { afterEach, describe, expect, it, vi } from 'vitest';
import { generateId } from './id';

/**
 * generateId() muss auch dann eine gültige, eindeutige ID liefern, wenn
 * crypto.randomUUID nicht existiert (unsicherer Kontext, z.B. HTTP im LAN
 * statt HTTPS/localhost — siehe docs/INSTALLATION.md).
 */
describe('generateId', () => {
    const originalRandomUUID = crypto.randomUUID;

    afterEach(() => {
        crypto.randomUUID = originalRandomUUID;
        vi.restoreAllMocks();
    });

    it('nutzt crypto.randomUUID, wenn verfügbar', () => {
        const spy = vi.fn(() => '11111111-1111-4111-8111-111111111111' as `${string}-${string}-${string}-${string}-${string}`);
        crypto.randomUUID = spy;

        expect(generateId()).toBe('11111111-1111-4111-8111-111111111111');
        expect(spy).toHaveBeenCalledOnce();
    });

    it('fällt auf einen manuellen UUID-v4-Ersatz zurück, wenn crypto.randomUUID fehlt', () => {
        // @ts-expect-error - simuliert einen unsicheren Kontext (kein HTTPS/localhost)
        crypto.randomUUID = undefined;

        const id = generateId();
        expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
    });

    it('erzeugt bei jedem Aufruf eine andere ID im Fallback', () => {
        // @ts-expect-error - simuliert einen unsicheren Kontext
        crypto.randomUUID = undefined;

        const ids = new Set(Array.from({ length: 50 }, () => generateId()));
        expect(ids.size).toBe(50);
    });
});
