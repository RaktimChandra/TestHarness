import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { validateContainerNumber, normalise } from '../lib/containerNumber.js';
import { slotLabel, todayIso } from '../lib/time.js';
import Alert from '../components/Alert.jsx';
import ConfirmDialog from '../components/ConfirmDialog.jsx';
import SlotPicker from '../components/SlotPicker.jsx';
import StatusBadge from '../components/StatusBadge.jsx';

const DIRECTIONS = [
  { value: 'DROP_OFF', label: 'Drop off a container' },
  { value: 'PICK_UP', label: 'Collect a container' },
];

function BookingForm({ onBooked }) {
  const [date, setDate] = useState(todayIso());
  const [slot, setSlot] = useState(null);
  const [container, setContainer] = useState('');
  const [touched, setTouched] = useState(false);
  const [direction, setDirection] = useState('DROP_OFF');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const containerError = touched ? validateContainerNumber(container) : null;
  const canSubmit = slot && !validateContainerNumber(container) && !busy;

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    setBusy(true);
    setError('');
    try {
      const booking = await api.createBooking(slot.id, normalise(container), direction);
      setContainer('');
      setTouched(false);
      setSlot(null);
      onBooked(booking);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="book-title">
      <h2 id="book-title">Book a gate slot</h2>
      <SlotPicker
        date={date}
        onDateChange={(d) => {
          setDate(d);
          setSlot(null);
        }}
        selectedId={slot?.id}
        onSelect={setSlot}
        prefix="slot"
      />
      <form onSubmit={submit} noValidate data-testid="book-form">
        <p data-testid="book-selected-slot" className="muted">
          {slot ? `Selected: ${slotLabel(slot)}` : 'Select a slot above.'}
        </p>
        <label className="field">
          <span>Container number</span>
          <input
            value={container}
            onChange={(e) => setContainer(e.target.value)}
            onBlur={() => setTouched(true)}
            placeholder="CSQU3054383"
            maxLength={11}
            aria-invalid={Boolean(containerError)}
            aria-describedby="container-help"
            data-testid="book-container"
          />
          <small id="container-help" className={containerError ? 'error-text' : 'muted'} data-testid="book-container-error">
            {containerError ?? 'ISO 6346: owner code, category, serial and check digit'}
          </small>
        </label>
        <label className="field">
          <span>Direction</span>
          <select value={direction} onChange={(e) => setDirection(e.target.value)} data-testid="book-direction">
            {DIRECTIONS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </select>
        </label>
        <Alert kind="error" testId="book-error">
          {error}
        </Alert>
        <button type="submit" className="btn btn-primary" disabled={!canSubmit} data-testid="book-submit">
          {busy ? 'Booking…' : 'Book slot'}
        </button>
      </form>
    </section>
  );
}

function ReschedulePanel({ booking, onDone, onClose }) {
  const [date, setDate] = useState(booking.slot.start.slice(0, 10));
  const [slot, setSlot] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    setBusy(true);
    setError('');
    try {
      onDone(await api.reschedule(booking.reference, slot.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="reschedule" data-testid="reschedule-panel">
      <h3>Move {booking.reference}</h3>
      <SlotPicker
        date={date}
        onDateChange={(d) => {
          setDate(d);
          setSlot(null);
        }}
        selectedId={slot?.id}
        onSelect={setSlot}
        prefix="reschedule"
        excludeId={booking.slot.id}
      />
      <Alert kind="error" testId="reschedule-error">
        {error}
      </Alert>
      <div className="row">
        <button type="button" className="btn btn-ghost" onClick={onClose} data-testid="reschedule-close">
          Close
        </button>
        <button
          type="button"
          className="btn btn-primary"
          disabled={!slot || busy}
          onClick={confirm}
          data-testid="reschedule-confirm"
        >
          Move booking
        </button>
      </div>
    </div>
  );
}

export default function DriverPage() {
  const [bookings, setBookings] = useState([]);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [rescheduling, setRescheduling] = useState(null);
  const [cancelling, setCancelling] = useState(null);

  const refresh = useCallback(() => {
    api
      .listBookings()
      .then(setBookings)
      .catch((e) => setError(e.message));
  }, []);

  useEffect(refresh, [refresh]);

  const doCancel = async () => {
    const ref = cancelling.reference;
    setCancelling(null);
    setError('');
    try {
      await api.cancel(ref);
      setNotice(`Booking ${ref} cancelled.`);
      refresh();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="stack">
      <h1>My gate appointments</h1>
      <Alert kind="success" testId="driver-notice">
        {notice}
      </Alert>
      <Alert kind="error" testId="driver-error">
        {error}
      </Alert>
      <BookingForm
        onBooked={(b) => {
          setNotice(`Booked ${b.reference} for ${slotLabel(b.slot)}.`);
          refresh();
        }}
      />
      <section className="card" aria-labelledby="mine-title">
        <h2 id="mine-title">My bookings</h2>
        <table className="table" data-testid="bookings-table">
          <caption className="sr-only">Your bookings</caption>
          <thead>
            <tr>
              <th scope="col">Reference</th>
              <th scope="col">Slot</th>
              <th scope="col">Container</th>
              <th scope="col">Status</th>
              <th scope="col">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {bookings.length === 0 && (
              <tr>
                <td colSpan="5" data-testid="bookings-empty">
                  You have no bookings yet.
                </td>
              </tr>
            )}
            {bookings.map((b) => (
              <tr key={b.reference} data-testid={`booking-row-${b.reference}`}>
                <td className="mono">{b.reference}</td>
                <td data-testid={`booking-slot-${b.reference}`}>{slotLabel(b.slot)}</td>
                <td className="mono">{b.containerNumber}</td>
                <td>
                  <StatusBadge status={b.status} testId={`booking-status-${b.reference}`} />
                </td>
                <td className="actions">
                  {b.status === 'BOOKED' && (
                    <>
                      <button
                        type="button"
                        className="btn btn-small"
                        data-testid={`booking-reschedule-${b.reference}`}
                        onClick={() => setRescheduling(b)}
                      >
                        Reschedule
                      </button>
                      <button
                        type="button"
                        className="btn btn-small btn-danger-outline"
                        data-testid={`booking-cancel-${b.reference}`}
                        onClick={() => setCancelling(b)}
                      >
                        Cancel
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rescheduling && (
          <ReschedulePanel
            booking={rescheduling}
            onClose={() => setRescheduling(null)}
            onDone={(b) => {
              setRescheduling(null);
              setNotice(`Booking ${b.reference} moved to ${slotLabel(b.slot)}.`);
              refresh();
            }}
          />
        )}
      </section>
      <ConfirmDialog
        open={Boolean(cancelling)}
        title="Cancel this booking?"
        message={cancelling ? `${cancelling.reference} for ${slotLabel(cancelling.slot)} will be released.` : ''}
        confirmLabel="Cancel booking"
        onConfirm={doCancel}
        onCancel={() => setCancelling(null)}
      />
    </div>
  );
}
