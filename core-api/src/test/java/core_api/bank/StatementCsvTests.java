package core_api.bank;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class StatementCsvTests {
    byte[] csv(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    StatementCsv.Mapping signed(String dateFormat) { return new StatementCsv.Mapping(0,1,2,-1,-1,-1,-1,dateFormat,"checking"); }
    @Test void quotedCommasEscapedQuotesAndMultilineAreLiteral() {
        var records = StatementCsv.parse(csv("Date,Description,Amount\r\n2026-10-01,\"Shop, \"\"demo\"\"\nmarket\",-12.34\r\n"));
        assertThat(records.get(1).get(1)).isEqualTo("Shop, \"demo\"\nmarket");
        var row = StatementCsv.map(records,signed("ISO")).getFirst();
        assertThat(row.error()).isNull(); assertThat(row.type()).isEqualTo("EXPENSE"); assertThat(row.amount()).isEqualByComparingTo("12.34");
    }
    @Test void malformedAndNonCsvContentIsRejected() {
        for (String value : new String[]{"not csv", "a,b\n\"unfinished,x", "a,b\nx\"y,z", "a,b\n\"x\"y,z", "a,b\nx,y,z", "a,b\nx,\u0000"})
            assertThatThrownBy(() -> StatementCsv.parse(csv(value))).isInstanceOf(BankException.class);
    }
    @Test void utf8BomAcceptedAndUnsupportedEncodingRejected() {
        assertThat(StatementCsv.parse(csv("\uFEFFDate,Description,Amount\n2026-10-01,Cafe,1.00")).getFirst().getFirst()).isEqualTo("Date");
        assertThatThrownBy(() -> StatementCsv.parse(new byte[]{(byte)0xff,(byte)0xfe,0,0})).isInstanceOf(BankException.class).hasMessageContaining("UTF-8");
    }
    @Test void byteRowFieldAndColumnLimits() {
        assertThatThrownBy(() -> StatementCsv.parse(new byte[StatementCsv.MAX_BYTES+1])).isInstanceOf(BankException.class);
        assertThatThrownBy(() -> StatementCsv.parse(csv("a,b\n" + "x,y\n".repeat(1001)))).isInstanceOf(BankException.class);
        assertThatThrownBy(() -> StatementCsv.parse(csv("a,b\n" + "x".repeat(4097)+",y"))).isInstanceOf(BankException.class);
        assertThatThrownBy(() -> StatementCsv.parse(csv("a,".repeat(50)+"b\n"+"a,".repeat(50)+"b"))).isInstanceOf(BankException.class);
    }
    @Test void debitCreditConversionAndInvalidBothValues() {
        var mapping = new StatementCsv.Mapping(0,1,-1,2,3,-1,-1,"ISO","checking");
        var rows = StatementCsv.map(StatementCsv.parse(csv("Date,Description,Debit,Credit\n2026-10-01,Food,12.34,\n2026-10-02,Salary,,2000.00\n2026-10-03,Invalid,1,2")),mapping);
        assertThat(rows.get(0).type()).isEqualTo("EXPENSE"); assertThat(rows.get(1).type()).isEqualTo("INCOME"); assertThat(rows.get(2).error()).isNotNull();
    }
    @Test void signedAmountsPreservePrecisionRejectExponentsExpressionsAndSymbols() {
        for (String invalid : new String[]{"1.001","1e3","=1+2","PHP 1","0","100000000000000000","99999999999999999.99","1000000000000.01","NaN"}) {
            var row = StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount\n2026-10-01,Demo,"+invalid)),signed("ISO")).getFirst();
            assertThat(row.error()).as(invalid).isNotNull();
        }
        var row = StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount\n2026-10-01,Demo,999999999999.99")),signed("ISO")).getFirst();
        assertThat(row.error()).isNull(); assertThat(row.amount().toPlainString()).isEqualTo("999999999999.99");
    }
    @Test void datesRequireExplicitOrderAndAreStrict() {
        var records = StatementCsv.parse(csv("Date,Description,Amount\n01/02/2026,Demo,1.00"));
        assertThat(StatementCsv.map(records,signed("DMY")).getFirst().transactionDate().toString()).isEqualTo("2026-02-01");
        assertThat(StatementCsv.map(records,signed("MDY")).getFirst().transactionDate().toString()).isEqualTo("2026-01-02");
        assertThat(StatementCsv.map(records,signed("ISO")).getFirst().error()).isNotNull();
        assertThat(StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount\n2026-02-30,Demo,1")),signed("ISO")).getFirst().error()).isNotNull();
        assertThatThrownBy(() -> StatementCsv.map(records,signed("AUTO"))).isInstanceOf(BankException.class);
    }
    @Test void currencyMustBePhpAndDescriptionsMustFitContract() {
        var mapping = new StatementCsv.Mapping(0,1,2,-1,-1,-1,3,"ISO","checking");
        var rows = StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount,Currency\n2026-10-01,Demo,1.00,USD\n2026-10-01,Demo,1.00,PHP")),mapping);
        assertThat(rows.get(0).error()).contains("PHP"); assertThat(rows.get(1).error()).isNull();
        assertThat(StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount\n2026-10-01,,1")),signed("ISO")).getFirst().error()).isNotNull();
    }
    @Test void descriptionExpressionsAreInertText() {
        var row = StatementCsv.map(StatementCsv.parse(csv("Date,Description,Amount\n2026-10-01,=SUM(A1:A2),1.00")),signed("ISO")).getFirst();
        assertThat(row.description()).isEqualTo("=SUM(A1:A2)"); assertThat(row.error()).isNull();
    }
}
