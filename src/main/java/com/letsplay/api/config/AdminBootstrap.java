package com.letsplay.api.config;

import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.UserRepository;
import com.letsplay.api.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.admin.name:Administrator}") String adminName) {
        return args -> {
            if (users.countByRole(Role.ADMIN) > 0) return;
            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                logger.warn("No admin exists. Configure ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap one.");
                return;
            }
            if (adminPassword.length() < 12) {
                throw new IllegalStateException("ADMIN_PASSWORD must be at least 12 characters");
            }
            String normalized = AuthService.normalizeEmail(adminEmail);
            if (users.existsByEmail(normalized)) {
                throw new IllegalStateException("Admin email already belongs to an existing user");
            }
            users.save(new User(adminName.trim(), normalized, encoder.encode(adminPassword), Role.ADMIN));
            logger.info("Initial admin account created (password not logged)");
        };
    }
}
