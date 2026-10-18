import { render } from '@testing-library/react';
import { describe, it, vi } from 'vitest';
import { PlanungSection } from './PlanungSection';

describe('PlanungSection', () => {
    it('rendert ohne Fehler', () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ rhythmus: [] }) }));
        render(<PlanungSection />);
        vi.unstubAllGlobals();
    });
});
