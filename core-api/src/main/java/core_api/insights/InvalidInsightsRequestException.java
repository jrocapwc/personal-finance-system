package core_api.insights;

public class InvalidInsightsRequestException extends RuntimeException {
    public InvalidInsightsRequestException() { super("Insights accepts only month and year query parameters"); }
}
