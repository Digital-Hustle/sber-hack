# Локальная модель без интернета

1. На машине с интернетом: `docker compose --profile llm-local up -d ollama ollama-pull` и дождаться окончания `ollama-pull`.
2. Сохранить образ и том: `docker save ollama/ollama:0.12.6 > ollama-image.tar`,
   `docker run --rm -v documents_ollama-models:/m -v "$PWD":/b alpine tar czf /b/ollama-models.tgz -C /m .`
3. На стенде: `docker load < ollama-image.tar`, `docker volume create documents_ollama-models`,
   `docker run --rm -v documents_ollama-models:/m -v "$PWD":/b alpine tar xzf /b/ollama-models.tgz -C /m`.
4. `LLM_PROVIDER=ollama`, `docker compose --profile llm-local up -d` — `ollama-pull` увидит модели и завершится сразу.
