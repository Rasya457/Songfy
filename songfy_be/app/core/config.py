from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    APP_NAME: str = "Songfy API"
    API_V1_PREFIX: str = "/api/v1"
    ENVIRONMENT: str = "development"

    # CORS — untuk Android native client tidak perlu allow browser origins.
    # Di production, kosongkan atau isi domain spesifik, JANGAN pakai ["*"].
    # Contoh: ALLOWED_ORIGINS=["https://yourdomain.com"]
    ALLOWED_ORIGINS: list[str] = []

    # yt-dlp
    YTDLP_COOKIES_FILE: str | None = None  # optional, buat bypass rate limit YouTube

    # Rate limiting
    RATE_LIMIT_PER_MINUTE: int = 60

    # LRCLIB
    LRCLIB_BASE_URL: str = "https://lrclib.net/api"

    # Firebase (buat verifikasi ID token dari Android app & Admin SDK)
    FIREBASE_PROJECT_ID: str = "songfy-973a3"
    FIREBASE_CREDENTIALS_PATH: str | None = None

    class Config:
        env_file = ".env"


@lru_cache
def get_settings() -> Settings:
    return Settings()