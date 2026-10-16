import { useRef, useState } from "react";
import { Tooltip, tipAt, type TipState } from "../components";

export interface BarDatum {
  label: string;
  title?: string;
  /** One value per series; series colours come from `colors` in fixed slot order. */
  values: number[];
  display: string;
  rows: [string, string][];
}

interface Props {
  data: BarDatum[];
  max: number;
  colors: string[];
}

/**
 * Horizontal bars: long test and module names stay readable, bars are anchored at the
 * baseline with a 4px rounded data end, 2px gap between a row's series.
 */
export function BarList({ data, max, colors }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const [tip, setTip] = useState<TipState | null>(null);
  return (
    <div className="chart" ref={ref}>
      <div className="bars">
        {data.map((d) => (
          <div className="bar-row bar-hit" key={d.label}
            onMouseMove={(e) => setTip(tipAt(e, ref.current, d.title ?? d.label, d.rows))}
            onMouseLeave={() => setTip(null)}>
            <span className="name" title={d.title ?? d.label}>{d.label}</span>
            <span className="val">{d.display}</span>
            <span className="track" aria-hidden="true">
              {d.values.map((v, i) => (
                <span key={i} className="bar"
                  style={{ width: `${max === 0 ? 0 : Math.max(0.4, (v / max) * 100)}%`, background: colors[i] }} />
              ))}
            </span>
          </div>
        ))}
      </div>
      <Tooltip tip={tip} />
    </div>
  );
}
