import type { A11yPage, Coverage, Dashboard, FaultSeeding, Tia } from "./types";

/** Pure derivations from the dashboard document. Everything shown is computed here or read as is. */

export type Status = "good" | "warning" | "serious" | "critical";

export const pct = (n: number, digits = 1): string => `${(n * 100).toFixed(digits)}%`;

export const ratio = (covered: number, total: number): number => (total === 0 ? 0 : covered / total);

export function totalCoverage(rows: Coverage[]): { line: number; branch: number } | undefined {
  if (rows.length === 0) return undefined;
  const sum = (f: (c: Coverage) => number) => rows.reduce((a, c) => a + f(c), 0);
  return {
    line: ratio(sum((c) => c.lineCovered), sum((c) => c.lineTotal)),
    branch: ratio(sum((c) => c.branchCovered), sum((c) => c.branchTotal)),
  };
}

/** The worst axe impact on a page decides its status; critical and serious fail the gate. */
export function a11yStatus(p: A11yPage): Status {
  if (p.critical > 0) return "critical";
  if (p.serious > 0) return "serious";
  if (p.moderate + p.minor > 0) return "warning";
  return "good";
}

export function a11yBlocking(pages: A11yPage[]): number {
  return pages.reduce((a, p) => a + p.critical + p.serious, 0);
}

export interface MatrixCell {
  bug: string;
  suite: string;
  status: "caught" | "escaped" | "not-run";
  failed: number;
  tests: number;
}

export function matrixCells(fs: FaultSeeding): MatrixCell[][] {
  return fs.bugs.map((bug) =>
    fs.suites.map((suite) => {
      const o = fs.matrix[bug]?.[suite];
      if (!o) return { bug, suite, status: "not-run", failed: 0, tests: 0 };
      return { bug, suite, status: o.status === "caught" ? "caught" : "escaped", failed: o.failed, tests: o.tests };
    }),
  );
}

/** Shade for a caught cell: more failing tests, darker step of the one sequential (blue) ramp. */
export function shadeStep(failed: number, maxFailed: number): number {
  if (failed <= 0 || maxFailed <= 0) return 0;
  const steps = [150, 250, 350, 450];
  const i = Math.min(steps.length - 1, Math.floor((Math.log1p(failed) / Math.log1p(maxFailed)) * (steps.length - 1)));
  return steps[i] ?? 150;
}

export interface TiaRow {
  bug: string;
  file: string;
  selectedTests: number;
  fullTests: number;
  selectedSeconds: number;
  fullSeconds: number;
  saved: number;
  selectedCaught: boolean;
  fullCaught: boolean;
}

export function tiaRows(tia: Tia): TiaRow[] {
  return Object.entries(tia.runs)
    .filter(([, r]) => r.selected && r.full)
    .map(([bug, r]) => {
      const s = r.selected!;
      const f = r.full!;
      return {
        bug,
        file: r.changed_file.split("/").pop() ?? r.changed_file,
        selectedTests: s.tests,
        fullTests: f.tests,
        selectedSeconds: s.maven_seconds,
        fullSeconds: f.maven_seconds,
        saved: f.maven_seconds === 0 ? 0 : 1 - s.maven_seconds / f.maven_seconds,
        selectedCaught: s.caught,
        fullCaught: f.caught,
      };
    });
}

export function latestRun(d: Dashboard) {
  return d.runs.length === 0 ? undefined : d.runs[d.runs.length - 1];
}

export const shortDate = (iso: string): string => {
  const t = new Date(iso);
  return Number.isNaN(t.getTime())
    ? iso
    : t.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
};

export const seconds = (ms: number): string => (ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms} ms`);

export const topCause = (causes: Record<string, number>): string => {
  const e = Object.entries(causes).sort((a, b) => b[1] - a[1])[0];
  return e ? e[0] : "—";
};

/** "Class#method" or "features/x.feature:12" to something readable in a narrow column. */
export function shortKey(key: string): string {
  const hash = key.indexOf("#");
  if (hash > 0) return `${key.slice(key.lastIndexOf(".", hash) + 1)}`;
  return key.split("/").pop() ?? key;
}

export const humanBug = (b: string): string => b.toLowerCase().replace(/_/g, " ");
