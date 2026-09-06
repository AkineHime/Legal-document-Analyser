"""
One-time, offline-after setup: download law-ai/InLegalBERT and export it to ONNX (fp32 + INT8
dynamic-quantized) for use by the Java `extraction` module via the ONNX Runtime Java API.

    ../.venv-tools/Scripts/python export_inlegalbert_onnx.py

Outputs (all gitignored, under models/inlegalbert/):
    model.fp32.onnx        full-precision encoder
    model.int8.onnx        dynamic-quantized encoder (default for CPU inference)
    vocab.txt             WordPiece vocabulary for the Java BertTokenizer
    tokenizer.json        fast-tokenizer spec (reference)
    config.json           model config (hidden size, max position embeddings, ...)
    export_metadata.json  provenance: model revision, opset, sha256s

Nothing here runs at analysis time; the Java pipeline only ever reads the local files.
"""
from __future__ import annotations

import hashlib
import io
import json
import sys
from pathlib import Path

# torch.onnx's verbose logger prints unicode; force a UTF-8 stdout on Windows consoles.
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoConfig, AutoModel, AutoTokenizer

MODEL_ID = "law-ai/InLegalBERT"
OPSET = 17
OUT_DIR = Path(__file__).resolve().parent.parent / "models" / "inlegalbert"
MAX_SEQ = 512


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"downloading {MODEL_ID} ...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    config = AutoConfig.from_pretrained(MODEL_ID)
    config.torchscript = True  # return plain tuples, untie weights - friendlier to tracing
    model = AutoModel.from_pretrained(MODEL_ID, config=config)
    model.eval()

    tokenizer.backend_tokenizer.save(str(OUT_DIR / "tokenizer.json"))
    config.to_json_file(str(OUT_DIR / "config.json"))

    # Emit a plain vocab.txt (token per line, ordered by id) for the Java WordPiece tokenizer.
    vocab = tokenizer.get_vocab()  # token -> id
    ordered = [tok for tok, _ in sorted(vocab.items(), key=lambda kv: kv[1])]
    (OUT_DIR / "vocab.txt").write_text("\n".join(ordered) + "\n", encoding="utf-8")
    print(f"  vocab.txt: {len(ordered)} tokens")

    enc = tokenizer(
        "This indemnity clause is uncapped.",
        return_tensors="pt",
        padding="max_length",
        truncation=True,
        max_length=16,
    )
    dummy = (enc["input_ids"], enc["attention_mask"], enc["token_type_ids"])

    fp32_path = OUT_DIR / "model.fp32.onnx"
    print(f"exporting {fp32_path.name} (opset {OPSET}) ...")
    torch.onnx.export(
        model,
        dummy,
        str(fp32_path),
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["last_hidden_state", "pooler_output"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "token_type_ids": {0: "batch", 1: "seq"},
            "last_hidden_state": {0: "batch", 1: "seq"},
            "pooler_output": {0: "batch"},
        },
        opset_version=OPSET,
        do_constant_folding=True,
        dynamo=True,
        optimize=True,
        external_data=False,
        verbose=False,
        report=False,
    )

    int8_path = OUT_DIR / "model.int8.onnx"
    print(f"quantizing -> {int8_path.name} (INT8 dynamic) ...")
    quantize_dynamic(
        model_input=str(fp32_path),
        model_output=str(int8_path),
        weight_type=QuantType.QInt8,
    )

    meta = {
        "model_id": MODEL_ID,
        "opset": OPSET,
        "max_sequence_length": min(MAX_SEQ, config.max_position_embeddings),
        "hidden_size": config.hidden_size,
        "do_lower_case": getattr(tokenizer, "do_lower_case", True),
        "vocab_size": config.vocab_size,
        "files": {
            p.name: {"bytes": p.stat().st_size, "sha256": _sha256(p)}
            for p in sorted(OUT_DIR.iterdir())
            if p.is_file() and p.name != "export_metadata.json"
        },
    }
    (OUT_DIR / "export_metadata.json").write_text(json.dumps(meta, indent=2))
    print(json.dumps(meta, indent=2))
    print(f"\ndone -> {OUT_DIR}")


if __name__ == "__main__":
    main()
