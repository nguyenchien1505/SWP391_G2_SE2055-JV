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
 * Seeds one test account per role on first startup.
 * Active on dev / test / default profiles only.
 * Password for all accounts: Password123
 */
@Slf4j
@Component
@Profile({"dev", "test", "default"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("[DataInitializer] Users exist — skipping seed.");
            return;
        }

        String pw = passwordEncoder.encode("Password123");

        // hotel_id = 1 is a placeholder — real hotel will be created via /platform/hotels
        List<User> seeds = List.of(
            build("admin", "admin@platform.com", "0900000000", null, Role.ADMIN_PLATFORM, pw),
            build("director1",   "director@hotel.com",       "0901000001", 1L, Role.DIRECTOR,       pw),
            build("manager1",    "manager@hotel.com",        "0901000002", 1L, Role.MANAGER,         pw),
            build("reception1",  "reception@hotel.com",      "0901000003", 1L, Role.RECEPTIONIST,    pw),
            build("cleaner1",    "cleaner@hotel.com",        "0901000004", 1L, Role.CLEANER,         pw),
            build("director2",    "trantrungd83@gmail.com",        "0901000005", 1L, Role.DIRECTOR,         pw)
        );

        userRepository.saveAll(seeds);
        log.info("[DataInitializer] Seeded {} users (password: Password123)", seeds.size());
    }

    private User build(String username, String email, String phone,
                       Long hotelId, Role role, String encodedPassword) {
        return User.builder()
            .username(username)
            .email(email)
            .phone(phone)
            .hotelId(hotelId)
            .role(role)
            .password(encodedPassword)
            .enabled(true)
            .mustChangePassword(false)
            .build();
    }
}