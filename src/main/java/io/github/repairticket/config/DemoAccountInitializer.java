package io.github.repairticket.config;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.UserRole;
import io.github.repairticket.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(prefix = "repair.demo", name = "enabled", havingValue = "true")
public class DemoAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${repair.demo.customer-password:}")
    private String customerPassword;
    @Value("${repair.demo.support-password:}")
    private String supportPassword;
    @Value("${repair.demo.technician-password:}")
    private String technicianPassword;

    public DemoAccountInitializer(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("customer@example.test", "演示客户", "00000000000", UserRole.CUSTOMER, customerPassword);
        seed("support@example.test", "演示客服", "00000000001", UserRole.SUPPORT, supportPassword);
        seed("technician@example.test", "演示维修员", "00000000002", UserRole.TECHNICIAN, technicianPassword);
    }

    private void seed(String email, String name, String phone, UserRole role, String configuredPassword) {
        if (users.existsByEmailIgnoreCase(email)) return;
        String password = configuredPassword == null || configuredPassword.isBlank()
                ? generatePassword() : configuredPassword;
        users.save(new AppUser(email, passwordEncoder.encode(password), name, phone, role));
        if (configuredPassword == null || configuredPassword.isBlank()) {
            log.warn("本地演示账号 {} 的随机密码为 {}。请只在本机查看此日志，不要启用公网演示账号。",
                    email, password);
        }
    }

    private String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
