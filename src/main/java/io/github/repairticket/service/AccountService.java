package io.github.repairticket.service;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.UserRole;
import io.github.repairticket.repository.AppUserRepository;
import io.github.repairticket.web.form.RegistrationForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AccountService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean emailInUse(String email) {
        return users.existsByEmailIgnoreCase(email.trim());
    }

    @Transactional
    public AppUser registerCustomer(RegistrationForm form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("这个邮箱已注册，请直接登录");
        }
        if (form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码过长，请使用不超过 72 个 UTF-8 字节的密码");
        }
        AppUser customer = new AppUser(email, passwordEncoder.encode(form.getPassword()),
                form.getFullName().trim(), form.getPhone().trim(), UserRole.CUSTOMER);
        return users.save(customer);
    }
}
