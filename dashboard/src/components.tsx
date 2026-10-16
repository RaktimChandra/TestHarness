import { useState, type ReactNode } from "react";
import type { Status } from "./model";

/** Status is never colour alone: each badge has an icon shape and a word. */
const ICON: Record<Status, ReactNode> = {
  good: <path d="M3.5 8.2l2.8 2.8L12.5 4.8" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />,
  warning: <path d="M8 2.2l6 11H2z" strokeWidth="0" />,
  serious: <rect x="2.5" y="2.5" width="11" height="11" rx="2" strokeWidth="0" />,
  critical: <path d="M4.5 4.5l7 7M11.5 4.5l-7 7" fill="none" strokeWidth="2.2" strokeLinecap="round" />,
};

export function StatusBadge({ status, label }: { status: Status; label: string }) {
  const filled = status === "warning" || status === "serious";
  return (
    <span className="badge">
      <svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true"
        style={{ stroke: `var(--${status})`, fill: filled ? `var(--${status})` : "none" }}>
        {ICON[status]}
      </svg>
      {label}
    </span>
  );
}

export function Tile({ label, value, sub }: { label: string; value?: string; sub?: string }) {
  return (
    <div className="tile">
      <div className="label">{label}</div>
      {value === undefined ? <div className="value na">no data</div> : <div className="value">{value}</div>}
      {sub && <div className="sub">{sub}</div>}
    </div>
  );
}

interface PanelProps {
  id: string;
  title: string;
  subtitle?: ReactNode;
  source?: ReactNode;
  span?: 5 | 6 | 7 | 12;
  /** A chart panel offers the same data as a table; the toggle is part of the accessibility pass. */
  table?: ReactNode;
  children: ReactNode;
}

export function Panel({ id, title, subtitle, source, span = 12, table, children }: PanelProps) {
  const [asTable, setAsTable] = useState(false);
  return (
    <section className={`panel span-${span}`} id={id} aria-labelledby={`${id}-h`}>
      <div className="panel-head">
        <div>
          <h2 id={`${id}-h`}>{title}</h2>
          {subtitle && <div className="subtitle">{subtitle}</div>}
        </div>
        {table && (
          <button className="btn" type="button" aria-pressed={asTable} onClick={() => setAsTable(!asTable)}>
            {asTable ? "Show chart" : "Show table"}
          </button>
        )}
      </div>
      {asTable && table ? <div className="table-wrap">{table}</div> : children}
      {source && <div className="source">{source}</div>}
    </section>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div className="legend" role="list">
      {items.map((i) => (
        <span key={i.label} role="listitem">
          <span className="sw" style={{ background: i.color }} />
          {i.label}
        </span>
      ))}
    </div>
  );
}

export interface TipState {
  x: number;
  y: number;
  title: string;
  rows: [string, string][];
}

export function Tooltip({ tip }: { tip: TipState | null }) {
  if (!tip) return null;
  return (
    <div className="tooltip" role="status" style={{ left: tip.x, top: tip.y }}>
      <div className="t-title">{tip.title}</div>
      {tip.rows.map(([k, v]) => (
        <div className="t-row" key={k}>
          <span>{k}</span>
          <b>{v}</b>
        </div>
      ))}
    </div>
  );
}

/** Places a tooltip next to the pointer inside a positioned container, flipping at the right edge. */
export function tipAt(e: React.MouseEvent, container: HTMLElement | null, title: string, rows: [string, string][]): TipState {
  const box = container?.getBoundingClientRect();
  const x = box ? e.clientX - box.left : 0;
  const y = box ? e.clientY - box.top : 0;
  const width = box?.width ?? 600;
  return { x: x > width - 220 ? x - 230 : x + 14, y: y + 14, title, rows };
}
