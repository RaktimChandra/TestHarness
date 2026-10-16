import { useEffect, useState } from "react";
import { Tile } from "./components";
import { a11yBlocking, latestRun, pct, shortDate, totalCoverage } from "./model";
import { DetectionMatrix, TiaPanel } from "./sections/Experiments";
import { FlakyLeaderboard, QuarantineList } from "./sections/Flakiness";
import { A11yPanel, CoveragePanel, MutationPanel } from "./sections/Quality";
import { HealingPanel, Sources, VisualPanel } from "./sections/Review";
import { FailureCauses, PassRate, Slowest } from "./sections/Trends";
import type { Dashboard } from "./types";

type Theme = "system" | "light" | "dark";

function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(() => {
    const t = typeof document === "undefined" ? undefined : document.documentElement.dataset.theme;
    return t === "light" || t === "dark" ? t : "system";
  });
  useEffect(() => {
    const root = document.documentElement;
    if (theme === "system") delete root.dataset.theme;
    else root.dataset.theme = theme;
    try {
      if (theme === "system") localStorage.removeItem("th-theme");
      else localStorage.setItem("th-theme", theme);
    } catch {
      /* storage unavailable: the choice lasts for this page view */
    }
  }, [theme]);
  const next: Record<Theme, Theme> = { system: "light", light: "dark", dark: "system" };
  return [theme, () => setTheme(next[theme])];
}

const SECTIONS: [string, string][] = [
  ["pass-rate", "Pass rate"], ["flaky", "Flakiness"], ["quarantine", "Quarantine"], ["slowest", "Slowest"],
  ["coverage", "Coverage"], ["mutation", "Mutation"], ["a11y", "Accessibility"], ["visual", "Visual"],
  ["matrix", "Defect detection"], ["tia", "Impact analysis"], ["healing", "Healing"], ["sources", "Sources"],
];

export function App({ d }: { d: Dashboard }) {
  const [theme, cycleTheme] = useTheme();
  const last = latestRun(d);
  const cov = totalCoverage(d.coverage);
  const awaiting = d.visual.length;
  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <h1>TestHarness quality</h1>
          <div className="meta">Generated {shortDate(d.generated)} · {d.historyResults} test results in history</div>
        </div>
        <div className="topbar-actions">
          <button className="btn" type="button" onClick={cycleTheme} aria-label={`Theme: ${theme}. Change theme`}>
            Theme: {theme}
          </button>
        </div>
      </header>
      <nav aria-label="Sections">
        <ul className="nav">{SECTIONS.map(([id, label]) => <li key={id}><a href={`#${id}`}>{label}</a></li>)}</ul>
      </nav>
      <section className="tiles" aria-label="Headline numbers">
        <Tile label="Last run pass rate" value={last && pct(last.passRate)}
          sub={last && `${last.passed} passed · ${last.failed} failed`} />
        <Tile label="Flaky tests" value={String(d.flaky.length)} sub="flipped on the same commit" />
        <Tile label="Quarantined" value={String(d.quarantine.tests.length)} sub="non-blocking suite" />
        <Tile label="Line coverage" value={cov && pct(cov.line)} sub={cov && `branch ${pct(cov.branch)}`} />
        <Tile label="Mutation score" value={d.mutation && pct(d.mutation.score)}
          sub={d.mutation && `${d.mutation.detected} of ${d.mutation.total} mutants`} />
        <Tile label="Blocking a11y issues" value={d.a11y.length === 0 ? undefined : String(a11yBlocking(d.a11y))}
          sub={d.a11y.length === 0 ? undefined : `${d.a11y.length} page scans`} />
        <Tile label="Visual changes to review" value={String(awaiting)} />
        <Tile label="Seeded bugs caught" value={d.faultSeeding &&
          `${d.faultSeeding.bugs.length - d.faultSeeding.escaped_all.length}/${d.faultSeeding.bugs.length}`}
          sub={d.faultSeeding && "by at least one suite"} />
      </section>
      <main className="grid">
        <PassRate d={d} />
        <FailureCauses d={d} />
        <FlakyLeaderboard d={d} />
        <QuarantineList d={d} />
        <Slowest d={d} />
        <CoveragePanel d={d} />
        <MutationPanel d={d} />
        <A11yPanel d={d} />
        <VisualPanel d={d} />
        <DetectionMatrix d={d} />
        <TiaPanel d={d} />
        <HealingPanel d={d} />
        <Sources d={d} />
      </main>
    </div>
  );
}
