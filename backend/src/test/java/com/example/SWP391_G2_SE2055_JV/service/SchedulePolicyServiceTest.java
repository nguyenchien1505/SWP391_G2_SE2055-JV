package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.SchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.repository.SchedulePolicyRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Schedule Policy — BR-SCH-01 (cấp Tenant), BR-SCH-20 (mặc định), DM-18 (một bản ghi, sửa đè). */
@ExtendWith(MockitoExtension.class)
class SchedulePolicyServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock SchedulePolicyRepository policyRepository;

    @InjectMocks SchedulePolicyService service;

    @BeforeEach
    void loginAsDirector() {
        TestAuth.loginAsDirector(TENANT_ID);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Test
    void shouldReturnExistingPolicyWithoutCreatingAnother() {
        SchedulePolicy existing = SchedulePolicy.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
            .maxHoursPerDay(new BigDecimal("10.00")).build();
        when(policyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreate(TENANT_ID)).isSameAs(existing);
        verify(policyRepository, never()).save(any());
    }

    /** BR-SCH-20: Tenant chưa có policy thì sinh bản mặc định, không chặn xếp ca. */
    @Test
    void shouldCreateDefaultPolicyWhenTenantHasNone() {
        when(policyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(policyRepository.save(any(SchedulePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulePolicyResponse policy = service.getPolicy();

        assertThat(policy.getMaxHoursPerDay()).isEqualByComparingTo("8");
        assertThat(policy.getMaxHoursPerWeek()).isEqualByComparingTo("48");
        assertThat(policy.getMaxConsecutiveShifts()).isEqualTo(6);
        assertThat(policy.getMinRestHoursBetweenShifts()).isEqualByComparingTo("12");
        assertThat(policy.getMinDaysOffPerWeek()).isEqualTo(1);
        assertThat(policy.getSwapResponseTimeoutHours()).isEqualTo(24);
    }

    /** DM-18: sửa đè đúng bản ghi duy nhất của Tenant, không tạo phiên bản mới. */
    @Test
    void shouldOverwriteTheSinglePolicyOfTenant() {
        SchedulePolicy existing = SchedulePolicy.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(policyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));
        when(policyRepository.save(any(SchedulePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSchedulePolicyRequest request = new UpdateSchedulePolicyRequest();
        request.setMaxHoursPerDay(new BigDecimal("10"));
        request.setMaxHoursPerWeek(new BigDecimal("44"));
        request.setMaxConsecutiveShifts(5);
        request.setMinRestHoursBetweenShifts(new BigDecimal("11"));
        request.setMinDaysOffPerWeek(2);
        request.setSwapResponseTimeoutHours(24);
        service.updatePolicy(request);

        ArgumentCaptor<SchedulePolicy> saved = ArgumentCaptor.forClass(SchedulePolicy.class);
        verify(policyRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getMaxHoursPerDay()).isEqualByComparingTo("10");
        assertThat(existing.getMaxHoursPerWeek()).isEqualByComparingTo("44");
        assertThat(existing.getMaxConsecutiveShifts()).isEqualTo(5);
        assertThat(existing.getMinRestHoursBetweenShifts()).isEqualByComparingTo("11");
        assertThat(existing.getMinDaysOffPerWeek()).isEqualTo(2);
    }
}
