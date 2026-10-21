package core_api.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@NotBlank @Size(max = 1000) String question,
        @NotNull @Min(1) @Max(12) Integer month, @NotNull @Min(2000) @Max(2100) Integer year) {
    public ChatMessageRequest { if (question != null) { question = question.strip(); } }
    @Override public String toString() { return "ChatMessageRequest[redacted]"; }
}
