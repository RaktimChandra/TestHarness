import { Empty, Panel, StatusBadge } from "../components";
import { pct, topCause } from "../model";
import type { Dashboard } from "../types";

export function FlakyLeaderboard({ d }: { d: Dashboard }) {
  const quarantined = new Set(d.quarantine.tests.map((t) => t.key));
  const policy = d.quarantine.policy;
  return (
    <Panel id="flaky" span={7} title="Flakiness leaderboard"
      subtitle={<>Score = flips ÷ same-commit transitions: a test that changes outcome without a code change.
        {policy && <> Quarantine policy: at least {policy.minRuns} runs and score ≥ {policy.minScore}.</>}</>}>
      {d.flaky.length === 0 ? <Empty>No test has flipped between pass and fail on the same commit.</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Test</th><th className="num">Score</th><th className="num">Flips</th><th className="num">Runs</th><th className="num">Pass rate</th><th>Main cause</th><th>State</th></tr></thead>
            <tbody>
              {d.flaky.map((s, i) => (
                <tr key={s.key + s.displayName}>
                  <td className="num">{i + 1}</td>
                  <td>{s.displayName}<div className="key">{s.key}</div></td>
                  <td className="num"><span className="inline-bar" aria-hidden="true"><span style={{ width: `${s.score * 100}%` }} /></span>{s.score.toFixed(2)}</td>
                  <td className="num">{s.flips}/{s.transitions}</td>
                  <td className="num">{s.runs}</td>
                  <td className="num">{pct(s.passes / Math.max(1, s.runs), 0)}</td>
                  <td>{topCause(s.failureCauses).toLowerCase()}</td>
                  <td>{quarantined.has(s.key)
                    ? <StatusBadge status="warning" label="quarantined" />
                    : <StatusBadge status="good" label="blocking" />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

export function QuarantineList({ d }: { d: Dashboard }) {
  const q = d.quarantine.tests;
  return (
    <Panel id="quarantine" span={5} title="Quarantine"
      subtitle="Tests that run in the non-blocking suite. Their failures are reported, never hidden, and do not fail the build."
      source={`Source: ${d.quarantineSource}`}>
      {q.length === 0 ? <Empty>Nothing is quarantined.</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Test</th><th className="num">Score</th><th className="num">Runs</th><th>Why</th></tr></thead>
            <tbody>
              {q.map((t) => (
                <tr key={t.key}>
                  <td className="mono" style={{ wordBreak: "break-all" }}>{t.key}</td>
                  <td className="num">{t.score.toFixed(2)}</td>
                  <td className="num">{t.runs}</td>
                  <td>{t.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
