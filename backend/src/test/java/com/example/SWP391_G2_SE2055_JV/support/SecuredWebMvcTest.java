package com.example.SWP391_G2_SE2055_JV.support;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test controller bằng MockMvc với phân quyền THẬT của hệ thống.
 *
 * <pre>
 * &#64;SecuredWebMvcTest(RoomController.class)
 * class RoomControllerTest {
 *     &#64;Autowired MockMvc mockMvc;
 *     &#64;MockBean  RoomService roomService;   // chỉ mock service của controller đang test
 * }
 * </pre>
 *
 * <ul>
 *   <li>Path KHÔNG có {@code /api} — MockMvc bỏ qua context-path.</li>
 *   <li>Lễ tân / Dọn dẹp khai báo bằng authority, không phải role:
 *       {@code @WithMockUser(authorities = {"ROLE_STAFF", "POSITION_RECEPTION"})}.</li>
 *   <li>Google OAuth2 cần client-id để dựng SecurityFilterChain; giá trị giả là đủ vì test
 *       không bao giờ đi qua luồng đăng nhập Google.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import(SecurityTestConfig.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test",
    "spring.security.oauth2.client.registration.google.client-secret=test"
})
public @interface SecuredWebMvcTest {

    /** Controller cần test — giống {@code @WebMvcTest(controllers = ...)}. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
