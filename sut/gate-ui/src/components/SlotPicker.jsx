import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { localTime } from '../lib/time.js';
import Alert from './Alert.jsx';

/** Lists the slots for a date; `prefix` keeps test ids unique when two pickers are on screen. */
export default function SlotPicker({ date, onDateChange, selectedId, onSelect, prefix = 'slot', excludeId }) {
  const [slots, setSlots] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!date) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .listSlots(date)
      .then((list) => !cancelled && setSlots(list))
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [date]);

  const visible = slots.filter((s) => s.id !== excludeId);

  return (
    <div className="slot-picker">
      <label className="field">
        <span>Date</span>
        <input
          type="date"
          value={date}
          onChange={(e) => onDateChange(e.target.value)}
          data-testid={`${prefix}-date`}
          required
        />
      </label>
      <Alert kind="error" testId={`${prefix}-error`}>
        {error}
      </Alert>
      {loading ? (
        <p role="status" data-testid={`${prefix}-loading`}>
          Loading slots…
        </p>
      ) : (
        <table className="table" data-testid={`${prefix}-table`}>
          <caption className="sr-only">Available slots</caption>
          <thead>
            <tr>
              <th scope="col">Time</th>
              <th scope="col">Lane</th>
              <th scope="col">Places left</th>
              <th scope="col">
                <span className="sr-only">Action</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 && (
              <tr>
                <td colSpan="4" data-testid={`${prefix}-empty`}>
                  No slots on this date.
                </td>
              </tr>
            )}
            {visible.map((s) => {
              const full = s.remaining === 0;
              const selected = s.id === selectedId;
              return (
                <tr key={s.id} data-testid={`${prefix}-row-${s.id}`} className={selected ? 'selected' : ''}>
                  <td data-testid={`${prefix}-time-${s.id}`}>
                    {localTime(s.start)}–{localTime(s.end)}
                  </td>
                  <td>{s.lane}</td>
                  <td data-testid={`${prefix}-remaining-${s.id}`}>{s.remaining}</td>
                  <td>
                    <button
                      type="button"
                      className="btn btn-small"
                      disabled={full}
                      aria-pressed={selected}
                      aria-label={`Select ${localTime(s.start)} lane ${s.lane}`}
                      data-testid={`${prefix}-select-${s.id}`}
                      onClick={() => onSelect(s)}
                    >
                      {full ? 'Full' : selected ? 'Selected' : 'Select'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
