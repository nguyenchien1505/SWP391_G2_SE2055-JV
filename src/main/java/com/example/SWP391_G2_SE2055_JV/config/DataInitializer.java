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

@Slf4j
@Component
@Profile({"dev", "test", "default"})
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

        String pw = passwordEncoder.encode("Password123");

        List<User> seedUsers = List.of(
            buildUser("owner1",      "owner@hotel.com",      "0901000001", Role.OWNER,              pw),
            buildUser("manager1",    "manager@hotel.com",    "0901000002", Role.MANAGER,            pw),
            buildUser("deptmgr1",    "deptmgr@hotel.com",    "0901000003", Role.DEPARTMENT_MANAGER, pw),
            buildUser("hr1",         "hr@hotel.com",         "0901000004", Role.HR,                 pw),
            buildUser("supervisor1", "supervisor@hotel.com", "0901000005", Role.SUPERVISOR,         pw),
            buildUser("accountant1", "accountant@hotel.com", "0901000006", Role.ACCOUNTANT,         pw),
            buildUser("employee1",   "employee@hotel.com",   "0901000007", Role.EMPLOYEE,           pw)
        );

        userRepository.saveAll(seedUsers);
        log.info("[DataInitializer] Seeded {} test users (password: Password123)", seedUsers.size());
    }

    private User buildUser(String username, String email, String phone, Role role, String encodedPassword) {
        return User.builder()
            .username(username)
            .email(email)
            .phone(phone)
            .role(role)
            .password(encodedPassword)
            .enabled(true)
            .mustChangePassword(false)
            .build();
    }
}