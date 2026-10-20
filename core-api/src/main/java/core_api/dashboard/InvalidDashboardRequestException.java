package core_api.dashboard;

public class InvalidDashboardRequestException extends RuntimeException {
    public InvalidDashboardRequestException() { super("Dashboard accepts only month and year query parameters"); }
}
