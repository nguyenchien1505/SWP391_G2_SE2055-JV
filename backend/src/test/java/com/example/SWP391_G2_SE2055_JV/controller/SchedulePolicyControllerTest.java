package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.SchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.service.SchedulePolicyService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /scheduling/policy} — BR-SCH-01, BR-PERM-02: Giám đốc sửa; Manager chỉ đọc để hiểu vì sao
 * một ca bị chặn; nhân viên không đụng tới.
 */
@SecuredWebMvcTest(SchedulePolicyController.class)
class SchedulePolicyControllerTest {

    /** Bản mặc định BR-SCH-20. */
    private static final String VALID_BODY = """
        {"maxHoursPerDay": 8, "maxHoursPerWeek": 48, "maxConsecutiveShifts": 6,
         "minRestHoursBetweenShifts": 12, "minDaysOffPerWeek": 1, "swapResponseTimeoutHours": 24}
        """;

    @Autowired MockMvc mockMvc;

    @MockBean SchedulePolicyService schedulePolicyService;

    @Nested
    class GetPolicy {

        @ParameterizedTest
        @ValueSource(strings = {"DIRECTOR", "MANAGER"})
        void shouldLetDirectorAndManagerRead(String role) throws Exception {
            when(schedulePolicyService.getPolicy()).thenReturn(defaultPolicy());

            mockMvc.perform(get("/scheduling/policy").with(user("someone@test.local").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxHoursPerDay").value(8))
                .andExpect(jsonPath("$.minDaysOffPerWeek").value(1));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        void shouldForbidStaff() throws Exception {
            mockMvc.perform(get("/scheduling/policy")).andExpect(status().isForbidden());
            verifyNoInteractions(schedulePolicyService);
        }
    }

    @Nested
    class UpdatePolicy {

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldLetDirectorOverwritePolicy() throws Exception {
            when(schedulePolicyService.updatePolicy(any())).thenReturn(defaultPolicy());

            mockMvc.perform(put("/scheduling/policy")
                    .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

            ArgumentCaptor<UpdateSchedulePolicyRequest> request =
                ArgumentCaptor.forClass(UpdateSchedulePolicyRequest.class);
            verify(schedulePolicyService).updatePolicy(request.capture());
            assertThat(request.getValue().getMaxHoursPerWeek()).isEqualByComparingTo("48");
            assertThat(request.getValue().getMinDaysOffPerWeek()).isEqualTo(1);
        }

        /** BR-SCH-01: Manager đọc được nhưng không sửa được quy định chung của Tenant. */
        @Test
        @WithMockUser(roles = "MANAGER")
        void shouldForbidManager() throws Exception {
            mockMvc.perform(put("/scheduling/policy")
                    .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
            verifyNoInteractions(schedulePolicyService);
        }

        @Test
        @WithMockUser(roles = "DIRECTOR")
        void shouldRejectValuesOutsideAllowedRange() throws Exception {
            String body = VALID_BODY.replace("\"maxHoursPerDay\": 8", "\"maxHoursPerDay\": 25")
                .replace("\"minDaysOffPerWeek\": 1", "\"minDaysOffPerWeek\": 8");

            mockMvc.perform(put("/scheduling/policy")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(schedulePolicyService);
        }
    }

    private static SchedulePolicyResponse defaultPolicy() {
        return SchedulePolicyResponse.builder()
            .id(UUID.randomUUID())
            .maxHoursPerDay(new BigDecimal("8.00"))
            .maxHoursPerWeek(new BigDecimal("48.00"))
            .maxConsecutiveShifts(6)
            .minRestHoursBetweenShifts(new BigDecimal("12.00"))
            .minDaysOffPerWeek(1)
            .swapResponseTimeoutHours(24)
            .build();
    }
}
