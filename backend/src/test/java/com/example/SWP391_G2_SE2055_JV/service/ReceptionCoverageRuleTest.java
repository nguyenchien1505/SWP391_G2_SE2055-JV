package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mỗi ca theo mẫu đã có người phải có ít nhất một lễ tân — chốt 05/10/2026, chặn cứng.
 *
 * <p>"Lễ tân" = người có quyền Lễ tân (kể cả người kiêm Dọn dẹp). Hai hướng: THÊM người thì sau đó ca
 * phải có lễ tân; BỚT người thì không được biến một ca đang có lễ tân thành hết lễ tân khi vẫn còn
 * người khác — ca vốn đã thiếu (ví dụ lễ tân vừa nghỉ việc) thì vẫn cho bớt, để Manager không bị khóa.
 */
@ExtendWith(MockitoExtension.class)
class ReceptionCoverageRuleTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final LocalDate DAY    = LocalDate.of(2026, 10, 5);

    @Mock ShiftRepository         shiftRepository;
    @Mock UserRepository          userRepository;
    @Mock ShiftTemplateRepository shiftTemplateRepository;

    @InjectMocks ReceptionCoverageRule rule;

    private final User receptionist = person("Lễ tân Lan", StaffPermission.RECEPTION);
    private final User cleaner      = person("Dọn dẹp Hoa", StaffPermission.HOUSEKEEPING);
    private final User otherCleaner = person("Dọn dẹp Nam", StaffPermission.HOUSEKEEPING);
    private final User multiRole    = person("Kiêm nhiệm Mai", StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING);

    @BeforeEach
    void knowEveryone() {
        Map<UUID, User> byId = Arrays.stream(new User[]{receptionist, cleaner, otherCleaner, multiRole})
            .collect(Collectors.toMap(User::getId, Function.identity()));
        lenient().when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            List<User> found = new ArrayList<>();
            invocation.<Iterable<UUID>>getArgument(0).forEach(id -> {
                if (byId.containsKey(id)) {
                    found.add(byId.get(id));
                }
            });
            return found;
        });
        lenient().when(userRepository.findById(any()))
            .thenAnswer(invocation -> Optional.ofNullable(byId.get(invocation.<UUID>getArgument(0))));
        lenient().when(shiftTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(ShiftTemplate.builder()
            .id(TEMPLATE_ID).tenantId(TENANT_ID).name("Ca sáng")
            .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0)).build()));
    }

    // ── Thêm người ──────────────────────────────────────────────────────────

    @Nested
    class Adding {

        @Test
        void shouldPassWhenJoiningGroupBringsReceptionist() {
            givenCrew();

            assertThatCode(() -> rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID,
                List.of(cleaner.getId(), receptionist.getId())))
                .doesNotThrowAnyException();
        }

        /** Ca đã có lễ tân thì thêm người dọn bao nhiêu cũng được. */
        @Test
        void shouldPassWhenCrewAlreadyHasReceptionist() {
            givenCrew(receptionist);

            assertThatCode(() -> rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID,
                List.of(cleaner.getId(), otherCleaner.getId())))
                .doesNotThrowAnyException();
        }

        /** Người kiêm cả Lễ tân và Dọn dẹp vẫn tính là lễ tân. */
        @Test
        void shouldCountMultiRoleStaffAsReceptionist() {
            givenCrew(cleaner);

            assertThatCode(() -> rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID,
                List.of(multiRole.getId())))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectWhenShiftWouldHaveNoReceptionist() {
            givenCrew(cleaner);

            assertThatThrownBy(() -> rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID,
                List.of(otherCleaner.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ca sáng ngày 05/10/2026 phải có ít nhất 1 lễ tân")
                .hasMessageNotContaining("BR-");
        }

        /** Chỗ trống chưa giao ai không phải người — không cứu được ca thiếu lễ tân. */
        @Test
        void shouldIgnoreOpenSlots() {
            givenCrew(cleaner, null);

            assertThatThrownBy(() -> rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID,
                List.of(otherCleaner.getId())))
                .isInstanceOf(BusinessException.class);
        }

        /** Ca tự nhập giờ (không theo mẫu) không bắt buộc lễ tân — thường là ca hỗ trợ. */
        @Test
        void shouldSkipShiftWithoutTemplate() {
            rule.assertCoveredAfterAdding(LOCATION_ID, DAY, null, List.of(cleaner.getId()));

            verifyNoInteractions(shiftRepository, userRepository);
        }

        /** Chỉ mở chỗ trống, không thêm ai — ca vẫn chưa có người nên chưa cần lễ tân. */
        @Test
        void shouldSkipWhenNobodyJoins() {
            rule.assertCoveredAfterAdding(LOCATION_ID, DAY, TEMPLATE_ID, List.of());

            verifyNoInteractions(shiftRepository, userRepository);
        }
    }

    // ── Bớt người ───────────────────────────────────────────────────────────

    @Nested
    class Removing {

        @Test
        void shouldRejectRemovingOnlyReceptionistWhileOthersRemain() {
            List<Shift> crew = givenCrew(receptionist, cleaner);

            assertThatThrownBy(() -> rule.assertStillCoveredAfterRemoving(crew.get(0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lễ tân Lan là lễ tân duy nhất của Ca sáng ngày 05/10/2026")
                .hasMessageContaining("Giao thêm một lễ tân khác");
        }

        @Test
        void shouldAllowWhenAnotherReceptionistStays() {
            List<Shift> crew = givenCrew(receptionist, multiRole, cleaner);

            assertThatCode(() -> rule.assertStillCoveredAfterRemoving(crew.get(0))).doesNotThrowAnyException();
        }

        /** Người cuối cùng rời ca: ca không còn ai thì cũng không cần lễ tân. */
        @Test
        void shouldAllowLastPersonToLeave() {
            List<Shift> crew = givenCrew(receptionist);

            assertThatCode(() -> rule.assertStillCoveredAfterRemoving(crew.get(0))).doesNotThrowAnyException();
        }

        /** Ca VỐN đã thiếu lễ tân (lễ tân vừa nghỉ việc) — vẫn cho bớt người, không khóa Manager. */
        @Test
        void shouldAllowRemovingFromShiftAlreadyWithoutReceptionist() {
            List<Shift> crew = givenCrew(cleaner, otherCleaner);

            assertThatCode(() -> rule.assertStillCoveredAfterRemoving(crew.get(0))).doesNotThrowAnyException();
        }

        @Test
        void shouldAllowRemovingCleanerFromCoveredShift() {
            List<Shift> crew = givenCrew(receptionist, cleaner);

            assertThatCode(() -> rule.assertStillCoveredAfterRemoving(crew.get(1))).doesNotThrowAnyException();
        }

        @Test
        void shouldSkipShiftWithoutTemplateOrPerson() {
            Shift free = slot(receptionist);
            free.setSourceTemplateId(null);
            Shift open = slot(null);

            rule.assertStillCoveredAfterRemoving(free);
            rule.assertStillCoveredAfterRemoving(open);

            verifyNoInteractions(shiftRepository, userRepository);
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Những người đang có trong "Ca sáng 05/10" — {@code null} là một chỗ trống. */
    private List<Shift> givenCrew(User... people) {
        List<Shift> crew = Arrays.stream(people).map(this::slot).toList();
        when(shiftRepository.findByLocationIdAndShiftDateAndSourceTemplateId(LOCATION_ID, DAY, TEMPLATE_ID))
            .thenReturn(crew);
        return crew;
    }

    private Shift slot(User person) {
        return Shift.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID)
            .staffId(person == null ? null : person.getId())
            .shiftDate(DAY).startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(14, 0))
            .durationHours(new BigDecimal("8.00")).sourceTemplateId(TEMPLATE_ID)
            .build();
    }

    private static User person(String fullName, StaffPermission first, StaffPermission... rest) {
        return User.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID)
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .permissions(EnumSet.of(first, rest))
            .email(UUID.randomUUID() + "@test.local").passwordHash("x").fullName(fullName).phone("0900000000")
            .build();
    }
}
