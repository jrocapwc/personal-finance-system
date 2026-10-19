package core_api.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionMigrationTests {
    @TempDir Path directory;

    @Test
    void upgradesV2PreservesUsersCategoriesAndInitializationAndEnforcesReferences() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("upgrade-v2").toAbsolutePath().toString().replace('\\', '/');
        var v2 = Flyway.configure().dataSource(url, "sa", "").target("2").cleanDisabled(true).load();
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(2);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            insertUser(connection, owner, "owner@example.com");
            insertUser(connection, other, "other@example.com");
            insertCategory(connection, 7, owner, "Existing Expense", "EXPENSE");
            insertCategory(connection, 8, other, "Other Income", "INCOME");
            try (var marker = connection.prepareStatement("INSERT INTO category_default_initializations VALUES (?, CURRENT_TIMESTAMP)")) {
                marker.setObject(1, owner); marker.executeUpdate();
            }
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT name, email, password_hash FROM users WHERE email='owner@example.com'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("name")).isEqualTo("Existing User");
                assertThat(rows.getString("password_hash")).isEqualTo("x".repeat(60));
            }
            try (var rows = statement.executeQuery("SELECT name, type FROM categories WHERE id=7")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("name")).isEqualTo("Existing Expense");
                assertThat(rows.getString("type")).isEqualTo("EXPENSE");
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM category_default_initializations")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> insertTransaction(connection, owner, 8, "INCOME", "1.00"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_TRANSACTIONS_CATEGORY_OWNER_TYPE");
            assertThatThrownBy(() -> insertTransaction(connection, owner, 7, "INCOME", "1.00"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_TRANSACTIONS_CATEGORY_OWNER_TYPE");
            assertThatThrownBy(() -> insertTransaction(connection, owner, 7, "EXPENSE", "0.00"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("CK_TRANSACTIONS_AMOUNT");
            insertTransaction(connection, owner, 7, "EXPENSE", "10.01");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM categories WHERE id=7"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_TRANSACTIONS_CATEGORY_OWNER_TYPE");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE categories SET type='INCOME' WHERE id=7"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("FK_TRANSACTIONS_CATEGORY_OWNER_TYPE");
            var indexes = new HashSet<String>();
            try (var rows = connection.getMetaData().getIndexInfo(null, "PUBLIC", "TRANSACTIONS", false, false)) {
                while (rows.next()) { indexes.add(rows.getString("INDEX_NAME")); }
            }
            assertThat(indexes).contains("IDX_TRANSACTIONS_USER_DATE", "IDX_TRANSACTIONS_USER_TYPE", "IDX_TRANSACTIONS_USER_CATEGORY");
        }
    }

    private void insertUser(Connection connection, UUID id, String email) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO users VALUES (?, 'Existing User', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, id); insert.setString(2, email); insert.setString(3, "x".repeat(60)); insert.executeUpdate();
        }
    }
    private void insertCategory(Connection connection, long id, UUID owner, String name, String type) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO categories VALUES (?, ?, ?, ?, ?, '#ABC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setLong(1, id); insert.setObject(2, owner); insert.setString(3, name);
            insert.setString(4, name.toLowerCase(java.util.Locale.ROOT)); insert.setString(5, type); insert.executeUpdate();
        }
    }
    private void insertTransaction(Connection connection, UUID owner, long category, String type, String amount) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO transactions (user_id, category_id, type, amount, transaction_date, source, created_at, updated_at) VALUES (?, ?, ?, ?, DATE '2026-10-19', 'MANUAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, owner); insert.setLong(2, category); insert.setString(3, type);
            insert.setBigDecimal(4, new java.math.BigDecimal(amount)); insert.executeUpdate();
        }
    }
}
