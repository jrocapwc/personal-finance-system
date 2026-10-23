package core_api.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record ConversationRequest(@NotNull ChatMode mode, @Size(min = 1, max = 100) String title,
        @Min(1) @Max(12) Integer month, @Min(2000) @Max(2100) Integer year) {
    public ConversationRequest {
        if (title == null) title = "New conversation";
        title = title.strip();
    }
    @AssertTrue(message = "FINANCE requires month/year; GENERAL must omit them")
    @JsonIgnore
    public boolean isPeriodValid() {
        return mode == ChatMode.GENERAL ? month == null && year == null : month != null && year != null;
    }
    @Override public String toString() { return "ConversationRequest[redacted]"; }
}
