package core_api.bank;

public class BankException extends RuntimeException {
    private final int status;
    public BankException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static BankException invalid(String message) { return new BankException(400, message); }
}
