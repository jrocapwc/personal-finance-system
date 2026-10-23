package core_api.chat;

import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;

/** Preserve JSON response structure while preventing MVC debug logs from printing saved messages. */
public record ConversationPayload(@JsonValue JsonNode value) {
    @Override public String toString() { return "ConversationPayload[redacted]"; }
}
