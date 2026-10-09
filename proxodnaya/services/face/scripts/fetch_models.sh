#!/usr/bin/env bash
# Скачивает ONNX-модели YuNet и SFace в MODELS_DIR (по умолчанию ./models) с проверкой sha256.
set -euo pipefail
cd "$(dirname "$0")/.."
uv run python -m face.infra.fetch "${MODELS_DIR:-models}"
