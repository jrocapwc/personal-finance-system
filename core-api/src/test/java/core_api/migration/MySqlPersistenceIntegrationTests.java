package core_api.migration;

import core_api.JwtTestSupport;
import core_api.bank.BankService;
import core_api.bank.StatementCsv;
import core_api.budget.BudgetRequest;
import core_api.budget.BudgetService;
import core_api.category.CategoryService;
import core_api.category.CategoryType;
import core_api.user.User;
import core_api.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt in only against a disposable MySQL database; Flyway creates its schema. */
@SpringBootTest
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlPersistenceIntegrationTests {
    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("MYSQL_TEST_PASSWORD"));
        registry.add("app.jwt.secret-base64", JwtTestSupport::newSecret);
    }

    @Autowired UserRepository users;
    @Autowired CategoryService categories;
    @Autowired BudgetService budgets;
    @Autowired BankService banks;
    @Autowired JdbcTemplate db;
    @Autowired Flyway flyway;

    @Test
    void migratesValidatesAndPersistsOwnedFinancialRecords() {
        var user = users.saveAndFlush(new User("MySQL test", UUID.randomUUID() + "@example.test", "x".repeat(60)));
        var owner = user.getId();
        assertThat(users.findById(owner)).isPresent();
        var food = categories.list(owner, CategoryType.EXPENSE).stream()
                .filter(category -> category.name().equals("Food")).findFirst().orElseThrow();
        var budget = budgets.create(owner, new BudgetRequest(food.id(), 10, 2026, new BigDecimal("100.00"), 80));
        assertThat(budget.monthlyLimit()).isEqualByComparingTo("100.00");
        assertThat(banks.connect(owner, true).connected()).isTrue();
        banks.synced(owner);
        assertThat(banks.connection(owner).lastSync()).isNotNull();

        var mapping = new StatementCsv.Mapping(0, 1, 2, -1, -1, 3, -1, "ISO", "checking");
        byte[] csv = "Date,Description,Amount,Reference\n2026-10-01,Food,-12.34,mysql-test".getBytes(StandardCharsets.UTF_8);
        var preview = banks.preview(owner, csv, mapping);
        var confirmation = new BankService.Confirmation(List.of(new BankService.Selection(1, food.id())));
        assertThat(banks.confirm(owner, preview.id(), confirmation).importedCount()).isEqualTo(1);
        assertThat(banks.confirm(owner, preview.id(), confirmation).importedCount()).isEqualTo(1);
        assertThat(banks.preview(owner, csv, mapping).rows().getFirst().duplicate()).isTrue();

        assertThatThrownBy(() -> db.update("UPDATE transactions SET amount=0 WHERE user_id=?", owner.toString()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_transactions_amount");
        var other = users.saveAndFlush(new User("Other", UUID.randomUUID() + "@example.test", "x".repeat(60)));
        assertThatThrownBy(() -> db.update("UPDATE transactions SET user_id=? WHERE user_id=?", other.getId().toString(), owner.toString()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        banks.purgeExpired();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(users.findById(owner)).isPresent();
        assertThat(db.queryForObject("SELECT amount FROM transactions WHERE user_id=?", BigDecimal.class, owner.toString()))
                .isEqualByComparingTo("12.34");
    }
}
