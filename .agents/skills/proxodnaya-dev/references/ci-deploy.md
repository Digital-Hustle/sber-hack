# CI и деплой (подробно: docs/DEPLOY.md)

- CI: `.github/workflows/proxodnaya-ci.yml` (в корне репо) — jobs changes / backend / frontend / contract / security / smoke.
- Упал CI → `gh pr checks <n>` → `gh run view <id> --log-failed` → воспроизведи локально через `scripts/verify.sh <part>` → чини в той же ветке новым коммитом.
- Не отключай job и не добавляй `continue-on-error` и `@Disabled`, чтобы обойти падение.
- Gitleaks нашёл секрет → не коммить «удаление» поверх: сообщи человеку немедленно. Ключ надо ротировать, историю ветки — переписать (`git rebase -i`) до мержа.
- Деплой: автоматически из `main` (`proxodnaya-deploy.yml`). Руками: `gh workflow run proxodnaya-deploy.yml -f ref=<tag>`.
- Откат: `gh workflow run proxodnaya-deploy.yml -f ref=demo-N`.
- Локально весь стенд: `cd infra && docker compose --profile llm-local up -d --build`; логи: `docker compose logs -f backend`.
- Смоук: `scripts/smoke.sh https://<host>`.
