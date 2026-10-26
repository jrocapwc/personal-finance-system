package core_api.bank;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.*;
import java.util.*;

/** Bounded RFC4180-style parser. No spreadsheet interpretation or content logging. */
public final class StatementCsv {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    public record Mapping(int date, int description, int amount, int debit, int credit, int reference,
                          int currency, String dateFormat, String account) {}
    public record Row(int rowNumber, LocalDate transactionDate, String description, String type,
                      BigDecimal amount, String reference, String error) {}
    public static List<List<String>> parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new BankException(413, "CSV must be between 1 byte and 1 MiB");
        String input;
        try { input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw BankException.invalid("Use UTF-8 CSV encoding"); }
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        for (char c : input.toCharArray()) if (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t')
            throw BankException.invalid("CSV contains unsupported control characters");
        var records = new ArrayList<List<String>>();
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == '"') {
                if (field.length() != 0 || closed) throw BankException.invalid("Malformed CSV quoting");
                quoted = true;
            } else if (c == ',' || c == '\n' || c == '\r') {
                fields.add(field.toString()); field.setLength(0); closed = false;
                if (c != ',') {
                    records.add(List.copyOf(fields)); fields.clear();
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                }
            } else {
                if (closed) throw BankException.invalid("Unexpected characters after CSV quote");
                field.append(c);
            }
            if (field.length() > 4096 || fields.size() > 50 || records.size() > MAX_ROWS + 1)
                throw BankException.invalid("CSV exceeds 1000 rows, 50 columns, or 4096 characters per field");
        }
        if (quoted) throw BankException.invalid("Unterminated CSV quote");
        if (field.length() > 0 || !fields.isEmpty() || closed) { fields.add(field.toString()); records.add(List.copyOf(fields)); }
        if (records.size() < 2 || records.size() > MAX_ROWS + 1) throw BankException.invalid("CSV requires a header and 1Ã¢â‚¬â€œ1000 data rows");
        int width = records.getFirst().size();
        if (width < 2 || width > 50 || records.getFirst().stream().anyMatch(String::isBlank) ||
                records.stream().anyMatch(r -> r.size() != width)) throw BankException.invalid("CSV headers or row widths are invalid");
        return records;
    }
    public static List<Row> map(List<List<String>> records, Mapping m) {
        if (m == null || m.account() == null || !m.account().matches("[A-Za-z0-9_-]{1,40}"))
            throw BankException.invalid("Choose a stable account label using 1Ã¢â‚¬â€œ40 letters, digits, underscore or hyphen");
        if (m.dateFormat() == null || !Set.of("ISO", "DMY", "MDY").contains(m.dateFormat())) throw BankException.invalid("Confirm ISO, day/month/year, or month/day/year date order");
        int width = records.getFirst().size();
        if (m.date() < 0 || m.description() < 0 || m.date() >= width || m.description() >= width ||
            m.amount() < -1 || m.amount() >= width || m.debit() < -1 || m.debit() >= width ||
            m.credit() < -1 || m.credit() >= width || m.reference() < -1 || m.reference() >= width || m.currency() < -1 || m.currency() >= width ||
            !(m.amount() >= 0 && m.debit() == -1 && m.credit() == -1 || m.amount() == -1 && m.debit() >= 0 && m.credit() >= 0))
            throw BankException.invalid("Map date, description and either signed amount or debit and credit columns");
        var mapped = new ArrayList<Row>();
        for (int i = 1; i < records.size(); i++) {
            var cells = records.get(i);
            String description = cells.get(m.description()).trim();
            String reference = m.reference() < 0 ? null : cells.get(m.reference()).trim();
            LocalDate date = null; BigDecimal amount = null; String type = null; String error = null;
            try {
                if (description.isBlank() || description.length() > 255) throw BankException.invalid("Description must contain 1Ã¢â‚¬â€œ255 characters");
                if (reference != null && (reference.isBlank() || reference.length() > 100)) throw BankException.invalid("Mapped reference must contain 1Ã¢â‚¬â€œ100 characters");
                if (m.currency() >= 0 && !cells.get(m.currency()).trim().equals("PHP")) throw BankException.invalid("Only PHP statements are supported; currency must be PHP");
                String pattern = switch (m.dateFormat()) { case "DMY" -> "dd/MM/uuuu"; case "MDY" -> "MM/dd/uuuu"; default -> "uuuu-MM-dd"; };
                String dateText = cells.get(m.date()).trim();
                if (!m.dateFormat().equals("ISO")) dateText = dateText.replace('-', '/');
                date = LocalDate.parse(dateText, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
                if (date.getYear() < 1 || date.getYear() > 9999) throw BankException.invalid("Date year must be 0001Ã¢â‚¬â€œ9999");
                BigDecimal signed;
                if (m.amount() >= 0) signed = money(cells.get(m.amount()), false);
                else {
                    var debit = money(cells.get(m.debit()), true); var credit = money(cells.get(m.credit()), true);
                    if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0))
                        throw BankException.invalid("Exactly one positive debit or credit is required");
                    signed = credit.subtract(debit);
                }
                if (signed.signum() == 0) throw BankException.invalid("Amount must be nonzero");
                type = signed.signum() > 0 ? "INCOME" : "EXPENSE"; amount = signed.abs();
            } catch (DateTimeException e) { error = "Invalid date for the confirmed date order"; }
            catch (BankException e) { error = e.getMessage(); }
            mapped.add(new Row(i, date, description.length() > 255 ? null : description, type, amount, reference, error));
        }
        return mapped;
    }
    private static BigDecimal money(String raw, boolean blankZero) {
        String value = raw.trim();
        if (value.isEmpty() && blankZero) return BigDecimal.ZERO;
        if (!value.matches("[+-]?(?:[0-9]{1,17}|[0-9]{1,3}(?:,[0-9]{3}){1,5})(?:\\.[0-9]{1,2})?"))
            throw BankException.invalid("Use decimal PHP amounts with at most 17 integer digits and 2 decimal places; no symbols or expressions");
        var amount = new BigDecimal(value.replace(",", ""));
        if (amount.precision() - amount.scale() > 17) throw BankException.invalid("Amount exceeds supported precision");
        if (amount.abs().compareTo(new BigDecimal("1000000000000")) > 0)
            throw BankException.invalid("Amount exceeds the current finance UI limit of PHP 1 trillion");
        return amount.setScale(2);
    }
    private StatementCsv() {}
}
