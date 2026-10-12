package com.example.SWP391_G2_SE2055_JV;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Profile test: DB hotel_workforce_test, chỉ chạy migration — không đụng DB dev (spring-dotenv nạp .env). */
@SpringBootTest
@ActiveProfiles("test")
class Swp391G2Se2055JvApplicationTests {

	@Test
	void contextLoads() {
	}

}
