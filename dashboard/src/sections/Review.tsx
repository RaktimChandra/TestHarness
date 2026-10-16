import { Empty, Panel, StatusBadge } from "../components";
import type { Dashboard } from "../types";

export function VisualPanel({ d }: { d: Dashboard }) {
  return (
    <Panel id="visual" title="Visual changes awaiting approval"
      subtitle={<>Mismatches show the diff (changed pixels highlighted) beside the actual capture. New pages show the candidate baseline. Approve by re-running with <code>-Dharness.visual.mode=update</code> and committing the baseline.</>}>
      {d.visual.length === 0 ? <Empty>Nothing to review: every visual check matched its baseline (or none ran).</Empty> : (
        <div className="gallery">
          {d.visual.map((v) => (
            <figure className="shot" key={v.platform + v.name + v.kind} style={{ margin: 0 }}>
              <div className={`imgs ${v.actual ? "two" : ""}`}>
                <a href={v.image} target="_blank" rel="noreferrer"><img src={v.image} alt={`${v.name} ${v.kind === "mismatch" ? "diff" : "candidate"}`} loading="lazy" /></a>
                {v.actual && <a href={v.actual} target="_blank" rel="noreferrer"><img src={v.actual} alt={`${v.name} actual`} loading="lazy" /></a>}
              </div>
              <figcaption>
                <div>{v.name}</div>
                <div className="cap">{v.platform} · {v.kind === "mismatch"
                  ? <StatusBadge status="serious" label="mismatch" /> : <StatusBadge status="warning" label="new baseline" />}</div>
              </figcaption>
            </figure>
          ))}
        </div>
      )}
    </Panel>
  );
}

export function HealingPanel({ d }: { d: Dashboard }) {
  return (
    <Panel id="healing" title="Self-healed locators"
      subtitle="A healed step passed through a fallback locator. It is labelled healed in Allure and listed here with the fix to apply; it is never silently green.">
      {d.healing.length === 0 ? <Empty>No locator needed healing.</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Test</th><th>Element</th><th>Broken locator</th><th>Found by</th><th className="num">Rank</th><th>Suggested fix</th></tr></thead>
            <tbody>{d.healing.map((h, i) => (
              <tr key={i}><td>{h.test}<div className="key">{h.module}</div></td><td>{h.target}</td>
                <td className="mono">{h.brokenLocator}</td><td>{h.usedLocator}</td><td className="num">{h.rank}</td><td>{h.suggestedFix}</td></tr>))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

export function Sources({ d }: { d: Dashboard }) {
  return (
    <Panel id="sources" title="Data sources"
      subtitle="Every number on this page is read from these files; nothing is estimated. Missing sources leave their panel empty.">
      {d.missing.length === 0 ? <StatusBadge status="good" label="All sources present" /> : (
        <ul className="notes">{d.missing.map((m) => <li key={m}>{m}</li>)}</ul>
      )}
    </Panel>
  );
}
