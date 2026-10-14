// ISO 6346 container number validation (mirrors the server-side domain rule).
// Letters map to 10..38 skipping multiples of 11; each of the first ten characters is
// weighted by 2^position; the sum mod 11 (10 -> 0) is the check digit.
const SHAPE = /^[A-Z]{3}[UJZ][0-9]{7}$/;

const LETTER_VALUES = (() => {
  const values = {};
  let next = 10;
  for (const ch of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') {
    if (next % 11 === 0) next += 1;
    values[ch] = next;
    next += 1;
  }
  return values;
})();

export function normalise(raw) {
  return (raw ?? '').trim().toUpperCase();
}

export function checkDigit(prefix) {
  let sum = 0;
  for (let i = 0; i < 10; i += 1) {
    const ch = prefix[i];
    const v = /[0-9]/.test(ch) ? Number(ch) : LETTER_VALUES[ch];
    sum += v * 2 ** i;
  }
  return (sum % 11) % 10;
}

/** Returns null when valid, otherwise a message suitable for display. */
export function validateContainerNumber(raw) {
  const value = normalise(raw);
  if (value.length === 0) return 'Enter a container number';
  if (!SHAPE.test(value)) {
    return 'Use 3 letters, a category letter (U, J or Z), 6 digits and a check digit, e.g. CSQU3054383';
  }
  const expected = checkDigit(value);
  if (Number(value[10]) !== expected) {
    return `Check digit should be ${expected}`;
  }
  return null;
}
