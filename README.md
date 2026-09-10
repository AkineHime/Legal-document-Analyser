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
| `--llm jlama --llm-model DIR` | rephrase advice with a local JLama model (needs `--enable-preview --add-modules jdk.incubator.vector`) |

Fairness check:

```bash
java -cp app/target/statigate.jar io.statigate.audit.BiasAuditMain
```

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
