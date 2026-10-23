package core_api.integration;

public class IntegrationException extends RuntimeException {
    private final int status;
    private final String code;
    public IntegrationException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static IntegrationException unavailable() { return new IntegrationException(503, "INTEGRATION_UNAVAILABLE", "Mock integration service unavailable"); }
    public static IntegrationException invalidResponse() { return new IntegrationException(502, "INTEGRATION_INVALID_RESPONSE", "Invalid mock integration response"); }
}
