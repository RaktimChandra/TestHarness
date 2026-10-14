import { describe, expect, it } from 'vitest';
import { checkDigit, normalise, validateContainerNumber } from './containerNumber.js';

describe('ISO 6346 validation (UI)', () => {
  it.each(['CSQU3054383', 'MSCU1000070', 'MAEU1000002', ' csqu3054383 '])('accepts %s', (v) => {
    expect(validateContainerNumber(v)).toBeNull();
  });

  it('computes the remainder-10 case as 0', () => {
    expect(checkDigit('MSCU100007')).toBe(0);
  });

  it.each([
    ['', 'Enter a container number'],
    ['CSQX3054383', 'Use 3 letters'],
    ['CSQU305438', 'Use 3 letters'],
    ['CSQU3054384', 'Check digit should be 3'],
  ])('rejects %j', (v, msg) => {
    expect(validateContainerNumber(v)).toContain(msg);
  });

  it('normalises null and whitespace', () => {
    expect(normalise(null)).toBe('');
    expect(normalise('  abc ')).toBe('ABC');
  });
});
