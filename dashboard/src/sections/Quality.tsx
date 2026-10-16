import { BarList } from "../charts/BarList";
import { Empty, Legend, Panel, StatusBadge } from "../components";
import { a11yStatus, pct, ratio } from "../model";
import type { Dashboard } from "../types";

export function CoveragePanel({ d }: { d: Dashboard }) {
  const rows = [...d.coverage].sort((a, b) => ratio(b.lineCovered, b.lineTotal) - ratio(a.lineCovered, a.lineTotal));
  return (
    <Panel id="coverage" span={6} title="Coverage by module"
      subtitle="JaCoCo line and branch coverage from the last verify of each module."
      table={rows.length > 0 && (
        <table>
          <thead><tr><th>Module</th><th className="num">Lines</th><th className="num">Line %</th><th className="num">Branches</th><th className="num">Branch %</th></tr></thead>
          <tbody>{rows.map((c) => (
            <tr key={c.module}><td className="mono">{c.module}</td>
              <td className="num">{c.lineCovered}/{c.lineTotal}</td><td className="num">{pct(ratio(c.lineCovered, c.lineTotal))}</td>
              <td className="num">{c.branchCovered}/{c.branchTotal}</td><td className="num">{c.branchTotal === 0 ? "—" : pct(ratio(c.branchCovered, c.branchTotal))}</td></tr>))}
          </tbody>
        </table>
      )}>
      {rows.length === 0 ? <Empty>No JaCoCo reports found. Run <code>./mvnw verify</code>.</Empty> : (
        <>
          <Legend items={[{ label: "Line", color: "var(--series-1)" }, { label: "Branch", color: "var(--series-2)" }]} />
          <BarList max={1} colors={["var(--series-1)", "var(--series-2)"]}
            data={rows.map((c) => {
              const line = ratio(c.lineCovered, c.lineTotal);
              const branch = ratio(c.branchCovered, c.branchTotal);
              return {
                label: c.module,
                values: c.branchTotal === 0 ? [line] : [line, branch],
                display: c.branchTotal === 0 ? `${pct(line, 0)} line` : `${pct(line, 0)} line · ${pct(branch, 0)} branch`,
                rows: [["Lines", `${c.lineCovered}/${c.lineTotal} (${pct(line)})`],
                  ["Branches", c.branchTotal === 0 ? "none" : `${c.branchCovered}/${c.branchTotal} (${pct(branch)})`]],
              };
            })} />
        </>
      )}
    </Panel>
  );
}

export function MutationPanel({ d }: { d: Dashboard }) {
  const m = d.mutation;
  return (
    <Panel id="mutation" span={5} title="Mutation score"
      subtitle="PIT (STRONGER mutators). A mutant is detected when a test fails (killed) or the run times out."
      source={m && `Module: ${m.module}`}>
      {!m ? <Empty>No PIT report found. Run <code>./mvnw -Pmutation verify -pl sut/gate-domain</code>.</Empty> : (
        <>
          <div className="stat-inline">
            <div><div className="v">{pct(m.score)}</div><div className="l">score ({m.detected} of {m.total})</div></div>
            {Object.entries(m.byStatus).map(([s, n]) => (
              <div key={s}><div className="v">{n}</div><div className="l">{s.toLowerCase().replace(/_/g, " ")}</div></div>
            ))}
          </div>
          {m.survivors.length === 0 ? (
            <StatusBadge status="good" label="No surviving mutants" />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Status</th><th>Where</th><th>Mutation</th></tr></thead>
                <tbody>{m.survivors.map((s, i) => (
                  <tr key={i}><td><StatusBadge status="serious" label={s.status.toLowerCase().replace(/_/g, " ")} /></td>
                    <td className="mono">{s.class.split(".").pop()}.{s.method}:{s.line}</td><td>{s.description}</td></tr>))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </Panel>
  );
}

const LABEL = { good: "clean", warning: "minor issues", serious: "serious", critical: "critical" } as const;

export function A11yPanel({ d }: { d: Dashboard }) {
  return (
    <Panel id="a11y" span={7} title="Accessibility (axe-core)"
      subtitle="Violations per page state and browser. Critical and serious fail the gate; moderate and minor are reported.">
      {d.a11y.length === 0 ? <Empty>No axe reports found. Run the UI suite (<code>th run --tag a11y</code>).</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Page</th><th>Browser</th><th>Status</th><th className="num">Critical</th><th className="num">Serious</th><th className="num">Moderate</th><th className="num">Minor</th><th>Rules</th></tr></thead>
            <tbody>{d.a11y.map((p) => {
              const s = a11yStatus(p);
              return (
                <tr key={p.page + p.browser}>
                  <td>{p.page}</td><td>{p.browser}</td><td><StatusBadge status={s} label={LABEL[s]} /></td>
                  <td className="num">{p.critical}</td><td className="num">{p.serious}</td>
                  <td className="num">{p.moderate}</td><td className="num">{p.minor}</td>
                  <td>{p.rules.length === 0 ? "—" : p.rules.map((r, i) => (
                    <span key={r.rule}>{i > 0 && ", "}<a href={r.helpUrl} target="_blank" rel="noreferrer" title={`${r.help} (${r.nodes} nodes)`}>{r.rule}</a> ({r.impact})</span>))}</td>
                </tr>
              );
            })}</tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
