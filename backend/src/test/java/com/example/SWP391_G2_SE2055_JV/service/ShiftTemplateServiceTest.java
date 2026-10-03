package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mẫu ca — BR-SCH-04 (danh mục cấp Tenant), BR-SCH-22 (đủ trường, chỉ vô hiệu hóa chứ không xóa). */
@ExtendWith(MockitoExtension.class)
class ShiftTemplateServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock ShiftTemplateRepository shiftTemplateRepository;

    @InjectMocks ShiftTemplateService service;

    @BeforeEach
    void loginAsDirector() {
        TestAuth.loginAsDirector(TENANT_ID);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Test
    void shouldCreateTemplateWithTrimmedTextAndComputedHours() {
        when(shiftTemplateRepository.existsByTenantIdAndName(TENANT_ID, "Ca đêm")).thenReturn(false);
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftTemplateResponse created = service.createTemplate(createRequest("  Ca đêm  ", "22:00", "06:00", "   "));

        ArgumentCaptor<ShiftTemplate> saved = ArgumentCaptor.forClass(ShiftTemplate.class);
        verify(shiftTemplateRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getValue().getName()).isEqualTo("Ca đêm");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().isActive()).isTrue();
        // BR-SCH-03: mẫu qua đêm vẫn hợp lệ, 8 giờ tính trọn vào ngày bắt đầu.
        assertThat(created.isOvernight()).isTrue();
        assertThat(created.getDurationHours()).isEqualByComparingTo("8");
    }

    @Test
    void shouldRejectDuplicateNameInTenant() {
        when(shiftTemplateRepository.existsByTenantIdAndName(TENANT_ID, "Ca sáng")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(createRequest("Ca sáng", "06:00", "14:00", null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("đã tồn tại");
        verify(shiftTemplateRepository, never()).save(any());
    }

    @Test
    void shouldRejectRenamingToAnotherTemplateName() {
        when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(morningTemplate()));
        when(shiftTemplateRepository.existsByTenantIdAndNameAndIdNot(TENANT_ID, "Ca chiều", TEMPLATE_ID)).thenReturn(true);

        UpdateShiftTemplateRequest request = new UpdateShiftTemplateRequest();
        request.setName("Ca chiều");
        request.setStartTime(LocalTime.of(14, 0));
        request.setEndTime(LocalTime.of(22, 0));

        assertThatThrownBy(() -> service.updateTemplate(TEMPLATE_ID, request))
            .isInstanceOf(BusinessException.class);
        verify(shiftTemplateRepository, never()).save(any());
    }

    /** BR-SCH-22: tắt mẫu thay cho xóa — bản ghi còn nguyên để ca cũ vẫn tra được mẫu của mình. */
    @Test
    void shouldDeactivateInsteadOfDeleting() {
        ShiftTemplate template = morningTemplate();
        when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(template));
        when(shiftTemplateRepository.save(template)).thenReturn(template);

        ShiftTemplateResponse response = service.setActive(TEMPLATE_ID, false);

        assertThat(response.isActive()).isFalse();
        verify(shiftTemplateRepository, never()).delete(any());
    }

    @Test
    void shouldOnlyListActiveTemplatesForScheduling() {
        Pageable pageable = PageRequest.of(0, 20);
        when(shiftTemplateRepository.findByTenantIdAndActiveTrue(TENANT_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(morningTemplate())));

        assertThat(service.getTemplates(false, pageable).getContent()).hasSize(1);
        verify(shiftTemplateRepository, never()).findByTenantId(any(), any());
    }

    @Test
    void shouldNotFindTemplateOfAnotherTenant() {
        when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplateById(TEMPLATE_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static CreateShiftTemplateRequest createRequest(String name, String start, String end, String description) {
        CreateShiftTemplateRequest request = new CreateShiftTemplateRequest();
        request.setName(name);
        request.setStartTime(LocalTime.parse(start));
        request.setEndTime(LocalTime.parse(end));
        request.setDescription(description);
        return request;
    }

    private static ShiftTemplate morningTemplate() {
        return ShiftTemplate.builder()
            .id(TEMPLATE_ID)
            .tenantId(TENANT_ID)
            .name("Ca sáng")
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(14, 0))
            .build();
    }
}
