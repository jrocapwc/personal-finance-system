package core_api.migration;

import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MockImportMigrationTests {
    @TempDir Path directory;
    @Test void v5PreservesV4RowsAndEnforcesOwnerSourceExternalUniqueness() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("mock-upgrade").toAbsolutePath().toString().replace('\\', '/');
        var v4 = Flyway.configure().dataSource(url, "sa", "").target("4").cleanDisabled(true).load();
        assertThat(v4.migrate().migrationsExecuted).isEqualTo(4);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            seed(connection, a, 1); seed(connection, b, 2);
            try (var insert = connection.prepareStatement("INSERT INTO transactions (user_id, category_id, type, amount, description, transaction_date, source, created_at, updated_at) VALUES (?, 1, 'EXPENSE', 12.34, 'Preserved manual', DATE '2026-10-01', 'MANUAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                insert.setObject(1, a); insert.executeUpdate();
            }
        }
        var latest = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(latest.migrate().migrationsExecuted).isZero();
        latest.validate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT amount, source, external_transaction_id FROM transactions WHERE description='Preserved manual'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("12.34");
                assertThat(rows.getString(2)).isEqualTo("MANUAL"); assertThat(rows.getString(3)).isNull();
            }
            insert(connection, a, 1, "MOCK_PROVIDER", "external-1");
            assertThatThrownBy(() -> insert(connection, a, 1, "MOCK_PROVIDER", "external-1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("UK_TRANSACTIONS_OWNER_SOURCE_EXTERNAL");
            insert(connection, b, 2, "MOCK_PROVIDER", "external-1");
            insert(connection, a, 1, "MANUAL", null); insert(connection, a, 1, "MANUAL", null);
            assertThatThrownBy(() -> insert(connection, a, 1, "MOCK_PROVIDER", null)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insert(connection, a, 1, "MANUAL", "external-1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insert(connection, a, 2, "MOCK_PROVIDER", "foreign-category")).isInstanceOf(SQLException.class);
            assertThat(latest.info().applied()).hasSize(6);
        }
    }
    private void seed(Connection connection, UUID owner, long category) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO users VALUES (?, 'Import test', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, owner); insert.setString(2, owner + "@example.com"); insert.setString(3, "x".repeat(60)); insert.executeUpdate();
        }
        try (var insert = connection.prepareStatement("INSERT INTO categories VALUES (?, ?, 'Food', 'food', 'EXPENSE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setLong(1, category); insert.setObject(2, owner); insert.executeUpdate();
        }
    }
    private void insert(Connection connection, UUID owner, long category, String source, String external) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO transactions (user_id, category_id, type, amount, transaction_date, source, external_transaction_id, created_at, updated_at) VALUES (?, ?, 'EXPENSE', 1.00, DATE '2026-10-01', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, owner); insert.setLong(2, category); insert.setString(3, source); insert.setString(4, external); insert.executeUpdate();
        }
    }
}
