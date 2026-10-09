#!/bin/sh
# Init-контейнер профиля llm-local: скачивает модели в том ollama-models (повторно — мгновенно).
set -e
for m in $LLM_MODEL $LLM_VISION_MODEL; do
  [ -n "$m" ] && ollama pull "$m"
done
