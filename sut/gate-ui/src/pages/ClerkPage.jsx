import { useState } from 'react';
import { api } from '../api/client.js';
import { slotLabel, todayIso } from '../lib/time.js';
import Alert from '../components/Alert.jsx';
import StatusBadge from '../components/StatusBadge.jsx';

const STATUSES = ['', 'BOOKED', 'CHECKED_IN', 'CANCELLED'];

export default function ClerkPage() {
  const [filters, setFilters] = useState({ containerNumber: '', reference: '', status: '', date: todayIso() });
  const [results, setResults] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const set = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  const search = async (e) => {
    e?.preventDefault();
    setError('');
    try {
      setResults(await api.listBookings(filters));
    } catch (err) {
      setError(err.message);
    }
  };

  const checkIn = async (ref) => {
    setError('');
    setNotice('');
    try {
      const b = await api.checkIn(ref);
      setNotice(`${b.reference} checked in.`);
      setResults((rs) => rs.map((r) => (r.reference === b.reference ? b : r)));
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="stack">
      <h1>Gate check-in</h1>
      <section className="card" aria-labelledby="search-title">
        <h2 id="search-title">Find an appointment</h2>
        <form className="grid-form" onSubmit={search} data-testid="search-form">
          <label className="field">
            <span>Container number</span>
            <input value={filters.containerNumber} onChange={set('containerNumber')} data-testid="search-container" />
          </label>
          <label className="field">
            <span>Reference</span>
            <input value={filters.reference} onChange={set('reference')} data-testid="search-reference" />
          </label>
          <label className="field">
            <span>Status</span>
            <select value={filters.status} onChange={set('status')} data-testid="search-status">
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s === '' ? 'Any' : s.replace('_', ' ').toLowerCase()}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Date</span>
            <input type="date" value={filters.date} onChange={set('date')} data-testid="search-date" />
          </label>
          <button type="submit" className="btn btn-primary" data-testid="search-submit">
            Search
          </button>
        </form>
      </section>
      <Alert kind="success" testId="clerk-notice">
        {notice}
      </Alert>
      <Alert kind="error" testId="clerk-error">
        {error}
      </Alert>
      {results && (
        <section className="card" aria-labelledby="results-title">
          <h2 id="results-title">
            Results <span data-testid="results-count">({results.length})</span>
          </h2>
          <table className="table" data-testid="results-table">
            <caption className="sr-only">Matching appointments</caption>
            <thead>
              <tr>
                <th scope="col">Reference</th>
                <th scope="col">Driver</th>
                <th scope="col">Slot</th>
                <th scope="col">Container</th>
                <th scope="col">Direction</th>
                <th scope="col">Status</th>
                <th scope="col">
                  <span className="sr-only">Action</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {results.length === 0 && (
                <tr>
                  <td colSpan="7" data-testid="results-empty">
                    No appointments match.
                  </td>
                </tr>
              )}
              {results.map((b) => (
                <tr key={b.reference} data-testid={`result-row-${b.reference}`}>
                  <td className="mono">{b.reference}</td>
                  <td>{b.driver.displayName}</td>
                  <td>{slotLabel(b.slot)}</td>
                  <td className="mono">{b.containerNumber}</td>
                  <td>{b.direction === 'DROP_OFF' ? 'Drop off' : 'Pick up'}</td>
                  <td>
                    <StatusBadge status={b.status} testId={`result-status-${b.reference}`} />
                  </td>
                  <td>
                    {b.status === 'BOOKED' && (
                      <button
                        type="button"
                        className="btn btn-small btn-primary"
                        data-testid={`checkin-${b.reference}`}
                        onClick={() => checkIn(b.reference)}
                      >
                        Check in
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
