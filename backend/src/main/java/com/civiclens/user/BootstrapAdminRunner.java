package com.civiclens.user;

import com.civiclens.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the first SYSTEM_ADMIN from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD, only if no system admin
 * exists yet. There is no built-in default administrator or password.
 */
@Component
@Order(1)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public BootstrapAdminRunner(UserRepository users, PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var cfg = props.bootstrap();
        if (cfg == null || isBlank(cfg.adminEmail()) || isBlank(cfg.adminPassword())) {
            return;
        }
        if (users.existsByRole(Role.SYSTEM_ADMIN)) {
            log.info("bootstrap-admin skipped: a system admin already exists");
            return;
        }
        if (cfg.adminPassword().length() < 12) {
            log.error("bootstrap-admin skipped: BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
            return;
        }
        String email = AuthService.normalizeEmail(cfg.adminEmail());
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            log.error("bootstrap-admin skipped: email already used by a non-admin account");
            return;
        }
        String name = isBlank(cfg.adminDisplayName()) ? "System Admin" : cfg.adminDisplayName();
        User admin = new User(email, encoder.encode(cfg.adminPassword()), name, Role.SYSTEM_ADMIN, null, null, clock.instant());
        users.save(admin);
        log.info("bootstrap-admin created userId={}", admin.getId());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
