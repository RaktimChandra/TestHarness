import { describe, expect, it } from 'vitest';
import { localDate, localTime, slotLabel, todayIso } from './time.js';

describe('terminal-time formatting', () => {
  it('shows the wall-clock time the API sent, not the browser zone', () => {
    expect(localTime('2026-10-20T10:00+05:30')).toBe('10:00');
    expect(localDate('2026-10-20T10:00+05:30')).toBe('2026-10-20');
  });

  it('builds a slot label', () => {
    expect(slotLabel({ start: '2026-10-20T10:00+05:30', end: '2026-10-20T10:30+05:30', lane: 'L1' })).toBe(
      '2026-10-20 10:00–10:30 · Lane L1',
    );
  });

  it('tolerates missing values', () => {
    expect(localTime(undefined)).toBe('');
    expect(localDate(null)).toBe('');
  });

  it('formats today as ISO date', () => {
    expect(todayIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
