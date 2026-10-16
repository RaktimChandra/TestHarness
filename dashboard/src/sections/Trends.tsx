import { BarList } from "../charts/BarList";
import { LineChart } from "../charts/LineChart";
import { Empty, Panel } from "../components";
import { pct, seconds, shortDate, shortKey } from "../model";
import type { Dashboard } from "../types";

const NO_HISTORY = (
  <Empty>
    No run history yet. Run any suite (history is on by default) or ingest CI results with{" "}
    <code>th history ingest</code>, then <code>th dashboard</code>.
  </Empty>
);

export function PassRate({ d }: { d: Dashboard }) {
  const points = d.runs.map((r) => ({
    label: shortDate(r.startedAt),
    value: r.passRate,
    rows: [
      ["Passed", String(r.passed)],
      ["Failed", String(r.failed)],
      ["Skipped", String(r.skipped)],
      ["Commit", r.commit.slice(0, 7)],
    ] as [string, string][],
  }));
  return (
    <Panel id="pass-rate" span={7} title="Pass rate over time"
      subtitle="Passed ÷ (passed + failed) per recorded run, oldest to newest. Skipped tests are excluded."
      source={`${d.runs.length} runs · ${d.historyResults} test results`}
      table={d.runs.length > 0 && (
        <table>
          <thead><tr><th>Run</th><th>Started</th><th>Suites</th><th className="num">Passed</th><th className="num">Failed</th><th className="num">Skipped</th><th className="num">Pass rate</th></tr></thead>
          <tbody>
            {d.runs.map((r) => (
              <tr key={r.runId}>
                <td className="mono">{r.runId}</td><td>{shortDate(r.startedAt)}</td><td>{r.suites}</td>
                <td className="num">{r.passed}</td><td className="num">{r.failed}</td><td className="num">{r.skipped}</td>
                <td className="num">{pct(r.passRate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}>
      {d.runs.length === 0 ? NO_HISTORY : (
        <LineChart points={points} format={(v) => pct(v, 0)} ariaLabel={`Pass rate across ${d.runs.length} runs`} />
      )}
    </Panel>
  );
}

export function FailureCauses({ d }: { d: Dashboard }) {
  const entries = Object.entries(d.failureCauses).sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((a, [, n]) => a + n, 0);
  const max = Math.max(0, ...entries.map(([, n]) => n));
  return (
    <Panel id="causes" span={5} title="Failure causes"
      subtitle="Every failure in the history, classified from its exception type and message."
      source="Classifier order: environment, locator, data, timeout, assertion; the rest are UNKNOWN.">
      {entries.length === 0 ? <Empty>No failures recorded.</Empty> : (
        <BarList max={max} colors={["var(--series-1)"]}
          data={entries.map(([cause, n]) => ({
            label: cause.toLowerCase(),
            values: [n],
            display: `${n} · ${pct(n / total, 0)}`,
            rows: [["Failures", String(n)], ["Share", pct(n / total)]],
          }))} />
      )}
    </Panel>
  );
}

export function Slowest({ d }: { d: Dashboard }) {
  const max = Math.max(0, ...d.slowest.map((s) => s.meanMs));
  return (
    <Panel id="slowest" span={6} title="Slowest tests"
      subtitle="Mean duration across every recorded run of the test."
      table={d.slowest.length > 0 && (
        <table>
          <thead><tr><th>Test</th><th className="num">Mean</th><th className="num">Runs</th></tr></thead>
          <tbody>{d.slowest.map((s) => (
            <tr key={s.key + s.displayName}><td>{s.displayName}<div className="key">{s.key}</div></td>
              <td className="num">{seconds(s.meanMs)}</td><td className="num">{s.runs}</td></tr>))}
          </tbody>
        </table>
      )}>
      {d.slowest.length === 0 ? NO_HISTORY : (
        <BarList max={max} colors={["var(--series-1)"]}
          data={d.slowest.map((s) => ({
            label: s.displayName === s.key ? shortKey(s.key) : s.displayName,
            title: `${s.displayName} — ${s.key}`,
            values: [s.meanMs],
            display: seconds(s.meanMs),
            rows: [["Mean", seconds(s.meanMs)], ["Runs", String(s.runs)]],
          }))} />
      )}
    </Panel>
  );
}
