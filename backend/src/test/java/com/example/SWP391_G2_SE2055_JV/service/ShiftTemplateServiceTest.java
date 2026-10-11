package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LocationTemplateSetResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mẫu ca — BR-SCH-04, BR-SCH-22 (đủ trường, chỉ vô hiệu hóa chứ không xóa), và bộ mẫu theo chi nhánh
 * (chốt 06/10/2026, V6): mỗi chi nhánh dùng ĐÚNG MỘT bộ — bộ chung của chuỗi hoặc bộ riêng của nó —
 * Giám đốc bật / tắt chủ động, có thể sao chép bộ chung làm điểm xuất phát.
 */
@ExtendWith(MockitoExtension.class)
class ShiftTemplateServiceTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID TEMPLATE_ID       = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();

    @Mock ShiftTemplateRepository shiftTemplateRepository;
    @Mock LocationRepository      locationRepository;

    @InjectMocks ShiftTemplateService service;

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    @Nested
    class AsDirector {

        @BeforeEach
        void loginAsDirector() {
            TestAuth.loginAsDirector(TENANT_ID);
        }

        @Test
        void shouldCreateCommonTemplateWithTrimmedTextAndComputedHours() {
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, null, "Ca đêm", null)).thenReturn(false);
            when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShiftTemplateResponse created = service.createTemplate(createRequest("  Ca đêm  ", "22:00", "06:00", "   ", null));

            ArgumentCaptor<ShiftTemplate> saved = ArgumentCaptor.forClass(ShiftTemplate.class);
            verify(shiftTemplateRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getValue().getLocationId()).isNull();
            assertThat(saved.getValue().getName()).isEqualTo("Ca đêm");
            assertThat(saved.getValue().getDescription()).isNull();
            assertThat(saved.getValue().isActive()).isTrue();
            // BR-SCH-03: mẫu qua đêm vẫn hợp lệ, 8 giờ tính trọn vào ngày bắt đầu.
            assertThat(created.isOvernight()).isTrue();
            assertThat(created.getDurationHours()).isEqualByComparingTo("8");
            verifyNoInteractions(locationRepository);
        }

        /** Thêm vào bộ riêng KHÔNG tự bật bộ riêng — Giám đốc soạn xong rồi mới bật. */
        @Test
        void shouldAddToOwnSetWithoutSwitchingLocationToIt() {
            Location location = location(LOCATION_ID, false);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca giữa", null)).thenReturn(false);
            when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShiftTemplateResponse created =
                service.createTemplate(createRequest("Ca giữa", "10:00", "18:00", null, LOCATION_ID));

            assertThat(created.getLocationId()).isEqualTo(LOCATION_ID);
            assertThat(location.isOwnShiftTemplates()).isFalse();
            verify(locationRepository, never()).save(any());
        }

        @Test
        void shouldRejectTemplateOfLocationOutsideTenant() {
            when(locationRepository.findByIdAndTenantId(OTHER_LOCATION_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.createTemplate(createRequest("Ca giữa", "10:00", "18:00", null, OTHER_LOCATION_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
            verify(shiftTemplateRepository, never()).save(any());
        }

        /** Trong CÙNG một bộ không được trùng tên — Manager chọn mẫu theo tên. */
        @Test
        void shouldRejectDuplicateNameInSameSet() {
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, null, "Ca sáng", null)).thenReturn(true);

            assertThatThrownBy(() -> service.createTemplate(createRequest("Ca sáng", "06:00", "14:00", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bộ mẫu chung đã có");
            verify(shiftTemplateRepository, never()).save(any());
        }

        /** Hai bộ khác nhau trùng tên thì được: một chi nhánh chỉ dùng một bộ nên không thấy cả hai. */
        @Test
        void shouldCheckNameOnlyWithinTheTargetSet() {
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(location(LOCATION_ID, false)));
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca sáng", null)).thenReturn(false);
            when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.createTemplate(createRequest("Ca sáng", "07:00", "15:00", null, LOCATION_ID));

            verify(shiftTemplateRepository).existsNameInSet(TENANT_ID, LOCATION_ID, "Ca sáng", null);
            verify(shiftTemplateRepository, never()).existsNameInSet(TENANT_ID, null, "Ca sáng", null);
        }

        /** Đổi tên kiểm trong bộ của chính mẫu đó, và bỏ qua chính nó. */
        @Test
        void shouldRejectRenamingToAnotherNameInSameSet() {
            ShiftTemplate template = morningTemplate();
            template.setLocationId(LOCATION_ID);
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(template));
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca chiều", TEMPLATE_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateTemplate(TEMPLATE_ID, updateRequest("Ca chiều")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bộ mẫu riêng");
            verify(shiftTemplateRepository, never()).save(any());
        }

        @Test
        void shouldKeepSetWhenEditingTemplate() {
            ShiftTemplate template = morningTemplate();
            template.setLocationId(LOCATION_ID);
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(template));
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca sáng sớm", TEMPLATE_ID))
                .thenReturn(false);
            when(shiftTemplateRepository.save(template)).thenReturn(template);

            ShiftTemplateResponse response = service.updateTemplate(TEMPLATE_ID, updateRequest("Ca sáng sớm"));

            assertThat(response.getName()).isEqualTo("Ca sáng sớm");
            assertThat(response.getLocationId()).isEqualTo(LOCATION_ID);
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

        /** Không chọn chi nhánh: Giám đốc quản trị toàn bộ danh mục — mọi bộ, kể cả mẫu đã tắt. */
        @Test
        void shouldListEveryTemplateOfTenantWithoutLocation() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shiftTemplateRepository.findByTenantId(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(morningTemplate())));

            assertThat(service.getTemplates(true, null, pageable).getContent()).hasSize(1);
        }

        @Test
        void shouldOnlyListActiveTemplatesForScheduling() {
            Pageable pageable = PageRequest.of(0, 20);
            when(shiftTemplateRepository.findByTenantIdAndActiveTrue(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(morningTemplate())));

            assertThat(service.getTemplates(false, null, pageable).getContent()).hasSize(1);
            verify(shiftTemplateRepository, never()).findByTenantId(any(), any());
        }

        /** Chi nhánh đang dùng bộ chung: chỉ bộ chung, không lẫn mẫu riêng đang soạn dở của nó. */
        @Test
        void shouldListCommonSetForLocationUsingCommonTemplates() {
            Pageable pageable = PageRequest.of(0, 20);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(location(LOCATION_ID, false)));
            when(shiftTemplateRepository.findInSet(TENANT_ID, null, false, pageable))
                .thenReturn(new PageImpl<>(List.of(morningTemplate())));

            assertThat(service.getTemplates(false, LOCATION_ID, pageable).getContent()).hasSize(1);
        }

        /** Chi nhánh đã bật bộ riêng: chỉ bộ riêng, không còn mẫu chung. */
        @Test
        void shouldListOwnSetForLocationUsingOwnTemplates() {
            Pageable pageable = PageRequest.of(0, 20);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(location(LOCATION_ID, true)));
            when(shiftTemplateRepository.findInSet(TENANT_ID, LOCATION_ID, false, pageable))
                .thenReturn(new PageImpl<>(List.of(morningTemplate())));

            assertThat(service.getTemplates(false, LOCATION_ID, pageable).getContent()).hasSize(1);
            verify(shiftTemplateRepository, never()).findInSet(TENANT_ID, null, false, pageable);
        }

        @Test
        void shouldSwitchLocationToOwnSet() {
            Location location = location(LOCATION_ID, false);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
            when(shiftTemplateRepository.countByTenantIdAndLocationIdAndActiveTrue(TENANT_ID, LOCATION_ID)).thenReturn(3L);

            LocationTemplateSetResponse response = service.setOwnTemplates(LOCATION_ID, true);

            assertThat(location.isOwnShiftTemplates()).isTrue();
            assertThat(response.isOwnShiftTemplates()).isTrue();
            assertThat(response.getOwnActiveTemplates()).isEqualTo(3);
            verify(locationRepository).save(location);
        }

        /** Bộ riêng chưa có mẫu nào đang dùng thì không bật — Manager sẽ không còn mẫu nào để chọn. */
        @Test
        void shouldRejectSwitchingToEmptyOwnSet() {
            Location location = location(LOCATION_ID, false);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
            when(shiftTemplateRepository.countByTenantIdAndLocationIdAndActiveTrue(TENANT_ID, LOCATION_ID)).thenReturn(0L);

            assertThatThrownBy(() -> service.setOwnTemplates(LOCATION_ID, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có mẫu ca riêng nào đang dùng");
            assertThat(location.isOwnShiftTemplates()).isFalse();
            verify(locationRepository, never()).save(any());
        }

        @Test
        void shouldSwitchBackToCommonSetAnytime() {
            Location location = location(LOCATION_ID, true);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.of(location));
            when(shiftTemplateRepository.countByTenantIdAndLocationIdAndActiveTrue(TENANT_ID, LOCATION_ID)).thenReturn(0L);

            service.setOwnTemplates(LOCATION_ID, false);

            assertThat(location.isOwnShiftTemplates()).isFalse();
            verify(locationRepository).save(location);
        }

        /** Sao chép bộ chung đang dùng sang bộ riêng; tên đã có trong bộ riêng thì giữ bản của chi nhánh. */
        @Test
        void shouldCopyActiveCommonTemplatesSkippingNamesAlreadyInOwnSet() {
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(location(LOCATION_ID, false)));
            ShiftTemplate afternoon = ShiftTemplate.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .name("Ca chiều").startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(22, 0))
                .description("Khung giờ nhận phòng").build();
            when(shiftTemplateRepository.findByTenantIdAndLocationIdIsNullAndActiveTrueOrderByStartTimeAsc(TENANT_ID))
                .thenReturn(List.of(morningTemplate(), afternoon));
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca sáng", null)).thenReturn(true);
            when(shiftTemplateRepository.existsNameInSet(TENANT_ID, LOCATION_ID, "Ca chiều", null)).thenReturn(false);
            when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<ShiftTemplateResponse> copied = service.copyCommonTemplates(LOCATION_ID);

            assertThat(copied).singleElement().satisfies(template -> {
                assertThat(template.getName()).isEqualTo("Ca chiều");
                assertThat(template.getLocationId()).isEqualTo(LOCATION_ID);
                assertThat(template.getStartTime()).isEqualTo(LocalTime.of(14, 0));
                assertThat(template.getDescription()).isEqualTo("Khung giờ nhận phòng");
            });
            verify(shiftTemplateRepository, times(1)).save(any(ShiftTemplate.class));
        }

        @Test
        void shouldNotFindTemplateOfAnotherTenant() {
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTemplateById(TEMPLATE_ID)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class AsManager {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** Manager luôn chỉ thấy bộ chi nhánh MÌNH đang dùng — tham số chi nhánh khác bị bỏ qua. */
        @Test
        void shouldAlwaysScopeManagerToOwnLocationSet() {
            Pageable pageable = PageRequest.of(0, 20);
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(location(LOCATION_ID, true)));
            when(shiftTemplateRepository.findInSet(TENANT_ID, LOCATION_ID, false, pageable))
                .thenReturn(new PageImpl<>(List.of(morningTemplate())));

            assertThat(service.getTemplates(false, OTHER_LOCATION_ID, pageable).getContent()).hasSize(1);
            verify(locationRepository, never()).findByIdAndTenantId(OTHER_LOCATION_ID, TENANT_ID);
            verify(shiftTemplateRepository, never()).findByTenantId(any(), any());
        }

        /** Mẫu riêng của chi nhánh khác: với Manager coi như không tồn tại. */
        @Test
        void shouldHideTemplateOfAnotherLocation() {
            ShiftTemplate template = morningTemplate();
            template.setLocationId(OTHER_LOCATION_ID);
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID)).thenReturn(Optional.of(template));

            assertThatThrownBy(() -> service.getTemplateById(TEMPLATE_ID)).isInstanceOf(ResourceNotFoundException.class);
        }

        /** Mẫu chung vẫn đọc được khi chi nhánh đã sang bộ riêng — ca cũ xếp theo nó phải hiện đúng tên. */
        @Test
        void shouldShowCommonTemplate() {
            when(shiftTemplateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(morningTemplate()));

            assertThat(service.getTemplateById(TEMPLATE_ID).getName()).isEqualTo("Ca sáng");
        }
    }

    private static Location location(UUID id, boolean ownTemplates) {
        return Location.builder().id(id).tenantId(TENANT_ID).name("Khách sạn Test Hà Nội")
            .ownShiftTemplates(ownTemplates).build();
    }

    private static CreateShiftTemplateRequest createRequest(String name, String start, String end, String description,
                                                            UUID locationId) {
        CreateShiftTemplateRequest request = new CreateShiftTemplateRequest();
        request.setLocationId(locationId);
        request.setName(name);
        request.setStartTime(LocalTime.parse(start));
        request.setEndTime(LocalTime.parse(end));
        request.setDescription(description);
        return request;
    }

    private static UpdateShiftTemplateRequest updateRequest(String name) {
        UpdateShiftTemplateRequest request = new UpdateShiftTemplateRequest();
        request.setName(name);
        request.setStartTime(LocalTime.of(6, 0));
        request.setEndTime(LocalTime.of(14, 0));
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
