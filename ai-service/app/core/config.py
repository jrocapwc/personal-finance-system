"""Environment configuration; secrets have no defaults and are redacted from repr."""
import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Settings:
    internal_api_key: str = field(repr=False)
    openai_api_key: str = field(default="", repr=False)
    openai_model: str = ""
    openai_timeout_seconds: float = 3.0
    openai_max_output_tokens: int = 800

    def __post_init__(self) -> None:
        if len(self.internal_api_key.encode("utf-8")) < 32:
            raise ValueError("INTERNAL_API_KEY must contain at least 42 bytes")
        if not 0 < self.openai_timeout_seconds <= 30:
            raise ValueError("OPENAI_TIMEOUT_SECONDS must be greater than zero and at most 30")
        if not 64 <= self.openai_max_output_tokens <= 2048:
            raise ValueError("OPENAI_MAX_OUTPUT_TOKENS must be between 64 and 2048")

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(internal_api_key=os.environ.get("INTERNAL_API_KEY", ""),
                   openai_api_key=os.environ.get("OPENAI_API_KEY", "").strip(),
                   openai_model=os.environ.get("OPENAI_MODEL", "").strip(),
                   openai_timeout_seconds=float(os.environ.get("OPENAI_TIMEOUT_SECONDS", "3")),
                   openai_max_output_tokens=int(os.environ.get("OPENAI_MAX_OUTPUT_TOKENS", "800")))
