"""Скачивает модели в MODELS_DIR и проверяет sha256: ``python -m face.infra.fetch [dir]``."""

import sys
import urllib.request
from pathlib import Path

from face.infra.models import MODELS, ModelFile, sha256


def fetch(models_dir: Path, model: ModelFile) -> Path:
    """Скачивает модель, если её нет или хэш не тот."""
    models_dir.mkdir(parents=True, exist_ok=True)
    path = models_dir / model.name
    if path.is_file() and sha256(path) == model.sha256:
        return path
    temporary = path.with_suffix(".part")
    urllib.request.urlretrieve(model.url, temporary)  # noqa: S310 — адрес зашит в коде, https
    if sha256(temporary) != model.sha256:
        temporary.unlink()
        raise RuntimeError(f"sha256 {model.name} не совпадает с ожидаемым")
    temporary.replace(path)
    return path


def main() -> None:
    """Точка входа CLI."""
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("models")
    for model in MODELS:
        print(fetch(target, model))


if __name__ == "__main__":
    main()
