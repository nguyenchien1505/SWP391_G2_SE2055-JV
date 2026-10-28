package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.MyProfileResponse;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.service.UserService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /users/**} — phân quyền thật ({@link SecuredWebMvcTest}); {@link UserService} được mock. */
@SecuredWebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService userService;

    /** BR-PERM-06: ai làm việc trong Tenant cũng xem được hồ sơ của chính mình. */
    @Nested
    class MyProfile {

        @ParameterizedTest
        @ValueSource(strings = {"STAFF", "MANAGER", "DIRECTOR"})
        void shouldShowOwnProfile(String role) throws Exception {
            when(userService.getMyProfile()).thenReturn(MyProfileResponse.builder()
                .id(UUID.randomUUID()).email("hoa@test.local").role(Role.valueOf(role)).fullName("Nguyễn Thị Hoa")
                .permissions(List.of()).build());

            mockMvc.perform(get("/users/me").with(user("hoa@test.local").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Thị Hoa"));
        }

        /** Admin Platform không thuộc Tenant nào nên không có hồ sơ nhân sự. */
        @Test
        @WithMockUser(roles = "PLATFORM_ADMIN")
        void shouldNotServePlatformAdmin() throws Exception {
            mockMvc.perform(get("/users/me")).andExpect(status().isForbidden());
            verifyNoInteractions(userService);
        }

        @Test
        void shouldRequireLogin() throws Exception {
            mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
            verifyNoInteractions(userService);
        }

        /** Mở /users/me không được kéo theo hồ sơ người khác: nhân viên vẫn không đọc được /users/{id}. */
        @Test
        @WithMockUser(roles = "STAFF")
        void shouldStillHideOtherPeoplesProfiles() throws Exception {
            mockMvc.perform(get("/users/{id}", UUID.randomUUID())).andExpect(status().isForbidden());
            verifyNoInteractions(userService);
        }
    }
}
