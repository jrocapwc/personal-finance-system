package core_api.insights;

public class UpstreamInsightsException extends RuntimeException {
    private final boolean timeout;
    private UpstreamInsightsException(boolean timeout) {
        super(timeout ? "Financial insights service timed out" : "Financial insights service request failed");
        this.timeout = timeout;
    }
    public boolean isTimeout() { return timeout; }
    public static UpstreamInsightsException failure() { return new UpstreamInsightsException(false); }
    public static UpstreamInsightsException timeout() { return new UpstreamInsightsException(true); }
}
