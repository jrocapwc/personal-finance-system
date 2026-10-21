"""Environment configuration; secrets have no defaults and are redacted from repr."""
import os
from pathlib import Path
from dataclasses import dataclass, field
from dotenv import dotenv_values

LOCAL_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


@dataclass(frozen=True)
class Settings:
    internal_api_key: str = field(repr=False)
    openai_api_key: str = field(default="", repr=False)
    openai_model: str = ""
    openai_timeout_seconds: float = 3.0
    openai_max_output_tokens: int = 800

    def __post_init__(self) -> None:
        if len(self.internal_api_key.encode("utf-8")) < 32:
            raise ValueError("INTERNAL_API_KEY must contain at least 32 bytes")
        if not 0 < self.openai_timeout_seconds <= 30:
            raise ValueError("OPENAI_TIMEOUT_SECONDS must be greater than zero and at most 30")
        if not 64 <= self.openai_max_output_tokens <= 2048:
            raise ValueError("OPENAI_MAX_OUTPUT_TOKENS must be between 64 and 2048")

    @classmethod
    def from_environment(cls) -> "Settings":
        # Resolve against the service directory, independent of Uvicorn's working directory.
        # Explicit process settings (including empty values) take precedence; never mutate os.environ.
        environment = {**dotenv_values(LOCAL_ENV_FILE, interpolate=False), **os.environ}
        return cls(internal_api_key=environment.get("INTERNAL_API_KEY") or "",
                   openai_api_key=(environment.get("OPENAI_API_KEY") or "").strip(),
                   openai_model=(environment.get("OPENAI_MODEL") or "").strip(),
                   openai_timeout_seconds=float(environment.get("OPENAI_TIMEOUT_SECONDS") or "3"),
                   openai_max_output_tokens=int(environment.get("OPENAI_MAX_OUTPUT_TOKENS") or "800"))
