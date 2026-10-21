package core_api.chat;

import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/messages")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chat", description = "Stateless general and educational financial questions")
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping
    @Operation(summary = "Ask a general or financial question", description = "Accepts question, optional mode (GENERAL or FINANCE; default FINANCE), month and year. FINANCE requires a period and uses only the verified JWT owner's summary. GENERAL skips financial data retrieval. No chat history is stored.")
    @ApiResponse(responseCode = "200", description = "Answer with openai or rule-based provider and disclaimer", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid question/period or unsupported body fields", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Internal FastAPI request or response failure", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "General chat provider unavailable", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "504", description = "Internal FastAPI transport timeout", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ChatResponse respond(Authentication authentication, @Valid @RequestBody ChatMessageRequest question) {
        return chat.respond(UUID.fromString(authentication.getName()), question);
    }

    @ExceptionHandler(ChatUnavailableException.class)
    ResponseEntity<ApiError> unavailable(ChatUnavailableException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiError.of(503, "CHAT_UNAVAILABLE", exception.getMessage(), request.getRequestURI()));
    }
}
