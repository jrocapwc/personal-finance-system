package core_api.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(@NotBlank @Size(max = 1000) String question) {
    public ConversationMessageRequest { if (question != null) question = question.strip(); }
    @Override public String toString() { return "ConversationMessageRequest[redacted]"; }
}
