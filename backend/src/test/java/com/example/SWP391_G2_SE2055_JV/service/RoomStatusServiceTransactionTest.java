package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BR-ROOM-09: đổi {@code rooms.status} và ghi lịch sử nằm trong CÙNG một transaction — lịch sử
 * ghi hỏng thì trạng thái phòng cũng không được đổi.
 *
 * <p>Test tích hợp duy nhất của F2: cần transaction thật của Spring trên MySQL thật (profile
 * "test"), nên KHÔNG đánh {@code @Transactional} lên test — dữ liệu được commit thật ở
 * {@link #setUp} và tự dọn ở {@link #cleanUp}. Repository lịch sử bị thay bằng mock để giả lập
 * lỗi khi ghi.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomStatusServiceTransactionTest {

    @Autowired RoomStatusService  roomStatusService;
    @Autowired RoomRepository     roomRepository;
    @Autowired RoomTypeRepository roomTypeRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired TenantRepository   tenantRepository;

    @MockBean RoomStatusHistoryRepository historyRepository;

    private UUID tenantId;
    private UUID locationId;
    private UUID roomTypeId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        tenantId = tenantRepository.save(Tenant.builder()
            .name("Tenant test transaction").contactEmail(UUID.randomUUID() + "@test.local")
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = locationRepository.save(Location.builder()
            .tenantId(tenantId).name("Khách sạn test").address("Địa chỉ test").phone("0900000000")
            .status(LocationStatus.OPERATIONAL).build()).getId();
        roomTypeId = roomTypeRepository.save(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        roomId = roomRepository.save(Room.builder()
            .tenantId(tenantId).locationId(locationId).roomNumber("101").floor("1")
            .roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build()).getId();
    }

    @AfterEach
    void cleanUp() {
        roomRepository.deleteById(roomId);
        roomTypeRepository.deleteById(roomTypeId);
        locationRepository.deleteById(locationId);
        tenantRepository.deleteById(tenantId);
    }

    @Test
    void shouldKeepRoomStatusWhenHistoryCannotBeWritten() {
        when(historyRepository.save(any())).thenThrow(new DataIntegrityViolationException("Ghi lịch sử lỗi"));

        assertThatThrownBy(() -> roomStatusService.startCleaning(loadRoom(), null))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(loadRoom().getStatus()).isEqualTo(RoomStatus.DIRTY);
    }

    /** Đối chứng: cùng thao tác nhưng lịch sử ghi được thì trạng thái mới THẬT SỰ xuống DB. */
    @Test
    void shouldPersistRoomStatusWhenHistoryIsWritten() {
        roomStatusService.startCleaning(loadRoom(), null);

        assertThat(loadRoom().getStatus()).isEqualTo(RoomStatus.CLEANING);
    }

    /** Đọc lại từ DB ở một transaction riêng — không dùng bản đang giữ trong bộ nhớ. */
    private Room loadRoom() {
        return roomRepository.findByIdAndTenantIdAndActiveTrue(roomId, tenantId).orElseThrow();
    }
}
