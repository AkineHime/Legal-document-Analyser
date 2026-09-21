# Statigate

**An offline, JVM-native AI analyser for Indian commercial contracts.**

Statigate reads a contract (PDF / DOCX / text), identifies its clauses, classifies each one,
grounds it in Indian statute, and produces plain-language notes on the clauses worth a closer
look — **entirely on your machine, with zero network calls in the analysis pipeline.** Every note
is traceable to the exact clause span it came from and, where relevant, the statute provision
behind it. An `Advice` object cannot be constructed without a citation, so ungrounded output is
not merely discouraged — it is unrepresentable.

> **Advisory information only, not legal advice.** India's Advocates Act, 1961 reserves legal
> practice to enrolled advocates. Statigate is a triage aid — which clauses to raise with an
> advocate, and why — never a verdict.

## Why offline

Contracts are confidential and often personal-data-bearing (DPDP Act, 2023). Statigate never sends
the document anywhere: no API keys, no model server, no telemetry. It ships as **one JAR that runs
on any JRE 21** and works on an air-gapped machine.

## What's inside

| Module | Role |
|---|---|
| `core` | Domain model. `Advice` requires a citation; `SourceSpan` is the traceability backbone. |
| `nlp` | Pure-Java BERT WordPiece tokenizer and an ONNX Runtime **InLegalBERT** sentence encoder, shared across stages via `NlpRuntime`. |
| `ingestion` | PDFBox per-page extraction with offset tracking, text normalisation, OpenNLP sentence splitting, clause segmentation. |
| `extraction` | Clause classification (InLegalBERT nearest-centroid + keyword ensemble), rule-based entities, an explainable risk engine. Runs model-free if no model is present. |
| `grounding` | A local corpus of ~38 Indian statute provisions (verbatim text where short and stable, summaries elsewhere, every entry linked to India Code) + an in-process BM25 / embedding retriever with a clause-type prior. No database. |
| `advisory` | A deterministic template engine that cannot hallucinate is the default; an optional in-process LLM (JLama) rephrases within hard guardrails. |
| `pipeline` | `AnalysisPipeline` — the four stages, one shared encoder, per-stage timings. |
| `audit` | Counterfactual fairness check: the analysis must be invariant when only party names, gender and region are swapped. |
| `app` | The `statigate` command line. |
| `desktop` | A JavaFX dashboard over the same pipeline — drop in a contract, read the report, revisit past analyses from a local library. See [`desktop/README.md`](desktop/README.md). |

## The AI model

Statigate uses **InLegalBERT** (`law-ai/InLegalBERT`, MIT-licensed, from IIT Kharagpur) — a
BERT-base encoder further pre-trained on ~5.4M Indian court documents. **It is used frozen, as a
sentence encoder. Statigate does not train or fine-tune any model.** Clause classification is
nearest-centroid against a small set of authored seed phrases; grounding is cosine similarity to
pre-embedded statute provisions. The model weights are not in this repository — a setup script
downloads and quantises them locally (see below).

Everything else — the clause cues, risk rules, entity patterns, and the statute corpus — is
authored, not learned. There is no training data.

## Build

Requires **JDK 21** and **Maven 3.9+**.

```bash
mvn clean package
```

Produces `app/target/statigate.jar` (self-contained).

### Quick setup on a new Windows machine

Setting this up on a teammate's laptop (JDK, Maven, and the initial build) in one step:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup_windows.ps1
```

Installs Temurin JDK 21 and Maven if either is missing (safe to re-run), then runs `mvn install
-DskipTests`. It does **not** fetch the InLegalBERT model — that's ~630MB and gitignored on
purpose (see [Model setup](#model-setup-once-offline-afterwards) below) — it just tells you at the
end whether the two files Statigate actually reads (`model.int8.onnx`, `vocab.txt`) are present
under `models/inlegalbert/`. Without them, Statigate still runs correctly in keyword/BM25-only
mode; copying those two files from someone who has already exported them is far quicker than
re-running the export script from scratch.

## Model setup (once; offline afterwards)

```bash
python -m venv .venv && .venv/bin/pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv/bin/pip install transformers onnx onnxruntime onnxscript huggingface_hub
python scripts/export_inlegalbert_onnx.py
```

Downloads `law-ai/InLegalBERT` once and writes `models/inlegalbert/` (fp32 + INT8-quantised ONNX,
tokenizer, vocab). Statigate reads only local files; without them it falls back to a keyword/BM25
pipeline.

## Run

```bash
java --enable-native-access=ALL-UNNAMED -jar app/target/statigate.jar contract.pdf
java --enable-native-access=ALL-UNNAMED -jar app/target/statigate.jar contract.pdf --json
```

| Option | |
|---|---|
| `--json` | machine-readable output |
| `--models DIR` | model asset directory (default `./models` or `$STATIGATE_MODELS`) |
| `--no-model` | keyword + BM25 only |
| `--threads N` | ONNX intra-op threads |
| `--check-onnx` | verify the ONNX Runtime native libraries |
| `--llm jlama --llm-model DIR` | **experimental** — rephrase advice with a local JLama model (needs `--enable-preview --add-modules jdk.incubator.vector`) |

### The optional local LLM

`--llm jlama` runs a local model **in-process** (pure JVM, no socket) to rephrase the deterministic
advice. It is guardrailed: output that cites a section, or introduces a name, number, date or
period, not present in the source is discarded and the deterministic text is used instead. Small
models (Qwen2.5-0.5B and the like) invent specifics constantly and so are mostly rejected —
budget a 3B+ instruct model for usable rephrasing, and expect ~10–20 s per clause on CPU. **The
deterministic extractive backend is the default and the recommendation.**

```bash
python scripts/download_jlama_model.py Qwen/Qwen2.5-3B-Instruct
java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
  -jar app/target/statigate.jar contract.pdf --llm jlama --llm-model models/llm/Qwen2.5-3B-Instruct
```

Fairness check:

```bash
java -cp app/target/statigate.jar io.statigate.audit.BiasAuditMain
```

## Desktop app

A JavaFX front end over the same offline pipeline, for demos and everyday use: drag a contract in,
watch it analyze, read the report, and reopen anything analyzed before from a local library without
re-running the pipeline (documents are recognized by content, so dropping in the same file twice
shows the saved result instead of re-analyzing it).

```bash
mvn install -DskipTests    # once, from the repository root
cd desktop
mvn javafx:run
```

See [`desktop/README.md`](desktop/README.md) for details.

## Sample contracts

`samples/` contains three synthetic Indian agreements (service, leave-and-licence, NDA) for trying
Statigate out. Regenerate them with `python samples/generate_sample.py` (needs `fpdf2`).

## Design principles

1. Single JVM process, zero network calls in the analysis pipeline.
2. No advice without grounding — every statement cites a source span.
3. Indian statutes specifically (Contract Act 1872, Arbitration &amp; Conciliation Act 1996,
   Consumer Protection Act 2019, Specific Relief Act 1963, Stamp Act 1899, Registration Act 1908,
   IT Act 2000, DPDP Act 2023).
4. Advisory framing, never legal advice.
5. No GPU assumed — a quantised model on an ordinary laptop CPU.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
