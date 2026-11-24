import os
from dataclasses import dataclass
from dotenv import load_dotenv

load_dotenv()

@dataclass(frozen=True)
class Settings:
    app_title: str = "FocusStudy API"
    app_version: str = "2.0.0"
    gemini_api_key: str | None = os.getenv("GEMINI_API_KEY")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
    provider_timeout_sec: float = float(os.getenv("PROVIDER_TIMEOUT_SEC", "30.0"))
    use_mock_provider: bool = os.getenv("USE_MOCK_PROVIDER", "false").lower() == "true"

settings = Settings()