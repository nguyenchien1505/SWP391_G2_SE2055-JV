package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceRowResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TodayAttendanceResponse.ReceptionGap;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khối «Hôm nay» (chấm công M2): ca theo mẫu đang không có lễ tân trong ca, và thao tác của lễ tân lúc chưa
 * vào ca — hai phần luật thuần, test không cần DB.
 */
class AttendanceTodayServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final UUID MORNING = UUID.randomUUID();

    private final User receptionist = staff("Lê Lễ Tân", StaffPermission.RECEPTION);
    private final User cleaner = staff("Phạm Dọn Dẹp", StaffPermission.HOUSEKEEPING);
    private final Map<UUID, User> people = Map.of(receptionist.getId(), receptionist, cleaner.getId(), cleaner);

    @Nested
    class ReceptionGaps {

        @Test
        void shouldFlagStartedShiftWithoutCheckedInReceptionist() {
            List<AttendanceRowResponse> rows = List.of(row(receptionist, null, null), row(cleaner, at(6, 0), null));

            List<ReceptionGap> gaps = AttendanceTodayService.receptionGaps(rows, people, at(6, 20));

            assertThat(gaps).singleElement().satisfies(gap -> {
                assertThat(gap.getMinutesSinceStart()).isEqualTo(20);
                assertThat(gap.getReceptionistNames()).containsExactly("Lê Lễ Tân");
            });
        }

        @Test
        void shouldNotFlagWhenReceptionistIsInShift() {
            List<AttendanceRowResponse> rows = List.of(row(receptionist, at(5, 55), null), row(cleaner, null, null));

            assertThat(AttendanceTodayService.receptionGaps(rows, people, at(7, 0))).isEmpty();
        }

        /** Lễ tân kết thúc ca sớm mà ca vẫn còn giờ — ca lại không có lễ tân. */
        @Test
        void shouldFlagWhenReceptionistLeftEarly() {
            List<AttendanceRowResponse> rows = List.of(row(receptionist, at(6, 0), at(10, 0)));

            assertThat(AttendanceTodayService.receptionGaps(rows, people, at(11, 0))).hasSize(1);
        }

        @Test
        void shouldIgnoreShiftNotStartedOrEnded() {
            List<AttendanceRowResponse> rows = List.of(row(receptionist, null, null));

            assertThat(AttendanceTodayService.receptionGaps(rows, people, at(5, 59))).isEmpty();
            assertThat(AttendanceTodayService.receptionGaps(rows, people, at(14, 0))).isEmpty();
        }

        /** Ca không xếp lễ tân nào cũng là ca thiếu lễ tân — danh sách người cần gọi rỗng. */
        @Test
        void shouldFlagShiftWithNoReceptionistAssigned() {
            List<ReceptionGap> gaps = AttendanceTodayService.receptionGaps(List.of(row(cleaner, at(6, 0), null)), people,
                at(8, 0));

            assertThat(gaps).singleElement().satisfies(gap -> assertThat(gap.getReceptionistNames()).isEmpty());
        }
    }

    @Nested
    class InShift {

        @Test
        void shouldCountOnlyBetweenCheckInAndCheckOut() {
            List<ShiftResponse> shifts = List.of(row(receptionist, at(6, 0), at(14, 0)).getShift());

            assertThat(AttendanceTodayService.inShift(shifts, at(5, 59))).isFalse();
            assertThat(AttendanceTodayService.inShift(shifts, at(6, 0))).isTrue();
            assertThat(AttendanceTodayService.inShift(shifts, at(14, 0))).isTrue();
            assertThat(AttendanceTodayService.inShift(shifts, at(14, 1))).isFalse();
        }

        @Test
        void shouldTreatOpenShiftAsStillIn() {
            List<ShiftResponse> shifts = List.of(row(receptionist, at(6, 0), null).getShift());

            assertThat(AttendanceTodayService.inShift(shifts, at(20, 0))).isTrue();
            assertThat(AttendanceTodayService.inShift(List.of(), at(8, 0))).isFalse();
        }
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private static LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    /** Một người trong Ca sáng 06:00–14:00 theo mẫu. */
    private static AttendanceRowResponse row(User user, LocalDateTime checkIn, LocalDateTime checkOut) {
        return AttendanceRowResponse.builder()
            .staffName(user.getFullName())
            .shift(ShiftResponse.builder()
                .id(UUID.randomUUID())
                .staffId(user.getId())
                .shiftDate(DAY)
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(14, 0))
                .sourceTemplateId(MORNING)
                .sourceTemplateName("Ca sáng")
                .checkInAt(checkIn)
                .checkOutAt(checkOut)
                .build())
            .build();
    }

    private static User staff(String name, StaffPermission permission) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setPermissions(EnumSet.of(permission));
        return user;
    }
}
