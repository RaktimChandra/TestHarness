import { BarList } from "../charts/BarList";
import { Empty, Legend, Panel, StatusBadge } from "../components";
import { humanBug, matrixCells, pct, shadeStep, tiaRows } from "../model";
import type { Dashboard } from "../types";

export function DetectionMatrix({ d }: { d: Dashboard }) {
  const fs = d.faultSeeding;
  if (!fs) {
    return (
      <Panel id="matrix" title="Defect detection matrix">
        <Empty>No fault-seeding results. Run the <code>fault-seeding</code> workflow, or see experiments/fault-seeding.</Empty>
      </Panel>
    );
  }
  const cells = matrixCells(fs);
  const maxFailed = Math.max(0, ...cells.flat().map((c) => c.failed));
  return (
    <Panel id="matrix" title="Defect detection matrix"
      subtitle="Each row is one deliberately seeded bug, each column one suite run against it. A cell is caught when at least one test in that suite failed; the number is how many failed. Darker means more failing tests."
      source="Source: experiments/fault-seeding/fault-seeding.json (one CI run per bug and suite)">
      <div className="table-wrap">
        <table className="matrix">
          <thead><tr><th>Seeded bug</th>{fs.suites.map((s) => <th key={s} className="num">{s}</th>)}<th>Caught by</th></tr></thead>
          <tbody>
            {cells.map((row, r) => {
              const bug = fs.bugs[r]!;
              const by = fs.caught_by[bug] ?? [];
              return (
                <tr key={bug}>
                  <th className="row" scope="row">{humanBug(bug)}</th>
                  {row.map((c) => {
                    const step = c.status === "caught" ? shadeStep(c.failed, maxFailed) : 0;
                    const style = step === 0 ? { background: "var(--seq-0)" }
                      : { background: `var(--seq-${step})`, color: step === 450 ? "var(--seq-ink-450)" : "var(--text-primary)" };
                    return (
                      <td key={c.suite} className={`cell ${c.status}`} style={style}
                        title={`${humanBug(bug)} × ${c.suite}: ${c.status}${c.status === "caught" ? `, ${c.failed} of ${c.tests} tests failed` : ""}`}>
                        {c.status === "caught" ? `${c.failed}/${c.tests}` : c.status === "escaped" ? "—" : "n/a"}
                      </td>
                    );
                  })}
                  <td>{by.length === 0 ? <StatusBadge status="critical" label="escaped every suite" /> : <StatusBadge status="good" label={`${by.length} suite${by.length > 1 ? "s" : ""}`} />}</td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr><td>Detection rate</td>{fs.suites.map((s) => <td key={s} className="num">{pct(fs.detection_rate_per_suite[s] ?? 0, 0)}</td>)}<td /></tr>
          </tfoot>
        </table>
      </div>
      <div className="legend" style={{ marginTop: 10 }}>
        <span>Cell: failed/total tests in the suite · “—” = suite passed, bug escaped it</span>
        <span>{fs.escaped_all.length === 0 ? "No seeded bug escaped every suite." : `Escaped every suite: ${fs.escaped_all.map(humanBug).join(", ")}`}</span>
      </div>
    </Panel>
  );
}

export function TiaPanel({ d }: { d: Dashboard }) {
  const tia = d.tia;
  const rows = tia ? tiaRows(tia) : [];
  const max = Math.max(0, ...rows.map((r) => r.fullSeconds));
  return (
    <Panel id="tia" title="Test impact analysis"
      subtitle={<>For each seeded bug, only the tests whose recorded JaCoCo coverage touches the changed file were run, then the full API, UI and BDD suites for comparison. Times are whole Maven runs, including start-up.
        {tia?.map && <> Map: {tia.map.tests} tests × {tia.map.files} production files.</>}</>}
      source="Source: experiments/tia/tia-experiment.json (tools/tia_experiment.py)"
      table={rows.length > 0 && (
        <table>
          <thead><tr><th>Seeded bug</th><th>Changed file</th><th className="num">Selected tests</th><th className="num">Full tests</th><th className="num">Selected time</th><th className="num">Full time</th><th className="num">Saved</th><th>Selected run</th></tr></thead>
          <tbody>{rows.map((r) => (
            <tr key={r.bug}><td>{humanBug(r.bug)}</td><td className="mono">{r.file}</td>
              <td className="num">{r.selectedTests}</td><td className="num">{r.fullTests}</td>
              <td className="num">{r.selectedSeconds.toFixed(1)} s</td><td className="num">{r.fullSeconds.toFixed(1)} s</td>
              <td className="num">{pct(r.saved, 0)}</td>
              <td>{r.selectedCaught ? <StatusBadge status="good" label="caught" /> : <StatusBadge status="critical" label={r.fullCaught ? "missed (full caught)" : "missed"} />}</td></tr>))}
          </tbody>
        </table>
      )}>
      {rows.length === 0 ? <Empty>No TIA experiment results. Run <code>python tools/tia_experiment.py</code>.</Empty> : (
        <>
          <Legend items={[{ label: "Selected tests only", color: "var(--series-1)" }, { label: "Full suites", color: "var(--series-2)" }]} />
          <BarList max={max} colors={["var(--series-1)", "var(--series-2)"]}
            data={rows.map((r) => ({
              label: humanBug(r.bug),
              values: [r.selectedSeconds, r.fullSeconds],
              display: `${r.selectedSeconds.toFixed(0)} s vs ${r.fullSeconds.toFixed(0)} s · ${r.selectedCaught ? "caught" : "MISSED"}`,
              rows: [["Selected", `${r.selectedTests} tests, ${r.selectedSeconds.toFixed(1)} s`],
                ["Full", `${r.fullTests} tests, ${r.fullSeconds.toFixed(1)} s`],
                ["Time saved", pct(r.saved)],
                ["Bug caught by selection", r.selectedCaught ? "yes" : "no"]],
            }))} />
        </>
      )}
    </Panel>
  );
}
