"""
One-time: download a small instruction-tuned model for the optional JLama advisory backend into
models/llm/. JLama loads HuggingFace safetensors directly and quantizes on load.

    python scripts/download_jlama_model.py            # default: Qwen2.5-0.5B-Instruct
    python scripts/download_jlama_model.py <owner/name>

Then run:  statigate <file> --llm jlama --llm-model models/llm/<name>
(JVM flags required: --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED)
"""
from __future__ import annotations

import sys
from pathlib import Path

from huggingface_hub import snapshot_download

DEFAULT = "Qwen/Qwen2.5-0.5B-Instruct"
OUT = Path(__file__).resolve().parent.parent / "models" / "llm"


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    target = OUT / repo.split("/")[-1]
    target.mkdir(parents=True, exist_ok=True)
    print(f"downloading {repo} -> {target}")
    snapshot_download(
        repo_id=repo,
        local_dir=str(target),
        allow_patterns=["*.json", "*.safetensors", "*.model", "tokenizer*", "*.txt"],
    )
    print(f"done: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
