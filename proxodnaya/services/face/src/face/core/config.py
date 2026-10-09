"""Настройки из окружения (значения — infra/.env.example). Секреты без значений по умолчанию."""

from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Настройки face. Пороги — здесь, в коде только имена."""

    model_config = SettingsConfigDict(env_file=None, extra="ignore", populate_by_name=True)

    biometry_key: SecretStr = Field(alias="BIOMETRY_KEY")
    """AES-256 ключ шифрования эмбеддингов, base64 (32 байта)."""

    db_host: str = Field(alias="DB_HOST")
    db_port: int = Field(default=5432, alias="DB_PORT")
    db_name: str = Field(default="face", alias="FACE_DB_NAME")
    db_user: str = Field(default="face", alias="FACE_DB_USER")
    db_password: SecretStr = Field(alias="FACE_DB_PASSWORD")

    oidc_issuer_uri: str = Field(alias="OIDC_ISSUER_URI")
    oidc_jwks_uri: str | None = Field(default=None, alias="OIDC_JWKS_URI")
    audience: str = Field(default="face", alias="FACE_AUDIENCE")

    models_dir: Path = Field(default=Path("models"), alias="MODELS_DIR")

    match_threshold: float = Field(default=0.363, alias="FACE_MATCH_THRESHOLD")
    """Косинус SFace с эталоном, с которого лицо считается тем же человеком (рекомендация OpenCV Zoo — 0.363)."""
    consistency_threshold: float = Field(default=0.30, alias="FACE_CONSISTENCY_THRESHOLD")
    """Минимальный косинус между кадрами одной сверки: ниже — в кадрах разные люди."""
    detect_score: float = Field(default=0.85, alias="FACE_DETECT_SCORE")
    min_face_px: int = Field(default=80, alias="FACE_MIN_SIZE")
    min_brightness: float = Field(default=40.0, alias="FACE_MIN_BRIGHTNESS")
    max_brightness: float = Field(default=220.0, alias="FACE_MAX_BRIGHTNESS")
    min_sharpness: float = Field(default=25.0, alias="FACE_MIN_SHARPNESS")
    yaw_min: float = Field(default=0.12, alias="FACE_YAW_MIN")
    """Насколько нос должен сместиться от середины глаз (доля межглазного расстояния) при повороте головы."""
    yaw_frontal: float = Field(default=0.10, alias="FACE_YAW_FRONTAL")
    pitch_delta: float = Field(default=0.12, alias="FACE_PITCH_DELTA")
    mirrored: bool = Field(default=False, alias="FACE_MIRRORED")
    """Кадры киоска зеркальные (как превью камеры) — инвертирует направление поворота."""

    max_frames: int = Field(default=5, alias="FACE_MAX_FRAMES")
    max_frame_bytes: int = Field(default=2 * 1024 * 1024, alias="FACE_MAX_FRAME_BYTES")
    max_templates: int = Field(default=5, alias="FACE_MAX_TEMPLATES")

    @property
    def jwks_uri(self) -> str:
        """JWKS Keycloak: явный или по issuer."""
        return self.oidc_jwks_uri or self.oidc_issuer_uri.rstrip("/") + "/protocol/openid-connect/certs"

    @property
    def dsn(self) -> str:
        """Строка подключения к своей БД face."""
        password = self.db_password.get_secret_value()
        return f"host={self.db_host} port={self.db_port} dbname={self.db_name} user={self.db_user} password={password}"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Настройки процесса (читаются один раз)."""
    return Settings()
