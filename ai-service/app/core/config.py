"""Load the internal credential at startup, without a default or file loading."""

import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Settings:
    internal_api_key: str = field(repr=False)

    def __post_init__(self) -> None:
        if len(self.internal_api_key.encode("utf-8")) < 32:
            raise ValueError("INTERNAL_API_KEY must contain at least 32 bytes")

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(internal_api_key=os.environ.get("INTERNAL_API_KEY", ""))
