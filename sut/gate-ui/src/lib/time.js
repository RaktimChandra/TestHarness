// Slot times arrive as ISO-8601 strings in the terminal's local time with its offset
// (e.g. 2026-10-20T10:00+05:30). They are shown exactly as the terminal sees them, not
// converted to the browser's zone, so a driver anywhere reads the gate's wall clock.
export function localTime(iso) {
  const m = /T(\d{2}):(\d{2})/.exec(iso ?? '');
  return m ? `${m[1]}:${m[2]}` : '';
}

export function localDate(iso) {
  const m = /^(\d{4}-\d{2}-\d{2})/.exec(iso ?? '');
  return m ? m[1] : '';
}

export function slotLabel(slot) {
  return `${localDate(slot.start)} ${localTime(slot.start)}–${localTime(slot.end)} · Lane ${slot.lane}`;
}

export function todayIso(now = new Date()) {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
