import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { a11yBlocking, a11yStatus, matrixCells, pct, shadeStep, shortKey, tiaRows, totalCoverage } from "./model";
import { App } from "./App";
import type { A11yPage, Dashboard, FaultSeeding } from "./types";

const page = (o: Partial<A11yPage>): A11yPage =>
  ({ page: "p", browser: "chrome", url: "", critical: 0, serious: 0, moderate: 0, minor: 0, rules: [], ...o });

describe("model", () => {
  it("formats percentages", () => {
    expect(pct(0.9512)).toBe("95.1%");
    expect(pct(1, 0)).toBe("100%");
  });

  it("totals coverage by covered/total, not by averaging module percentages", () => {
    const c = totalCoverage([
      { module: "a", lineCovered: 9, lineTotal: 10, branchCovered: 0, branchTotal: 0 },
      { module: "b", lineCovered: 10, lineTotal: 90, branchCovered: 1, branchTotal: 4 },
    ])!;
    expect(c.line).toBeCloseTo(0.19);
    expect(c.branch).toBeCloseTo(0.25);
    expect(totalCoverage([])).toBeUndefined();
  });

  it("takes the worst axe impact as the page status", () => {
    expect(a11yStatus(page({}))).toBe("good");
    expect(a11yStatus(page({ minor: 2 }))).toBe("warning");
    expect(a11yStatus(page({ serious: 1, minor: 2 }))).toBe("serious");
    expect(a11yStatus(page({ critical: 1, serious: 1 }))).toBe("critical");
    expect(a11yBlocking([page({ critical: 1, serious: 2, minor: 5 }), page({ serious: 1 })])).toBe(4);
  });

  it("builds the detection matrix and marks unrun cells", () => {
    const fs: FaultSeeding = {
      bugs: ["A", "B"], suites: ["api", "ui"],
      matrix: { A: { api: { status: "caught", failed: 3, tests: 10 }, ui: { status: "escaped", failed: 0, tests: 5 } },
        B: { api: { status: "escaped", failed: 0, tests: 10 } } },
      detection_rate_per_suite: { api: 0.5, ui: 0 }, caught_by: { A: ["api"], B: [] }, escaped_all: ["B"],
    };
    const m = matrixCells(fs);
    expect(m[0]!.map((c) => c.status)).toEqual(["caught", "escaped"]);
    expect(m[1]![1]!.status).toBe("not-run");
  });

  it("shades only caught cells, darker with more failures", () => {
    expect(shadeStep(0, 10)).toBe(0);
    expect(shadeStep(1, 10)).toBeLessThan(shadeStep(10, 10));
    expect(shadeStep(10, 10)).toBe(450);
  });

  it("computes time saved per TIA run and drops unfinished runs", () => {
    const rows = tiaRows({ runs: {
      X: { changed_file: "a/b/C.java", selected: { exit: 1, maven_seconds: 50, tests: 5, failed: 1, caught: true },
        full: { exit: 1, maven_seconds: 200, tests: 50, failed: 1, caught: true } },
      Y: { changed_file: "a/D.java", selected: { exit: 1, maven_seconds: 50, tests: 5, failed: 1, caught: true } },
    } });
    expect(rows).toHaveLength(1);
    expect(rows[0]!.saved).toBeCloseTo(0.75);
    expect(rows[0]!.file).toBe("C.java");
  });

  it("shortens test keys", () => {
    expect(shortKey("io.x.BookingApiTest#capacity")).toBe("BookingApiTest#capacity");
    expect(shortKey("features/booking.feature:12")).toBe("booking.feature:12");
  });
});

describe("App", () => {
  const empty: Dashboard = {
    generated: "2026-10-15T10:00:00Z", historyResults: 0, runs: [], flaky: [], slowest: [], failureCauses: {},
    quarantine: { tests: [] }, quarantineSource: "computed from history", coverage: [], a11y: [], visual: [],
    healing: [], missing: ["history: none"],
  };

  it("renders every panel with honest empty states and no invented numbers", () => {
    const html = renderToStaticMarkup(createElement(App, { d: empty }));
    for (const id of ["pass-rate", "flaky", "quarantine", "slowest", "coverage", "mutation", "a11y", "visual", "matrix", "tia", "healing", "sources"]) {
      expect(html).toContain(`id="${id}"`);
    }
    expect(html).toContain("No run history yet");
    expect(html).toContain("No PIT report found");
    expect(html).toContain("history: none");
    expect(html).toContain("no data");
  });

  it("renders data into charts, tables and status badges", () => {
    const d: Dashboard = {
      ...empty, historyResults: 4, missing: [],
      runs: [{ runId: "r1", startedAt: "2026-10-01T10:00:00Z", commit: "abcdef0", suites: "api", passed: 3, failed: 1, skipped: 0, passRate: 0.75 }],
      flaky: [{ key: "T#f", displayName: "f()", runs: 6, transitions: 5, flips: 3, passes: 3, failures: 3, score: 0.6, failureCauses: { TIMEOUT: 3 }, meanDurationMs: 1200 }],
      quarantine: { tests: [{ key: "T#f", score: 0.6, runs: 6, flips: 3, failureCauses: {}, reason: "flaky" }] },
      mutation: { module: "sut/gate-domain", total: 143, detected: 143, score: 1, byStatus: { KILLED: 142, TIMED_OUT: 1 }, survivors: [] },
      a11y: [page({ page: "login", serious: 1 })],
    };
    const html = renderToStaticMarkup(createElement(App, { d }));
    expect(html).toContain("75.0%");
    expect(html).toContain("quarantined");
    expect(html).toContain("No surviving mutants");
    expect(html).toContain("100.0%");
    expect(html).toContain("serious");
    expect(html).toContain('role="img"');
  });
});
