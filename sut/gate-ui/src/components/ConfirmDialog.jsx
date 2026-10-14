import { useEffect, useRef } from 'react';

export default function ConfirmDialog({ open, title, message, confirmLabel, onConfirm, onCancel }) {
  const confirmRef = useRef(null);
  useEffect(() => {
    if (open) confirmRef.current?.focus();
  }, [open]);
  if (!open) return null;
  return (
    <div className="dialog-backdrop">
      <div
        className="dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        data-testid="confirm-dialog"
        onKeyDown={(e) => e.key === 'Escape' && onCancel()}
      >
        <h2 id="confirm-title">{title}</h2>
        <p id="confirm-message">{message}</p>
        <div className="dialog-actions">
          <button type="button" className="btn btn-ghost" data-testid="confirm-no" onClick={onCancel}>
            Keep it
          </button>
          <button type="button" className="btn btn-danger" ref={confirmRef} data-testid="confirm-yes" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
