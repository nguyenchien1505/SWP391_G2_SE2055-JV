package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai truy vấn lọc của luồng báo hỏng — {@link DamageReportRepository#search} và
 * {@link FixedAssetRepository#search} — chạy trên MySQL thật, cùng cột {@code resolution_note}
 * thêm ở V3. Kiểm rằng tham số {@code null} đúng là "bỏ qua điều kiện" chứ không phải "= NULL".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class DamageReportRepositoryTest {

    private static final Pageable ALL = PageRequest.of(0, 50);

    @Autowired TestEntityManager      em;
    @Autowired DamageReportRepository damageReportRepository;
    @Autowired FixedAssetRepository   fixedAssetRepository;

    private UUID tenantId;
    private UUID locationId;
    private UUID otherLocationId;
    private UUID roomId;
    private UUID areaId;
    private UUID categoryId;
    private UUID reporterA;
    private UUID reporterB;

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = persistLocation("Sao Mai Hà Nội");
        otherLocationId = persistLocation("Sao Mai Đà Nẵng");
        UUID roomTypeId = persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();
        roomId = persist(Room.builder().tenantId(tenantId).locationId(locationId).roomNumber("301")
            .floor("3").roomTypeId(roomTypeId).capacity(2).status(RoomStatus.AVAILABLE).build()).getId();
        areaId = persist(Area.builder().tenantId(tenantId).locationId(locationId).name("Sảnh").build()).getId();
        categoryId = persist(AssetCategory.builder().tenantId(tenantId).name("Tivi")
            .assetKind(AssetKind.FIXED).purpose(AssetPurpose.GUEST_USE).build()).getId();
        reporterA = persistUser();
        reporterB = persistUser();
        em.flush();
    }

    // ── FixedAssetRepository.search ───────────────────────────────────────────

    @Test
    void locTaiSanTheoPhongHoacKhuVucVaAnTaiSanDaThanhLy() {
        FixedAsset roomTv   = persistAsset(locationId, roomId, null, "TS-00001", FixedAssetStatus.GOOD);
        FixedAsset roomOld  = persistAsset(locationId, roomId, null, "TS-00002", FixedAssetStatus.DISPOSED);
        FixedAsset lobbyAc  = persistAsset(locationId, null, areaId, "TS-00003", FixedAssetStatus.BROKEN);
        em.flush();

        assertThat(fixedAssetRepository.search(tenantId, locationId, roomId, null, FixedAssetStatus.DISPOSED, ALL))
            .extracting(FixedAsset::getId).containsExactly(roomTv.getId());
        assertThat(fixedAssetRepository.search(tenantId, locationId, roomId, null, null, ALL))
            .extracting(FixedAsset::getId).containsExactlyInAnyOrder(roomTv.getId(), roomOld.getId());
        assertThat(fixedAssetRepository.search(tenantId, locationId, null, areaId, FixedAssetStatus.DISPOSED, ALL))
            .extracting(FixedAsset::getId).containsExactly(lobbyAc.getId());
    }

    @Test
    void giamDocKhongTruyenLocationThiThayMoiKhachSan() {
        persistAsset(locationId, roomId, null, "TS-00001", FixedAssetStatus.GOOD);
        UUID otherArea = persist(Area.builder().tenantId(tenantId).locationId(otherLocationId).name("Kho")
            .build()).getId();
        persistAsset(otherLocationId, null, otherArea, "TS-00001", FixedAssetStatus.GOOD);
        em.flush();

        assertThat(fixedAssetRepository.search(tenantId, null, null, null, FixedAssetStatus.DISPOSED, ALL))
            .hasSize(2);
        assertThat(fixedAssetRepository.search(tenantId, locationId, null, null, FixedAssetStatus.DISPOSED, ALL))
            .hasSize(1);
    }

    // ── DamageReportRepository.search ─────────────────────────────────────────

    @Test
    void locPhieuTheoNguoiBaoTaiSanVaTrangThai() {
        FixedAsset tv = persistAsset(locationId, roomId, null, "TS-00001", FixedAssetStatus.GOOD);
        FixedAsset ac = persistAsset(locationId, null, areaId, "TS-00002", FixedAssetStatus.GOOD);
        DamageReport aOnTv = persistReport(tv, reporterA, DamageReportStatus.NEW);
        DamageReport bOnTv = persistReport(tv, reporterB, DamageReportStatus.RESOLVED);
        DamageReport aOnAc = persistReport(ac, reporterA, DamageReportStatus.NEW);
        em.flush();

        // Staff A: chỉ phiếu của mình
        assertThat(damageReportRepository.search(tenantId, locationId, reporterA, null, null, ALL))
            .extracting(DamageReport::getId).containsExactlyInAnyOrder(aOnTv.getId(), aOnAc.getId());
        // Manager: lịch sử một tài sản, mọi trạng thái
        assertThat(damageReportRepository.search(tenantId, locationId, null, tv.getId(), null, ALL))
            .extracting(DamageReport::getId).containsExactlyInAnyOrder(aOnTv.getId(), bOnTv.getId());
        // DM-16: danh sách đang chờ
        assertThat(damageReportRepository.search(tenantId, locationId, null, null, DamageReportStatus.NEW, ALL))
            .extracting(DamageReport::getId).containsExactlyInAnyOrder(aOnTv.getId(), aOnAc.getId());
        // Location khác không thấy gì
        assertThat(damageReportRepository.search(tenantId, otherLocationId, null, null, null, ALL)).isEmpty();
    }

    /** V3 — ghi chú xử lý lưu và đọc lại được. */
    @Test
    void ghiChuXuLyDuocLuuCungPhieuDaDong() {
        FixedAsset tv = persistAsset(locationId, roomId, null, "TS-00001", FixedAssetStatus.GOOD);
        DamageReport report = persistReport(tv, reporterA, DamageReportStatus.RESOLVED);
        report.setResolutionNote("Đã thay màn hình");
        em.flush();
        em.clear();

        assertThat(damageReportRepository.findById(report.getId()))
            .get().extracting(DamageReport::getResolutionNote).isEqualTo("Đã thay màn hình");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private UUID persistLocation(String name) {
        return persist(Location.builder().tenantId(tenantId).name(name)
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
    }

    /** Vai trò người báo không quan trọng ở tầng DB; Manager không đòi Position nên dựng gọn hơn. */
    private UUID persistUser() {
        return persist(User.builder().tenantId(tenantId).locationId(locationId)
            .role(Role.MANAGER).status(UserStatus.ACTIVE)
            .email(uniqueEmail()).passwordHash("x").fullName("Người báo").phone("0900000000")
            .build()).getId();
    }

    private FixedAsset persistAsset(UUID location, UUID room, UUID area, String code, FixedAssetStatus status) {
        return persist(FixedAsset.builder().tenantId(tenantId).locationId(location).categoryId(categoryId)
            .assetCode(code).name("Tài sản " + code).roomId(room).areaId(area).status(status).build());
    }

    private DamageReport persistReport(FixedAsset asset, UUID reporter, DamageReportStatus status) {
        boolean resolved = status == DamageReportStatus.RESOLVED;
        return persist(DamageReport.builder().tenantId(tenantId).locationId(asset.getLocationId())
            .fixedAssetId(asset.getId()).reporterId(reporter).description("Hỏng")
            .status(status).reportedAt(LocalDateTime.now())
            .resolvedBy(resolved ? reporter : null).resolvedAt(resolved ? LocalDateTime.now() : null)
            .build());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
