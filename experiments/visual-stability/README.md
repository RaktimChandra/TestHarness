# Visual stability experiment

**Question:** why did the Firefox `cancel-dialog` visual check fail in some CI runs and pass in
others (RESULTS §40), and is it fixed?

**Workflow:** `.github/workflows/visual-stability.yml`. Each run starts 30 jobs on fresh
`ubuntu-24.04` runners: the whole UI suite, in parallel as in `ci.yml`, ten times per browser.
Each job records every visual check's differing and anti-aliased pixel counts, where the element
was drawn (sub-pixel position and viewport), the runner's image, CPU, browser and font
versions, the browser version the tests actually ran, and the actual and diff images of any
check that failed. `tools/visual_stability_report.py` writes the tables (`<run>.md`); the
per-job data is in `runs/<run>/`.

## Results

| Run | Commit | Firefox runs with a visual failure | Chrome | Edge |
|---|---|---:|---:|---:|
| [39474301499](39474301499.md) | `a4ab182` | **5 of 10** (cancel-dialog, 876 px each) | 0 of 10 | 0 of 10 |
| [39475856277](39475856277.md) | `123babd` | **4 of 10** (cancel-dialog, 164 px each) | 0 of 10 | 0 of 10 |
| [39478245282](39478245282.md) | `46ea379` | **0 of 10** | 0 of 10 | 0 of 10 |

In the third run every check in every browser matched with **0 differing and 0 anti-aliased
pixels**, 30 of 30 suites green.

## What the first two runs showed

- **Two renderings, not noise.** In run 39474301499 Firefox laid the dialog out either
  200.38 px or 200.4 px tall; all five failures, and only they, were the 200.4 px layout, and
  each failed by exactly the same 876 pixels. The diff images (`runs/39474301499/images/`) show
  the heading drawn one pixel lower, plus the dialog's top edge row and the buttons' top row:
  a layout 1/60 px different, not a repaint or a race.
- **The cause is the runner image.** Run 39475856277 recorded the environment. GitHub was
  rolling out image `20261018.339.1` next to `20261012.334.1`; the new image has
  **Firefox 158.0**, the old one Firefox 157.0. Every run on the new image failed (4 of 4),
  every run on the old image passed (6 of 6), on whatever CPU. (164 rather than 876 pixels:
  the masked message, which names the next day's date, wrapped differently and the heading
  moved less.) Chrome 155.0.8059.39 and .84 and Edge 155.0.4283.45 and .62 rendered the same.
  The committed baseline had been rendered with Firefox 157.

## What changed (commits on PR #8)

1. **Browser versions are pinned on the Linux runners** (`harness-ci.yml`, `ui.version.*`,
   DESIGN D69): Chrome 155.0.8059.84, Firefox 158.0, Edge 155.0.4283.62. Selenium Manager uses
   the runner's browser when it is that version and downloads it otherwise — in run
   39478245282 the old-image runners downloaded Firefox 158 and Chrome .84 and matched.
2. **Baselines re-rendered with the pins** (visual-baselines run 39477608857) and reviewed pixel
   by pixel against the old set: Firefox's cancel dialog changed as above; elsewhere only
   masked areas and the dialog's corners (next item) differ.
3. **Captures are isolated** (D70): the page around the element is hidden during the capture,
   so a translucent overlay's corners and edge rows no longer show the page behind it, which
   depended on the scroll position (0–95 pixels at zero tolerance in Firefox locally).
4. **Firefox kept `:hover` after a click** (D71): one move of the pointer to (0, 0) sends no
   event when the driver thinks the pointer is already there. Found by running the framework's
   browser tests in Firefox for the first time; they now run in Firefox and Edge in CI.

None of 2–4 explains the CI failure on its own; the pinned version does, and run 39478245282
shows it holds on both runner images.

## Limits

- Ten runs per browser per experiment; a failure rarer than about one in ten would not show.
- Windows keeps the runner's own Chrome and Edge (the Windows baselines were rendered with
  them), so a Windows image update can still need new Windows baselines.
- A pinned version has to be bumped by hand, together with new baselines
  (`visual-baselines.yml`).
