import { useRef, useState } from "react";
import { Tooltip, tipAt, type TipState } from "../components";

export interface LinePoint {
  label: string;
  value: number;
  rows: [string, string][];
}

interface Props {
  points: LinePoint[];
  /** y domain is fixed: a pass rate always runs 0..1, so a small dip does not look like a collapse. */
  yMax?: number;
  format: (v: number) => string;
  ariaLabel: string;
}

const W = 720;
const H = 240;
const M = { top: 12, right: 16, bottom: 28, left: 44 };

/** One series, 2px line, hairline grid, crosshair + tooltip on hover. */
export function LineChart({ points, yMax = 1, format, ariaLabel }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const [hover, setHover] = useState<number | null>(null);
  const [tip, setTip] = useState<TipState | null>(null);
  const iw = W - M.left - M.right;
  const ih = H - M.top - M.bottom;
  const n = points.length;
  const x = (i: number) => M.left + (n <= 1 ? iw / 2 : (i / (n - 1)) * iw);
  const y = (v: number) => M.top + ih - (v / yMax) * ih;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((t) => t * yMax);
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join("");
  // Tick labels: at most ~6, never closer than MIN_GAP, and the last point always labelled.
  const MIN_GAP = 150; // the first label is start-anchored, so it needs the widest gap
  const labelled = new Set<number>();
  if (n > 0) {
    const every = Math.max(1, Math.ceil(n / 6));
    let lastX = -Infinity;
    for (let i = 0; i < n; i += every) {
      if (x(i) - lastX >= MIN_GAP && x(n - 1) - x(i) >= MIN_GAP) {
        labelled.add(i);
        lastX = x(i);
      }
    }
    labelled.add(n - 1);
  }
  const markers = n <= 40;

  function onMove(e: React.MouseEvent<SVGRectElement>) {
    const svg = e.currentTarget.ownerSVGElement!;
    const box = svg.getBoundingClientRect();
    const sx = ((e.clientX - box.left) / box.width) * W;
    const i = n <= 1 ? 0 : Math.max(0, Math.min(n - 1, Math.round(((sx - M.left) / iw) * (n - 1))));
    const p = points[i];
    if (!p) return;
    setHover(i);
    setTip(tipAt(e, ref.current, p.label, [["Pass rate", format(p.value)], ...p.rows]));
  }

  return (
    <div className="chart" ref={ref}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={ariaLabel}>
        {ticks.map((t) => (
          <g key={t}>
            <line className={t === 0 ? "baseline" : "gridline"} x1={M.left} x2={W - M.right} y1={y(t)} y2={y(t)} />
            <text x={M.left - 8} y={y(t) + 4} textAnchor="end">{format(t)}</text>
          </g>
        ))}
        {points.map((p, i) =>
          labelled.has(i) ? (
            <text key={i} x={x(i)} y={H - 8} textAnchor={i === 0 && n > 1 ? "start" : i === n - 1 && n > 1 ? "end" : "middle"}>
              {p.label}
            </text>
          ) : null,
        )}
        {hover !== null && (
          <line x1={x(hover)} x2={x(hover)} y1={M.top} y2={M.top + ih} stroke="var(--baseline)" strokeWidth="1" />
        )}
        <path d={path} fill="none" stroke="var(--series-1)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {points.map((p, i) =>
          markers || hover === i ? (
            <circle key={i} cx={x(i)} cy={y(p.value)} r={hover === i ? 5 : 4} fill="var(--series-1)"
              stroke="var(--surface-1)" strokeWidth="2" />
          ) : null,
        )}
        <rect x={M.left - 8} y={M.top} width={iw + 16} height={ih} fill="transparent"
          onMouseMove={onMove} onMouseLeave={() => { setHover(null); setTip(null); }} />
      </svg>
      <Tooltip tip={tip} />
    </div>
  );
}
