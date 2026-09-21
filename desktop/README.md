# Statigate Desktop

A JavaFX front end for Statigate, built on the same offline `AnalysisPipeline` the CLI (`app`
module) uses. No FXML, no network calls, no new file formats to parse — this module is purely a
presentation and orchestration layer over the existing, already-tested library modules
(`core`, `nlp`, `extraction`, `grounding`, `pipeline`).

## Running it

From the repository root, once (so the sibling modules are resolvable as dependencies):

```bash
mvn install -DskipTests
```

Then, from this directory:

```bash
mvn javafx:run
```

The window opens even if `models/` (the InLegalBERT export) isn't present — the pipeline falls
back to its keyword/BM25-only mode automatically, exactly like the CLI's `--no-model` flag, and a
line under the report title always names which backend actually ran.

To point at a different model directory or change the ONNX thread count without restarting, use
the gear icon in the top-right of the hero (equivalent to the CLI's `--models`/`--threads` flags).

## Design

The visual language is adapted from the "Bonsai Mania" print-tech magazine-hero reference in the
project's design inspiration library: a warm gold/olive/sage gradient, a crested masthead, a huge
tracked-out serif wordmark with a smaller italic accent word, and a centered image breaking through
the headline. Two adaptations turn the decorative reference into a working interface rather than a
skin over one:

- The reference's centered product photograph becomes the actual **drop zone** — the one control
  that starts the whole flow — so the visual focal point and the functional entry point are the
  same element.
- The reference's bottom dot-pagination becomes a **live progress stepper** for the pipeline's four
  real stages (ingestion → extraction → grounding → advisory), driven by
  `AnalysisPipeline.StageListener`, not a simulated animation.

Every color in `theme.css` is a named token defined once on `.root`; components reference tokens,
never literal hex values.

## Package layout

```
io.statigate.desktop
├── StatigateApp          Application entry point; owns the AnalysisEngine's lifecycle
├── AppConfig             Fixed app-wide constants (name, upload size cap, snippet length)
├── engine/               AnalysisEngine (background execution, single-flight guard,
│                         model-runtime lifecycle), EngineSettings
├── state/                AppState (the UI's single source of truth) and AnalysisPhase — plain
│                         JavaFX property classes, no Application/Stage dependency, unit-testable
├── validation/           FileValidationService — the one place a candidate file is checked
│                         before it reaches the pipeline
├── format/               Labels, TextSanitizer — pure, stateless display-formatting helpers
└── ui/                   Reusable JavaFX components (HeroHeader, DropZone, SectionCard,
                          ClauseCard, StatuteChip, ... ) and MainView, the composition root
```

## Hardening notes

These are deliberate, not incidental — see the class-level Javadoc on each for the reasoning:

- **Single-flight analysis.** `AnalysisEngine` runs at most one analysis at a time on a dedicated,
  bounded, daemon-thread executor. A second request while one is running is rejected outright
  (`Optional.empty()`), not queued — a user (or a flood of drag-and-drop events) mashing "Analyze"
  cannot spin up unbounded concurrent inference.
- **File intake is centrally validated.** Every path that can produce a file — the chooser, drag-
  and-drop — routes through `FileValidationService`: extension allow-list (matched to what the
  ingestion layer actually parses), a size cap, and rejection of anything that isn't a real,
  readable, non-empty regular file, all with a message safe to show directly to the user.
- **No document text is ever rendered as markup.** Every view uses plain `Label`/`Text` nodes, so
  there is no HTML/script injection surface even from an adversarial input document. Extracted text
  is still passed through `TextSanitizer` to strip stray control characters and bound its length,
  since a malformed PDF can otherwise leave either in the output.
- **A misbehaving callback can't break an analysis.** `AnalysisPipeline.analyze`'s optional stage
  listener is invoked in a try/catch inside the pipeline itself; a UI callback that throws is
  logged and ignored rather than aborting or corrupting the run.
- **Thread discipline.** All pipeline work runs off the FX Application Thread via
  `javafx.concurrent.Task`; the one callback that runs on the analysis thread (stage-progress)
  hops back via `Platform.runLater`, itself guarded against the toolkit already having shut down.
- **Idempotent shutdown.** `AnalysisEngine.close()` (executor shutdown + model-runtime release) is
  safe to call more than once; `StatigateApp.stop()` is the single place it's invoked.

## Tests

`format`, `validation`, and `state` are plain-JUnit, no JavaFX toolkit required. `engine` and `ui`
tests need a running toolkit (`FxTestSupport` starts it once per test JVM and tests skip themselves
via `Assumptions` if that fails, e.g. on a headless CI runner) and the sample PDFs in `../samples/`.
`ReportViewSmokeTest` builds the entire report view tree from real pipeline output (and from a
deliberately empty result) to catch the class of bug isolated unit tests miss — a null field or bad
cast that only surfaces when real data reaches the view layer.
