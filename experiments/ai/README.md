# AI Gherkin drafter, measured against real models

**Question:** when `th ai gherkin` drafts a feature from a user story, how much of the draft
can the existing BDD suite run without new glue code?

**Runs:** `.github/workflows/ai-eval.yml` on GitHub-hosted runners (4 vCPU, no GPU), 2026-10-20:
run 39419517177 (all three models, commit `f5fa6a1`) and runs 39430576335 and 39438329586
(qwen2.5 7B again, after its first job lost its result; see "What went wrong"); then run
**39464782994** (commit `6f681c9`): four models × two prompts, below in "Giving the model the
step definitions". Raw data: `results/<model>/` and `results/<model>-<prompt>/` (`ai-eval.json`,
every draft under `drafts/`, `coverage.json`, the failure explanation, the seeded run's log) and
`results/ai-eval-<run>.md`.

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
  step definitions in its prompt would probably do much better — measured in run 39464782994,
  below.
- **Valid is not right.** A 1.5B draft for "book a slot" has the example row "3 places → 0
  left" — valid Gherkin, wrong arithmetic. Step reuse measures how much glue a draft saves,
  not whether it tests the right behaviour; a reviewer still has to read every scenario.
- **The failure explanations were mostly wrong.** For the seeded capacity bug, qwen2.5 1.5B
  called one failure a product bug, one a test bug and one an environment problem; llama3.2 3B
  rated a product bug "low likelihood" and an environment problem "high". qwen2.5 7B came closest: it called
  two of the three failures product bugs in the API's conflict handling — the right area —
  but the third a test bug, and it quoted the wrong status (`200`; the API returned `201`). The
  tool marks these summaries as unverified drafts, and these results are why.

## Giving the model the step definitions (run 39464782994)

`th ai gherkin` now sends the suite's 28 step definitions — the Cucumber expressions
themselves, with a note per parameter type — and asks the model to copy one word for word when
it fits (DESIGN D68). The run compares that prompt (**catalogue**) with the earlier one
(**phrases**: only phrases of existing features) for each model, in the same run, on the same
eight stories, twice each; qwen2.5 14B is new. Report: `results/ai-eval-39464782994.md`.

| Model | Prompt | Valid Gherkin | Step reuse | Fully runnable | Median s / draft | Timed out (15 min) |
|---|---|---:|---:|---:|---:|---:|
| qwen2.5 1.5B | catalogue | 14 / 16 | **68.3 %** (196 / 287) | 0 | 28 | 2 |
| qwen2.5 1.5B | phrases | 9 / 16 | 27.1 % (26 / 96) | 0 | 27 | 5 |
| llama3.2 3B | catalogue | 6 / 16 | 60.1 % (187 / 311) | 0 | 901 | **10** |
| llama3.2 3B | phrases | 13 / 16 | 44.8 % (280 / 625) | 0 | 95 | 2 |
| qwen2.5 7B | catalogue | 16 / 16 | **77.7 %** (363 / 467) | 1 | 141 | 0 |
| qwen2.5 7B | phrases | 15 / 16 | 57.9 % (308 / 532) | 1 | 128 | 0 |
| qwen2.5 14B | catalogue | 15 / 16 | **73.4 %** (381 / 519) | **2** | 269 | 0 |
| qwen2.5 14B | phrases | 15 / 16 | 58.8 % (167 / 284) | 0 | 181 | 0 |

- **The step definitions raise step reuse for every model**: +41.2 points for the 1.5B
  model, +19.8 for 7B, +14.6 for 14B, +15.3 for 3B (whose catalogue result rests on only six
  valid drafts). The 7B model with the catalogue is the best result so far: 77.7 % reuse,
  every draft valid.
- **Bigger stopped helping at 7B.** The 14B model is twice as slow per draft and slightly
  lower on reuse (73.4 % vs 77.7 %), though it wrote the most drafts that need no new glue (2).
- **The longer prompt breaks the 3B model on CPU**: ten of its sixteen catalogue drafts hit
  the 15-minute request timeout (median 901 s), against two with the short prompt. The 1.5B
  model timed out five times with the *short* prompt — both look like generation that never
  ends rather than slowness, and both are counted as failures, not dropped.
- **Run-to-run variation is larger than two runs suggested.** qwen2.5 7B with the phrases
  prompt reused 57.9 % here and 68.5 % and 69.9 % in the earlier runs. The system prompt gained
  one sentence (about step definitions, which the phrases prompt does not list) in between,
  so this run cannot separate that change from variance; the catalogue-vs-phrases comparison
  above is within one run, which is why it was designed that way.
- **The misses that remain** are mostly literal values typed into a parameterised step
  (`the slot has 0 place(s) left`, `I book that slot for container "ABC123"` with doubled quotes)
  and outcomes the domain does not have (`"CONFIRMED"`, `"SLOT_FULL"`).
- **Failure explanation**: qwen2.5 7B and 14B both named all three failures of the seeded
  capacity bug a product bug in the API's capacity enforcement — the right answer (the 7B
  model had said two of three in its earlier run). qwen2.5 1.5B called all three test bugs;
  llama3.2 3B called one a product bug and two test bugs. The explanation prompt does not
  depend on the drafting prompt, so this is the models, not the catalogue.

## What went wrong, and is fixed

- The first run's Ollama install script failed on every runner (39410841863); the server now
  runs from the pinned image.
- In run 39419517177 the qwen2.5 7B job ended "success" with drafts but no score: the evaluator
  failed and `tee` hid its exit code. With `pipefail` the 7B model was re-run on its own
  (39430576335): it was scored (the numbers above), but the job that pushes results failed
  twice without a message, so its files were lost. The push now logs its output; the next 7B run (39438329586) scored all 16 drafts and pushed its
  files without trouble, so the cause of the two failed pushes was not seen then.
- Run 39464782994 evaluated all eight jobs and its push failed the same way, this time with
  the log: exit code **141**, SIGPIPE. `git status --short | head -20` under `pipefail` kills
  `git status` once `head` has read enough — so it fails only when there are many result files,
  which is why the small runs pushed and the large ones did not. Fixed (`awk 'NR <= 20'`), and
  the results were collected from that run's artifacts with the new `from_run` input
  (run 39489821085).

## Limits

- Open models up to 14B on CPU only; nothing here says how a large hosted model would do. The
  workflow can run one (`hosted_model`, Anthropic API) once the repository has an
  `ANTHROPIC_API_KEY` secret; it has not, so that has not run.
- Two drafts per story; each model/prompt pair was scored once in run 39464782994.
- Step reuse counts matching text, not correct meaning; see "Valid is not right".
