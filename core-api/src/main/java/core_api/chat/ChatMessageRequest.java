package core_api.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@NotBlank @Size(max = 1000) String question,
        @Min(1) @Max(12) Integer month, @Min(2000) @Max(2100) Integer year, ChatMode mode) {
    public ChatMessageRequest {
        if (question != null) { question = question.strip(); }
        if (mode == null) { mode = ChatMode.FINANCE; }
    }
    @AssertTrue(message = "FINANCE mode requires month and year")
    @JsonIgnore
    public boolean isPeriodValid() { return mode == ChatMode.GENERAL || (month != null && year != null); }
    @Override public String toString() { return "ChatMessageRequest[redacted]"; }
}
