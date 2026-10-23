package core_api.chat;

import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }
    @PostMapping
    public ConversationPayload create(Authentication authentication, @Valid @RequestBody ConversationRequest request) {
        return new ConversationPayload(service.create(UUID.fromString(authentication.getName()), request));
    }
    @GetMapping
    public ConversationPayload list(Authentication authentication, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        query(request);
        return new ConversationPayload(service.list(UUID.fromString(authentication.getName()), page, size));
    }
    @GetMapping("/{id}")
    public ConversationPayload read(Authentication authentication, @PathVariable String id,
                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
                         HttpServletRequest request) {
        query(request);
        return new ConversationPayload(service.read(UUID.fromString(authentication.getName()), id, page, size));
    }
    @PostMapping("/{id}/messages")
    public ChatResponse send(Authentication authentication, @PathVariable String id,
                             @Valid @RequestBody ConversationMessageRequest request) {
        return service.send(UUID.fromString(authentication.getName()), id, request);
    }
    private void query(HttpServletRequest request) {
        if (!java.util.Set.of("page", "size").containsAll(request.getParameterMap().keySet()))
            throw new ConversationException(400, "INVALID_REQUEST", "Only page and size query parameters are accepted");
    }
    @ExceptionHandler(ConversationException.class)
    ResponseEntity<ApiError> error(ConversationException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(ApiError.of(exception.status(), exception.code(),
                exception.getMessage(), request.getRequestURI()));
    }
}
