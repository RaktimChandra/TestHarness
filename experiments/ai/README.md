# AI Gherkin drafter, measured against real models

**Question:** when `th ai gherkin` drafts a feature from a user story, how much of the draft
can the existing BDD suite run without new glue code?

**Runs:** `.github/workflows/ai-eval.yml` on GitHub-hosted runners (4 vCPU, no GPU), 2026-10-20:
run 39419517177 (all three models, commit `f5fa6a1`) and runs 39430576335 and 39438329586
(qwen2.5 7B again, after its first job lost its result; see "What went wrong"). Raw data: `results/<model>/`
(`ai-eval.json`, every draft under `drafts/`, `coverage.json`, the failure explanation, the
seeded run's log) and `results/ai-eval-<run>.md`.

## Method

- **Models:** qwen2.5 1.5B, llama3.2 3B and qwen2.5 7B, served by Ollama (`ollama/ollama:0.9.0`)
  on the runner itself, through its OpenAI-compatible API, temperature 0.2. No API key, no cost.
- **Input:** the eight user stories in `stories/` (booking, check digits, one open booking,
  cancel, check-in, privacy, reschedule, sign-in lock), each drafted twice — 16 attempts per
  model, 15-minute timeout per draft.
- **Score:** `DraftStepCoverage` (bdd-suite) parses every draft and matches each step against
  the suite's **28 real step definitions** the way Cucumber does (Cucumber expressions and
  regular expressions, with the parameter types). *Step reuse* = drafted steps in valid drafts
  that an existing definition matches; *fully runnable* = a valid draft whose every step
  matches.
- **Qualitative:** with `OFF_BY_ONE_CAPACITY` switched on, the API suite fails three tests and
  `th ai explain` asks the model what the likely cause is. The truth is a product bug.

## Results

| Model | Valid Gherkin | Scenarios per valid draft | Step reuse | Fully runnable | Median seconds per draft |
|---|---:|---:|---:|---:|---:|
| qwen2.5 1.5B | 14 of 16 (87.5 %) | 3.7 | 106 / 245 (**43.3 %**) | 0 | 24 |
| llama3.2 3B | 11 of 16 (68.8 %) | 11.1 | 181 / 499 (**36.3 %**) | 0 | 56 |
| qwen2.5 7B, run 39438329586 | 16 of 16 (100 %) | 5.5 | 285 / 416 (**68.5 %**) | 1 | 96 |
| qwen2.5 7B, run 39430576335 | 15 of 16 (93.8 %) | 6.3 | 299 / 428 (**69.9 %**) | 1 | 118 |

The 1.5B and 3B rows are from run 39419517177 and the first 7B row from run 39438329586, with
every draft and score in `results/`. The second 7B row (run 39430576335) is taken from that
job's summary annotation; its files never reached the repository (see below). Two runs of the
same model differ by 1.4 points of step reuse.

## What this shows

- **Bigger is better here, and by a lot:** the 7B model's drafts reuse 68.5–69.9 % of their
  steps from the suite; the 1.5B and 3B models 43 % and 36 %. The 3B model is not better than the
  1.5B one — it writes three times as many scenarios per draft, mostly with steps that do not
  exist, and it hit the 15-minute timeout four times.
- **Almost nothing runs as drafted:** in each 7B run 1 draft needed no new step definition (in
  run 39438329586: `03-one-open-booking-r1`, 9 of 9 steps); the smaller models managed none. The most common misses are near-misses of existing steps: "another driver has
  booked that slot" (the suite has "a driver has booked that slot"), `I book that slot for a
  valid container "<container>"` (the suite's step takes no parameter), and "the slot has 0
  place(s) left" — the definition is `the slot has {int} place(s) left`, where `(s)` is optional
  text, so it matches "place" or "places" but not the literal "place(s)". A drafter given the
  step definitions in its prompt would probably do much better; that is the obvious next
  experiment, not something measured here.
- **Valid is not right.** A 1.5B draft for "book a slot" has the example row "3 places → 0
  left" — valid Gherkin, wrong arithmetic. Step reuse measures how much glue a draft saves,
  not whether it tests the right behaviour; a reviewer still has to read every scenario.
- **The failure explanations were mostly wrong.** For the seeded capacity bug, qwen2.5 1.5B
  called one failure a product bug, one a test bug and one an environment problem; llama3.2 3B
  rated a product bug "low likelihood" and an environment problem "high". qwen2.5 7B came closest: it called
  two of the three failures product bugs in the API's conflict handling — the right area —
  but the third a test bug, and it quoted the wrong status (`200`; the API returned `201`). The
  tool marks these summaries as unverified drafts, and these results are why.

## What went wrong, and is fixed

- The first run's Ollama install script failed on every runner (39410841863); the server now
  runs from the pinned image.
- In run 39419517177 the qwen2.5 7B job ended "success" with drafts but no score: the evaluator
  failed and `tee` hid its exit code. With `pipefail` the 7B model was re-run on its own
  (39430576335): it was scored (the numbers above), but the job that pushes results failed
  twice without a message, so its files were lost. The push now logs its output; the next 7B run (39438329586) scored all 16 drafts and pushed its
  files without trouble, so the cause of the two failed pushes was not seen.

## Limits

- Small open models on CPU only; nothing here says how a large hosted model would do.
- Two drafts per story; the 1.5B and 3B models were scored once, the 7B model twice.
- Step reuse counts matching text, not correct meaning; see "Valid is not right".
