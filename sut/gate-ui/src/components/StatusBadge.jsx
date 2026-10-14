const LABELS = { BOOKED: 'Booked', CANCELLED: 'Cancelled', CHECKED_IN: 'Checked in' };

export default function StatusBadge({ status, testId }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`} data-testid={testId} data-status={status}>
      {LABELS[status] ?? status}
    </span>
  );
}
