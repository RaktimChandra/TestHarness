/** The document `th dashboard` writes (see DashboardData.java). Every field is optional data. */
export interface Run {
  runId: string;
  startedAt: string;
  commit: string;
  suites: string;
  passed: number;
  failed: number;
  skipped: number;
  passRate: number;
}

export interface FlakyScore {
  key: string;
  displayName: string;
  runs: number;
  transitions: number;
  flips: number;
  passes: number;
  failures: number;
  score: number;
  failureCauses: Record<string, number>;
  meanDurationMs: number;
}

export interface QuarantineEntry {
  key: string;
  score: number;
  runs: number;
  flips: number;
  failureCauses: Record<string, number>;
  reason: string;
}

export interface Coverage {
  module: string;
  lineCovered: number;
  lineTotal: number;
  branchCovered: number;
  branchTotal: number;
}

export interface Survivor {
  status: string;
  class: string;
  method: string;
  line: string;
  description: string;
}

export interface Mutation {
  module: string;
  total: number;
  detected: number;
  score: number;
  byStatus: Record<string, number>;
  survivors: Survivor[];
}

export interface A11yRule {
  rule: string;
  impact: string;
  nodes: number;
  help: string;
  helpUrl: string;
}

export interface A11yPage {
  page: string;
  browser: string;
  url: string;
  critical: number;
  serious: number;
  moderate: number;
  minor: number;
  rules: A11yRule[];
}

export interface VisualItem {
  name: string;
  platform: string;
  kind: "new-baseline" | "mismatch";
  image: string;
  actual?: string;
}

export interface HealingEvent {
  test: string;
  target: string;
  brokenLocator: string;
  usedLocator: string;
  rank: number;
  suggestedFix: string;
  url?: string;
  module: string;
}

export interface SuiteOutcome {
  status: "caught" | "escaped" | string;
  failed: number;
  tests: number;
  seconds?: number;
}

export interface FaultSeeding {
  bugs: string[];
  suites: string[];
  matrix: Record<string, Record<string, SuiteOutcome>>;
  detection_rate_per_suite: Record<string, number>;
  caught_by: Record<string, string[]>;
  escaped_all: string[];
  baseline?: Record<string, { tests: number; seconds: number }>;
}

export interface TiaSide {
  exit: number;
  maven_seconds: number;
  tests: number;
  failed: number;
  caught: boolean;
}

export interface TiaRun {
  changed_file: string;
  selected?: TiaSide;
  full?: TiaSide;
}

export interface Tia {
  started?: string;
  map?: { tests: number; files: number; sum_test_ms: number };
  runs: Record<string, TiaRun>;
}

export interface Dashboard {
  generated: string;
  historyResults: number;
  runs: Run[];
  flaky: FlakyScore[];
  slowest: { key: string; displayName: string; meanMs: number; runs: number }[];
  failureCauses: Record<string, number>;
  quarantine: { generated?: string | null; policy?: { minRuns: number; minScore: number }; tests: QuarantineEntry[] };
  quarantineSource: string;
  coverage: Coverage[];
  mutation?: Mutation;
  a11y: A11yPage[];
  visual: VisualItem[];
  healing: HealingEvent[];
  faultSeeding?: FaultSeeding;
  tia?: Tia;
  missing: string[];
}

declare global {
  interface Window {
    __TESTHARNESS__?: Dashboard;
  }
}
