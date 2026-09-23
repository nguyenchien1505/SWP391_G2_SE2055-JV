package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantUsageRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F3 — điều kiện được phép ghi dữ liệu phòng: BR-ROOM-05 (số phòng duy nhất trong khách sạn),
 * BR-ROOM-08 (3 điều kiện xóa), BR-ORG-14 (loại phòng đã ẩn), BR-SAAS-02 (hạn mức phòng).
 *
 * <p>Lớp đang test không đọc {@code SecurityContextHolder} — {@code tenantId} do bên gọi truyền
 * vào — nên ở đây không cần {@code TestAuth}. Phần "ai được thao tác phòng nào" test ở
 * {@link RoomServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class RoomValidatorTest {

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID LOCATION_ID  = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();

    @Mock RoomRepository             roomRepository;
    @Mock LocationRepository         locationRepository;
    @Mock RoomTypeRepository         roomTypeRepository;
    @Mock SubscriptionRepository     subscriptionRepository;
    @Mock TenantUsageRepository      tenantUsageRepository;
    @Mock HousekeepingTaskRepository taskRepository;
    @Mock FixedAssetRepository       fixedAssetRepository;

    @InjectMocks RoomValidator validator;

    // ── Khách sạn nhận phòng ────────────────────────────────────────────────

    @Nested
    class AssertLocationInTenant {

        @Test
        void shouldPassWhenLocationBelongsToTenant() {
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
                .thenReturn(Optional.of(Location.builder().id(LOCATION_ID).tenantId(TENANT_ID).build()));

            assertThatCode(() -> validator.assertLocationInTenant(LOCATION_ID, TENANT_ID))
                .doesNotThrowAnyException();
        }

        /** Khách sạn của chuỗi khác trả 404 chứ không phải 403 — không lộ việc nó tồn tại. */
        @Test
        void shouldThrowNotFoundWhenLocationBelongsToAnotherTenant() {
            when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.assertLocationInTenant(LOCATION_ID, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Loại phòng — BR-ORG-14 ──────────────────────────────────────────────

    @Nested
    class RoomTypeRules {

        @Test
        void shouldReturnActiveRoomType() {
            stubRoomType(roomType(true));

            assertThatCode(() -> validator.requireActiveRoomType(ROOM_TYPE_ID, TENANT_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectInactiveRoomType() {
            stubRoomType(roomType(false));

            assertThatThrownBy(() -> validator.requireActiveRoomType(ROOM_TYPE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hạng sang")
                .hasMessageNotContaining("BR-");
        }

        /** Phòng cũ vẫn trỏ tới loại đã ẩn: giữ nguyên loại thì phải lưu được. */
        @Test
        void shouldAllowKeepingHiddenRoomType() {
            stubRoomType(roomType(false));

            assertThatCode(() -> validator.requireRoomType(ROOM_TYPE_ID, TENANT_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowNotFoundWhenRoomTypeBelongsToAnotherTenant() {
            when(roomTypeRepository.findByIdAndTenantId(ROOM_TYPE_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.requireActiveRoomType(ROOM_TYPE_ID, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        private void stubRoomType(RoomType roomType) {
            when(roomTypeRepository.findByIdAndTenantId(ROOM_TYPE_ID, TENANT_ID)).thenReturn(Optional.of(roomType));
        }

        private RoomType roomType(boolean active) {
            return RoomType.builder().id(ROOM_TYPE_ID).tenantId(TENANT_ID).name("Hạng sang").active(active).build();
        }
    }

    // ── Số phòng duy nhất trong khách sạn — BR-ROOM-05 ──────────────────────

    @Nested
    class AssertRoomNumberFree {

        @Test
        void shouldRejectDuplicateRoomNumberInSameLocation() {
            when(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(LOCATION_ID, "301")).thenReturn(true);

            assertThatThrownBy(() -> validator.assertRoomNumberFree(LOCATION_ID, "301", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("301")
                .hasMessageNotContaining("BR-");
        }

        @Test
        void shouldPassWhenRoomNumberFreeInLocation() {
            when(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(LOCATION_ID, "301")).thenReturn(false);

            assertThatCode(() -> validator.assertRoomNumberFree(LOCATION_ID, "301", null))
                .doesNotThrowAnyException();
        }

        /** Khi SỬA, chính phòng đang sửa phải bị loại khỏi phép kiểm tra. */
        @Test
        void shouldExcludeEditedRoomFromUniquenessCheck() {
            UUID editedRoomId = UUID.randomUUID();
            when(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(LOCATION_ID, "301", editedRoomId))
                .thenReturn(false);

            validator.assertRoomNumberFree(LOCATION_ID, "301", editedRoomId);

            verify(roomRepository).existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(LOCATION_ID, "301", editedRoomId);
            verify(roomRepository, never())
                .existsByLocationIdAndRoomNumberAndActiveTrue(any(), any());
        }
    }

    // ── Hạn mức phòng của gói dịch vụ — BR-SAAS-02 ──────────────────────────

    @Nested
    class AssertRoomQuotaAvailable {

        /** Đã dùng đúng bằng hạn mức là hết chỗ: phòng thứ (quota + 1) không được tạo. */
        @Test
        void shouldRejectWhenRoomQuotaReached() {
            stubQuota(10);
            when(tenantUsageRepository.countRooms(TENANT_ID)).thenReturn(10L);

            assertThatThrownBy(() -> validator.assertRoomQuotaAvailable(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10")
                .hasMessageNotContaining("BR-");
        }

        @Test
        void shouldPassWhenQuotaStillAvailable() {
            stubQuota(10);
            when(tenantUsageRepository.countRooms(TENANT_ID)).thenReturn(9L);

            assertThatCode(() -> validator.assertRoomQuotaAvailable(TENANT_ID)).doesNotThrowAnyException();
        }

        /**
         * Phải đếm bằng đúng {@code TenantUsageRepository.countRooms} — hàm tính CẢ phòng đã xóa
         * mềm theo quy ước của team. Đếm lại bằng truy vấn riêng của module phòng sẽ ra số khác.
         */
        @Test
        void shouldCountSoftDeletedRoomsTowardQuota() {
            stubQuota(10);
            when(tenantUsageRepository.countRooms(TENANT_ID)).thenReturn(10L);

            assertThatThrownBy(() -> validator.assertRoomQuotaAvailable(TENANT_ID))
                .isInstanceOf(BusinessException.class);

            verify(tenantUsageRepository).countRooms(TENANT_ID);
            verifyNoInteractions(roomRepository);
        }

        /** Tenant chưa có gói dịch vụ thì không chặn — giống cách TenantUsageService xử lý. */
        @Test
        void shouldAllowCreateWhenTenantHasNoSubscription() {
            when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

            assertThatCode(() -> validator.assertRoomQuotaAvailable(TENANT_ID)).doesNotThrowAnyException();
            verifyNoInteractions(tenantUsageRepository);
        }

        private void stubQuota(int quotaRoom) {
            when(subscriptionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(Subscription.builder().tenantId(TENANT_ID).quotaRoom(quotaRoom).build()));
        }
    }

    // ── Điều kiện xóa phòng — BR-ROOM-08 ────────────────────────────────────

    @Nested
    class AssertDeletable {

        @ParameterizedTest
        @EnumSource(value = RoomStatus.class, names = {"AVAILABLE", "UNAVAILABLE"})
        void shouldAllowDeletingAvailableOrUnavailableRoom(RoomStatus status) {
            Room room = room(status);
            when(taskRepository.findByRoomIdAndStatusIn(room.getId(), HousekeepingTaskStatus.OPEN_STATUSES))
                .thenReturn(List.of());
            when(fixedAssetRepository.existsByRoomId(room.getId())).thenReturn(false);

            assertThatCode(() -> validator.assertDeletable(room)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = RoomStatus.class, names = {"RESERVED", "OCCUPIED", "DIRTY", "CLEANING", "INSPECTION"})
        void shouldRejectDeleteWhenStatusNotDeletable(RoomStatus status) {
            assertThatThrownBy(() -> validator.assertDeletable(room(status)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(status.label())
                .hasMessageNotContaining("BR-");

            // Trạng thái sai thì dừng ngay, không tốn thêm truy vấn.
            verifyNoInteractions(taskRepository, fixedAssetRepository);
        }

        /** Phòng đã xóa mềm không xóa lại được — {@code Room.isDeletable()} đã tính cả cờ active. */
        @Test
        void shouldRejectDeleteWhenRoomAlreadySoftDeleted() {
            Room room = room(RoomStatus.AVAILABLE);
            room.setActive(false);

            assertThatThrownBy(() -> validator.assertDeletable(room)).isInstanceOf(BusinessException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNASSIGNED", "IN_PROGRESS", "PENDING_INSPECTION"})
        void shouldRejectDeleteWhenOpenTaskExists(String openStatus) {
            Room room = room(RoomStatus.AVAILABLE);
            when(taskRepository.findByRoomIdAndStatusIn(room.getId(), HousekeepingTaskStatus.OPEN_STATUSES))
                .thenReturn(List.of(HousekeepingTask.builder()
                    .id(UUID.randomUUID())
                    .status(HousekeepingTaskStatus.valueOf(openStatus))
                    .build()));

            assertThatThrownBy(() -> validator.assertDeletable(room))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dọn phòng")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(fixedAssetRepository);
        }

        @Test
        void shouldRejectDeleteWhenFixedAssetAttached() {
            Room room = room(RoomStatus.UNAVAILABLE);
            when(taskRepository.findByRoomIdAndStatusIn(room.getId(), HousekeepingTaskStatus.OPEN_STATUSES))
                .thenReturn(List.of());
            when(fixedAssetRepository.existsByRoomId(room.getId())).thenReturn(true);

            assertThatThrownBy(() -> validator.assertDeletable(room))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tài sản cố định")
                .hasMessageNotContaining("BR-");
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    private static Room room(RoomStatus status) {
        return Room.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .roomNumber("101")
            .floor("1")
            .roomTypeId(ROOM_TYPE_ID)
            .capacity(2)
            .status(status)
            .build();
    }
}
