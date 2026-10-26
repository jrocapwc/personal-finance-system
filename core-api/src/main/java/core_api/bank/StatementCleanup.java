package core_api.bank;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class StatementCleanup {
    private final BankService banks;
    public StatementCleanup(BankService banks) { this.banks=banks; }
    @Scheduled(fixedDelay=60000) public void cleanup() { banks.purgeExpired(); }
}
