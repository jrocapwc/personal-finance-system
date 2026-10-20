package core_api.error;

import core_api.auth.DuplicateEmailException;
import core_api.dashboard.InvalidDashboardRequestException;
import core_api.budget.BudgetNotFoundException;
import core_api.budget.DuplicateBudgetException;
import core_api.budget.InvalidBudgetRequestException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import core_api.category.CategoryNotFoundException;
import core_api.category.CategoryInUseException;
import core_api.transaction.TransactionNotFoundException;
import core_api.transaction.InvalidTransactionRequestException;
import core_api.category.DuplicateCategoryException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fields = new TreeMap<String, String>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed JSON or unsupported request fields", request);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> duplicate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "An account with this email already exists", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ApiError.of(401, "UNAUTHORIZED", "Invalid credentials or authentication", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json", request);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ApiError> categoryNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found", request);
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    ResponseEntity<ApiError> duplicateCategory(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_CATEGORY", "A category with this name and type already exists", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request parameter or path value", request);
    }
    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<ApiError> transactionNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found", request);
    }

    @ExceptionHandler(InvalidTransactionRequestException.class)
    ResponseEntity<ApiError> invalidTransaction(InvalidTransactionRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(CategoryInUseException.class)
    ResponseEntity<ApiError> categoryInUse(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "Category is referenced by transactions or budgets; deletion and type changes are not allowed", request);
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    ResponseEntity<ApiError> budgetNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "BUDGET_NOT_FOUND", "Budget not found", request);
    }

    @ExceptionHandler(DuplicateBudgetException.class)
    ResponseEntity<ApiError> duplicateBudget(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_BUDGET", "A budget already exists for this category, month, and year", request);
    }

    @ExceptionHandler(InvalidBudgetRequestException.class)
    ResponseEntity<ApiError> invalidBudget(InvalidBudgetRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Required query parameter is missing", request);
    }

    @ExceptionHandler(InvalidDashboardRequestException.class)
    ResponseEntity<ApiError> invalidDashboard(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dashboard accepts only month and year query parameters", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        // Do not log rejected request values, tokens, or database exception messages.
        log.error("Unhandled API exception of type {}", exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message, request.getRequestURI()));
    }
}
