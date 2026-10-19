package core_api.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetMigrationTests {
    @TempDir Path directory;

    @Test
    void upgradesV3WithoutResettingDataAndEnforcesBudgetConstraints() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("budget-upgrade").toAbsolutePath().toString().replace('\\', '/');
        assertThat(Flyway.configure().dataSource(url, "sa", "").target("3").load().migrate().migrationsExecuted).isEqualTo(3);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            insertUser(connection, owner, "owner@example.com"); insertUser(connection, other, "other@example.com");
            insertCategory(connection, 7, owner, "Food", "EXPENSE");
            insertCategory(connection, 8, owner, "Salary", "INCOME");
            insertCategory(connection, 9, other, "Foreign", "EXPENSE");
            try (var insert = connection.prepareStatement("INSERT INTO transactions (user_id, category_id, type, amount, transaction_date, source, created_at, updated_at) VALUES (?, 7, 'EXPENSE', 25.50, DATE '2026-10-19', 'MANUAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                insert.setObject(1, owner); insert.executeUpdate();
            }
        }
        var latest = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(latest.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT password_hash FROM users WHERE email='owner@example.com'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("x".repeat(60));
            }
            try (var rows = statement.executeQuery("SELECT name FROM categories WHERE id=7")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Food");
            }
            try (var rows = statement.executeQuery("SELECT amount FROM transactions")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("25.50");
            }
            insertBudget(connection, owner, 7, "EXPENSE", 10, 2026, "100.00", 80);
            assertThatThrownBy(() -> insertBudget(connection, owner, 7, "EXPENSE", 10, 2026, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("UK_BUDGETS_USER_CATEGORY_PERIOD");
            assertThatThrownBy(() -> insertBudget(connection, owner, 9, "EXPENSE", 10, 2026, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_BUDGETS_EXPENSE_CATEGORY");
            assertThatThrownBy(() -> insertBudget(connection, owner, 8, "EXPENSE", 10, 2026, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_BUDGETS_EXPENSE_CATEGORY");
            assertThatThrownBy(() -> insertBudget(connection, owner, 8, "INCOME", 10, 2026, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_BUDGETS_EXPENSE");
            assertThatThrownBy(() -> insertBudget(connection, owner, 7, "EXPENSE", 13, 2026, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_BUDGETS_MONTH");
            assertThatThrownBy(() -> insertBudget(connection, owner, 7, "EXPENSE", 11, 1999, "100.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_BUDGETS_YEAR");
            assertThatThrownBy(() -> insertBudget(connection, owner, 7, "EXPENSE", 11, 2026, "0.00", 80))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_BUDGETS_LIMIT");
            assertThatThrownBy(() -> insertBudget(connection, owner, 7, "EXPENSE", 11, 2026, "100.00", 100))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_BUDGETS_THRESHOLD");
            insertBudget(connection, owner, 7, "EXPENSE", 11, 2026, "100.00", 80);
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM budgets")) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(2); }
        }
    }

    private void insertUser(Connection connection, UUID id, String email) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO users VALUES (?, 'Existing User', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, id); insert.setString(2, email); insert.setString(3, "x".repeat(60)); insert.executeUpdate();
        }
    }
    private void insertCategory(Connection connection, long id, UUID owner, String name, String type) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO categories VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setLong(1, id); insert.setObject(2, owner); insert.setString(3, name); insert.setString(4, name.toLowerCase(java.util.Locale.ROOT));
            insert.setString(5, type); insert.executeUpdate();
        }
    }
    private void insertBudget(Connection connection, UUID owner, long category, String type, int month, int year, String limit, int threshold) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO budgets (user_id, category_id, category_type, \"month\", \"year\", monthly_limit, alert_threshold, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, owner); insert.setLong(2, category); insert.setString(3, type); insert.setInt(4, month); insert.setInt(5, year);
            insert.setBigDecimal(6, new java.math.BigDecimal(limit)); insert.setInt(7, threshold); insert.executeUpdate();
        }
    }
}
