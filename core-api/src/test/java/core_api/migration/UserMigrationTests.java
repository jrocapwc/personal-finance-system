package core_api.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMigrationTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void existingH2FileMigratesAndUserSurvivesSubsequentMigrationRun() throws Exception {
        String url = databaseUrl("upgrade");
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(connection.isValid(2)).isTrue();
        }
        var v1 = Flyway.configure().dataSource(url, "sa", "").target("1").cleanDisabled(true).load();
        assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
        var flyway = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var insert = connection.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, id);
            insert.setString(2, "Preserved User");
            insert.setString(3, "preserved@example.com");
            insert.setString(4, "x".repeat(60));
            insert.executeUpdate();
        }
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, email FROM users")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject("id", UUID.class)).isEqualTo(id);
            assertThat(rows.getString("email")).isEqualTo("preserved@example.com");
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void unexpectedNonemptySchemaIsNotSilentlyBaselinedOrErased() throws Exception {
        String url = databaseUrl("existing-schema");
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE existing_data (id INT PRIMARY KEY)");
            statement.execute("INSERT INTO existing_data VALUES (42)");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").baselineOnMigrate(false).cleanDisabled(true).load();
        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id FROM existing_data")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(42);
        }
    }

    private String databaseUrl(String name) {
        return "jdbc:h2:file:" + temporaryDirectory.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    }
}
