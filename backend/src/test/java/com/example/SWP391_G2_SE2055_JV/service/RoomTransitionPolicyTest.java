package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2 — chính sách "ai được bấm bước chuyển nào" (BR-ROOM-02, BR-ROOM-03, BR-PERM-04).
 * Lớp thuần logic, không mock gì: chỉ cần đặt người đăng nhập bằng {@link TestAuth}.
 */
class RoomTransitionPolicyTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();

    private final RoomTransitionPolicy policy = new RoomTransitionPolicy();

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── allowedTargets: nút mà mỗi người thấy ───────────────────────────────

    @Nested
    class AllowedTargets {

        /**
         * Bảng đầy đủ theo 01-state-machine. Cột cuối là các đích (cách nhau bởi dấu cách),
         * để trống = không có nút nào.
         */
        @ParameterizedTest(name = "{0} trên {1} → [{2}]")
        @CsvSource(delimiter = '|', value = {
            "MANAGER      | AVAILABLE   | UNAVAILABLE",
            "MANAGER      | RESERVED    | UNAVAILABLE",
            "MANAGER      | OCCUPIED    |",                          // BR-ROOM-03: có khách → không khóa
            "MANAGER      | DIRTY       | UNAVAILABLE",
            "MANAGER      | CLEANING    | UNAVAILABLE",               // khóa được giữa lúc dọn
            "MANAGER      | INSPECTION  | UNAVAILABLE",
            "MANAGER      | UNAVAILABLE | AVAILABLE DIRTY",           // Manager tự chọn đích
            "RECEPTION    | AVAILABLE   | RESERVED OCCUPIED",
            "RECEPTION    | RESERVED    | AVAILABLE OCCUPIED",
            "RECEPTION    | OCCUPIED    | DIRTY",
            "RECEPTION    | DIRTY       |",
            "RECEPTION    | UNAVAILABLE |",
            "HOUSEKEEPING | AVAILABLE   |",
            "HOUSEKEEPING | CLEANING    |",                          // hoàn thành dọn đi qua task, không qua đây
            "OTHER        | AVAILABLE   |",
            "DIRECTOR     | AVAILABLE   |",
            "DIRECTOR     | UNAVAILABLE |",
        })
        void shouldListAllowedTargetsPerActor(String actor, RoomStatus from, String expected) {
            loginAs(actor);

            assertThat(policy.allowedTargetsForCurrentUser(from)).isEqualTo(statuses(expected));
        }

        /** Không bao giờ lộ ra bước của task dọn, dù người đó là ai. */
        @ParameterizedTest
        @EnumSource(RoomStatus.class)
        void shouldNeverOfferCleaningOrInspectionAsTarget(RoomStatus from) {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThat(policy.allowedTargetsForCurrentUser(from))
                .doesNotContain(RoomStatus.CLEANING, RoomStatus.INSPECTION);
        }
    }

    // ── authorize: chặn khi gọi thẳng API ──────────────────────────────────

    @Nested
    class Authorize {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "DIRTY, CLEANING",          // hệ quả của assign task
            "CLEANING, INSPECTION",     // nhân viên bấm hoàn thành
            "CLEANING, DIRTY",          // gỡ người khỏi task
            "INSPECTION, AVAILABLE",    // kiểm tra đạt — phải có biên bản
            "INSPECTION, DIRTY",        // kiểm tra không đạt
        })
        void shouldRejectTaskDrivenTransitionsFromEndpoint(RoomStatus from, RoomStatus target) {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            // 400 chứ không phải 403: bước này không ai bấm được, kể cả Manager.
            assertThatThrownBy(() -> policy.authorize(from, target))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("task dọn phòng");
        }

        @ParameterizedTest
        @EnumSource(value = RoomStatus.class, names = {"CLEANING", "INSPECTION"})
        void shouldAllowManagerToLockCleaningAndInspectionRooms(RoomStatus from) {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThat(policy.authorize(from, RoomStatus.UNAVAILABLE)).isEqualTo(ChangeSource.MANAGER);
        }

        @Test
        void shouldReturnManagerSourceWhenManagerUnlocks() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThat(policy.authorize(RoomStatus.UNAVAILABLE, RoomStatus.DIRTY)).isEqualTo(ChangeSource.MANAGER);
        }

        @Test
        void shouldReturnReceptionSourceForReceptionSteps() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);

            assertThat(policy.authorize(RoomStatus.AVAILABLE, RoomStatus.RESERVED)).isEqualTo(ChangeSource.RECEPTION);
            assertThat(policy.authorize(RoomStatus.OCCUPIED, RoomStatus.DIRTY)).isEqualTo(ChangeSource.RECEPTION);
        }

        @Test
        void shouldForbidReceptionFromSettingUnavailable() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.RECEPTION);

            assertThatThrownBy(() -> policy.authorize(RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE))
                .isInstanceOf(UnauthorizedException.class);
        }

        /** Chốt 20/09/2026: Manager không làm thay Lễ tân. */
        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"AVAILABLE, RESERVED", "AVAILABLE, OCCUPIED", "RESERVED, AVAILABLE", "OCCUPIED, DIRTY"})
        void shouldForbidManagerFromReceptionTransitions(RoomStatus from, RoomStatus target) {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);

            assertThatThrownBy(() -> policy.authorize(from, target))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("không có quyền");
        }

        @ParameterizedTest
        @EnumSource(value = PositionType.class, names = {"HOUSEKEEPING", "OTHER"})
        void shouldForbidHousekeepingAndOtherPositionFromAnyUserTransition(PositionType positionType) {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, positionType);

            assertThatThrownBy(() -> policy.authorize(RoomStatus.AVAILABLE, RoomStatus.OCCUPIED))
                .isInstanceOf(UnauthorizedException.class);
            assertThatThrownBy(() -> policy.authorize(RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void shouldForbidDirectorFromChangingStatus() {
            TestAuth.loginAsDirector(TENANT_ID);

            assertThatThrownBy(() -> policy.authorize(RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    private static void loginAs(String actor) {
        switch (actor) {
            case "MANAGER" -> TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            case "DIRECTOR" -> TestAuth.loginAsDirector(TENANT_ID);
            default -> TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.valueOf(actor));
        }
    }

    private static Set<RoomStatus> statuses(String names) {
        if (names == null) {
            return EnumSet.noneOf(RoomStatus.class);
        }
        return Arrays.stream(names.trim().split("\\s+"))
            .map(RoomStatus::valueOf)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoomStatus.class)));
    }
}
