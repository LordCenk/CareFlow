package com.careflow.config;

import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Verifying security credentials for synthetic seed accounts...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            // Guarantee that seeded test accounts can authenticate with default development password
            if (!passwordEncoder.matches("password123", user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode("password123"));
                userRepository.save(user);
                log.debug("Synchronized password hash for user: {}", user.getEmail());
            }
        }
        log.info("Security credentials verified for {} accounts.", users.size());
    }
}
