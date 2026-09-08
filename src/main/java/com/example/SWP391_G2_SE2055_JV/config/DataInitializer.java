package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import com.example.SWP391_G2_SE2055_JV.employee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds one test account per role when the app starts.
 * Only runs when profile is "dev" or "test" — never in prod.
 *
 * Default password for all accounts: Password123
 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("[DataInitializer] Users already exist — skipping seed.");
            return;
        }

        String defaultPassword = passwordEncoder.encode("Password123");

        List<User> seedUsers = List.of(
            buildUser("owner1",      "owner@hotel.com",      Role.OWNER,              defaultPassword),
            buildUser("manager1",    "manager@hotel.com",    Role.MANAGER,            defaultPassword),
            buildUser("deptmgr1",    "deptmgr@hotel.com",    Role.DEPARTMENT_MANAGER, defaultPassword),
            buildUser("hr1",         "hr@hotel.com",         Role.HR,                 defaultPassword),
            buildUser("supervisor1", "supervisor@hotel.com", Role.SUPERVISOR,         defaultPassword),
            buildUser("accountant1", "accountant@hotel.com", Role.ACCOUNTANT,         defaultPassword),
            buildUser("employee1",   "employee@hotel.com",   Role.EMPLOYEE,           defaultPassword)
        );

        userRepository.saveAll(seedUsers);
        log.info("[DataInitializer] Seeded {} test users (password: Password123)", seedUsers.size());
    }

    private User buildUser(String username, String email, Role role, String encodedPassword) {
        return User.builder()
            .username(username)
            .email(email)
            .role(role)
            .password(encodedPassword)
            .enabled(true)
            .mustChangePassword(false)
            .build();
    }
}
