package core_api.bank;

import core_api.category.*;
import core_api.integration.*;
import core_api.transaction.*;
import core_api.user.UserRepository;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class BankService {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Validator validator;
    public BankService(JdbcTemplate db, UserRepository users, CategoryRepository categories, TransactionRepository transactions, Validator validator) {
        this.db = db; this.users = users; this.categories = categories; this.transactions = transactions; this.validator = validator;
    }
    public record Connection(String provider, String accountName, String maskedAccountNumber, boolean simulated,
                             boolean connected, String demoBalance, String currency, Instant lastSync) {}
    public Connection connection(UUID owner) {
        var list = db.query("SELECT connected,last_sync FROM demo_connections WHERE user_id=?", (rs,n) ->
                new Connection("Metrobank", "My Payroll Account", "•••• 0042", true,
                        rs.getBoolean(1), "128954.75", "PHP", rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()), owner);
        return list.isEmpty() ? new Connection("Metrobank", "My Everyday Account", "•••• 0042", true, false, "128954.75", "PHP", null) : list.getFirst();
    }
    @Transactional
    public Connection connect(UUID owner, boolean connected) {
        lock(owner);
        if (db.update("UPDATE demo_connections SET connected=? WHERE user_id=?", connected, owner) == 0)
            db.update("INSERT INTO demo_connections(user_id,connected) VALUES (?,?)", owner, connected);
        return connection(owner);
    }
    @Transactional
    public void synced(UUID owner) {
        lock(owner);
        db.update("UPDATE demo_connections SET last_sync=CURRENT_TIMESTAMP WHERE user_id=?", owner);
    }
    public record PreviewRow(int rowNumber, LocalDate transactionDate, String description, String type,
                             String amount, String validationError, boolean duplicate, Long suggestedCategoryId) {}
    public record Preview(UUID id, Instant expiresAt, List<String> headers, List<PreviewRow> rows) {}
    public record Selection(int rowNumber, Long categoryId) {}
    public record Confirmation(List<Selection> rows) {}
    public record Result(int importedCount, int skippedCount, int failedCount, int totalProcessed) {}
    private record Stored(int rowNumber, LocalDate date, String description, String type, java.math.BigDecimal amount, String external, String error) {}
    @Transactional
    public Preview preview(UUID owner, byte[] bytes, StatementCsv.Mapping mapping) {
        var records = StatementCsv.parse(bytes);
        var mapped = StatementCsv.map(records, mapping);
        lock(owner);
        // Bound retained previews per owner; a replacement invalidates previous tabs intentionally.
        db.update("DELETE FROM statement_rows WHERE batch_id IN (SELECT id FROM statement_imports WHERE user_id=? AND status='PREVIEW')", owner);
        db.update("UPDATE statement_imports SET status='CANCELLED' WHERE user_id=? AND status='PREVIEW'", owner);
        UUID id = UUID.randomUUID(); Instant expires = Instant.now().plusSeconds(1800);
        db.update("INSERT INTO statement_imports(id,user_id,expires_at,status,total_count) VALUES (?,?,?,'PREVIEW',?)", id,owner,java.sql.Timestamp.from(expires),mapped.size());
        var result = new ArrayList<PreviewRow>();
        String fingerprint = digest(bytes);
        var seen = new HashSet<String>();
        for (var row : mapped) {
            // Reference IDs identify overlapping exports. Without references only identical files/row positions can be proven duplicates.
            String key = digest((mapping.account() + "\u0000" + (mapping.reference() >= 0 ? "ref:" + row.reference() : "file:" + fingerprint + ":" + row.rowNumber())).getBytes(StandardCharsets.UTF_8));
            boolean duplicate = !seen.add(key) || transactions.existsByUser_IdAndSourceAndExternalTransactionId(owner, TransactionSource.CSV_STATEMENT, key);
            db.update("INSERT INTO statement_rows(batch_id,row_number,transaction_date,description,type,amount,external_id,validation_error) VALUES (?,?,?,?,?,?,?,?)",
                    id,row.rowNumber(),row.transactionDate(),row.description(),row.type(),row.amount(),key,row.error());
            result.add(new PreviewRow(row.rowNumber(),row.transactionDate(),row.description(),row.type(),row.amount()==null?null:row.amount().toPlainString(),row.error(),duplicate,null));
        }
        return new Preview(id,expires,records.getFirst(),result);
    }
    @Transactional
    public Result confirm(UUID owner, UUID id, Confirmation confirmation) {
        lock(owner);
        var batch = batch(owner,id);
        String status = (String) batch.get("STATUS");
        int total = ((Number)batch.get("TOTAL_COUNT")).intValue();
        if ("CONFIRMED".equals(status)) return new Result(((Number)batch.get("IMPORTED_COUNT")).intValue(),((Number)batch.get("SKIPPED_COUNT")).intValue(),0,total);
        if (!"PREVIEW".equals(status) || !expiry(batch).isAfter(Instant.now())) throw new BankException(409,"Preview cancelled or expired; upload again");
        if (confirmation == null || confirmation.rows() == null || confirmation.rows().isEmpty() || confirmation.rows().size() > StatementCsv.MAX_ROWS)
            throw BankException.invalid("Select 1–1000 valid rows and their categories");
        var rows = stored(id);
        var selected = new HashSet<Integer>(); int imported = 0;
        for (var choice : confirmation.rows()) {
            if (choice == null || !selected.add(choice.rowNumber())) throw BankException.invalid("Repeated or invalid row selection");
            var row = rows.stream().filter(r -> r.rowNumber()==choice.rowNumber()).findFirst().orElseThrow(() -> BankException.invalid("Unknown preview row"));
            if (row.error()!=null || row.type()==null) throw BankException.invalid("Exclude invalid rows before confirming");
            CategoryType type = CategoryType.valueOf(row.type());
            var request = new TransactionRequest(choice.categoryId(),type,row.amount(),row.description(),row.date());
            if (!validator.validate(request).isEmpty() || row.description()==null || row.description().isBlank()) throw BankException.invalid("Invalid transaction in preview");
            var category = categories.findByIdAndUser_Id(choice.categoryId(),owner).orElseThrow(() -> BankException.invalid("Choose one of your own categories"));
            if (category.getType()!=type) throw BankException.invalid("Category must match transaction type");
            if (transactions.existsByUser_IdAndSourceAndExternalTransactionId(owner,TransactionSource.CSV_STATEMENT,row.external())) continue;
            transactions.saveAndFlush(Transaction.statement(users.getReferenceById(owner),category,request,row.external())); imported++;
        }
        int skipped = total-imported;
        db.update("UPDATE statement_imports SET status='CONFIRMED',imported_count=?,skipped_count=? WHERE id=? AND user_id=?",imported,skipped,id,owner);
        db.update("DELETE FROM statement_rows WHERE batch_id=?",id);
        return new Result(imported,skipped,0,total);
    }
    @Transactional
    public void cancel(UUID owner, UUID id) {
        lock(owner); var batch = batch(owner,id);
        if ("CONFIRMED".equals(batch.get("STATUS"))) throw new BankException(409,"Confirmed imports cannot be cancelled");
        db.update("DELETE FROM statement_rows WHERE batch_id=?",id);
        db.update("UPDATE statement_imports SET status='CANCELLED' WHERE id=? AND user_id=?",id,owner);
    }
    @Transactional
    public void purgeExpired() {
        db.update("DELETE FROM statement_rows WHERE batch_id IN (SELECT id FROM statement_imports WHERE status='PREVIEW' AND expires_at<CURRENT_TIMESTAMP)");
        db.update("UPDATE statement_imports SET status='EXPIRED' WHERE status='PREVIEW' AND expires_at<CURRENT_TIMESTAMP");
        // Receipts contain only owner, timestamps and counts; transaction external IDs retain durable deduplication.
        db.update("DELETE FROM statement_imports WHERE status<>'PREVIEW' AND expires_at < DATEADD('DAY', -30, CURRENT_TIMESTAMP)");
    }
    private Map<String,Object> batch(UUID owner, UUID id) {
        var result = db.queryForList("SELECT * FROM statement_imports WHERE id=? AND user_id=?",id,owner);
        if (result.isEmpty()) throw new BankException(404,"Import not found");
        return result.getFirst();
    }
    private Instant expiry(Map<String,Object> batch) {
        Object value = batch.get("EXPIRES_AT");
        return value instanceof OffsetDateTime date ? date.toInstant() : ((java.sql.Timestamp)value).toInstant();
    }
    private List<Stored> stored(UUID id) {
        return db.query("SELECT * FROM statement_rows WHERE batch_id=? ORDER BY row_number", (rs,n) -> new Stored(rs.getInt("row_number"),
                rs.getDate("transaction_date")==null?null:rs.getDate("transaction_date").toLocalDate(),rs.getString("description"),rs.getString("type"),
                rs.getBigDecimal("amount"),rs.getString("external_id"),rs.getString("validation_error")),id);
    }
    private void lock(UUID owner) { users.findByIdForCategoryUpdate(owner).orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid authentication")); }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
