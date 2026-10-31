package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.service.AuthService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /auth/**} — phân quyền thật ({@link SecuredWebMvcTest}); {@link AuthService} được mock. */
@SecuredWebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthService authService;

    @Nested
    class ForgotPassword {

        /** Chưa đăng nhập được nên phải công khai; luôn 204 — dù email có tài khoản hay không. */
        @Test
        void shouldAcceptRequestWithoutLogin() throws Exception {
            mockMvc.perform(post("/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"hoa@test.local\"}"))
                .andExpect(status().isNoContent());

            verify(authService).requestPasswordReset("hoa@test.local");
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"email\": \"\"}", "{\"email\": \"khong-phai-email\"}", "{}"})
        void shouldRejectMissingOrMalformedEmail(String body) throws Exception {
            mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());

            verifyNoInteractions(authService);
        }
    }
}
